# getStatsTimer と handleReqStats で peer 再参照時に不整合が発生する

- Priority: Medium
- Created: 2026-05-24
- Completed: 2026-05-27
- Model: deepseek-v4-pro
- Branch: feature/fix-getstats-timer-race

## 目的

`getStatsTimer` のコールバックと `handleReqStats` の非同期コールバック内で、`peer` フィールドを mutable なまま参照している。特に `handleReqStats` ではコールバック内で `peer` を 2 回参照しており、間で値が変わると不整合が生じる。`getStatsTimer` 側は `peer` 参照が 1 回であり不整合のリスクは低いが、`currentPeer` への束縛により `peer` の意図をコード上で明確にし、両者の一貫性を揃える。

なお本修正で防げるのは `peer` フィールドの再参照不整合であり、キャプチャ後のインスタンスが `internalDisconnect()` により dispose される可能性までは防げない。dispose 済み PeerConnection へのアクセスを防ぐには、`PeerChannel` 側での `getStats()` と `disconnect()` の排他制御が必要であり、これは本 issue の範囲外とする。

## 優先度根拠

- `handleReqStats` 内の `peer` 再参照不整合はコード上に実在するバグであり、修正はローカル変数への束縛で簡潔に完了する
- `getStatsTimer` 側は `peer` 参照が 1 回で不整合のリスクは低いが、両者を同じパターンに揃えることでコードの一貫性が向上する
- ただし本修正で防げる不整合の発生確率は低く、実害に直結する可能性も限定的であるため Priority は Medium とする

## 現状

### 問題 1: getStatsTimer

```kotlin
// SoraMediaChannel.kt:1242-1251
getStatsTimer?.schedule(0L, peerConnectionOption.getStatsIntervalMSec) {
    peer?.getStats(                           // mutable フィールドを直接参照
        RTCStatsCollectorCallback {
            listener?.onPeerConnectionStatsReady(this@SoraMediaChannel, it)
        },
    )
}
```

`peer` の参照は 1 回であり `handleReqStats` のような再参照不整合は生じないが、mutable フィールドを直接参照しているため、`peer` が null の場合に後続処理がスキップされる意図が `?.` に暗黙的に依存している。`currentPeer` に束縛することでこの意図を明示する。

### 問題 2: handleReqStats

```kotlin
// SoraMediaChannel.kt:1439-1445
private fun handleReqStats(dataChannel: DataChannel) {
    peer?.getStats {                          // (A) で peer を安全呼び出し
        it?.let { reports ->
            peer?.sendStats(dataChannel, reports)  // (B) 別の peer か null の可能性
        }
    }
}
```

(A) と (B) で `peer` を 2 回参照しており、間で値が変わると (A) ではインスタンス A の `getStats` を呼んだのに、(B) ではインスタンス B（または null）の `sendStats` を呼ぶ不整合が生じる。

## 設計方針

各非同期呼び出しの前に `val currentPeer = peer ?: return` でローカルスナップショットを取得し、コールバック内ではそのスナップショットのみを使用する。`peer` フィールドの再参照を避けることで、参照の一貫性を確保する。

キャプチャした `currentPeer` がその後に `internalDisconnect()` で dispose される可能性は残るが、これは本修正の範囲外であり、`PeerChannel` 側の排他制御で対処すべき別の問題である。

`peer` に `@Volatile` は追加しない。ローカルスナップショットをコールバッククロージャにキャプチャすることで、スレッド間の可視性問題も同時に回避されるため。

## スレッド安全性に関する判断

- `getStatsTimer` のコールバックは Timer スレッドで実行される。ローカル変数 `currentPeer` に `peer` の値をキャプチャした時点で、その値はスタックに固定され、以降の `peer = null` の影響を受けない
- `handleReqStats` の非同期コールバックも同様に、キャプチャした値は不変
- キャプチャした `currentPeer` がその後 dispose される可能性は残るが、これは本 issue の対象外

## テスト戦略

本修正は `private` メンバーの変更のみであり、公開 API 経由での直接検証は困難。以下の方針で検証する:

- コードレビュー: `val currentPeer = peer ?: return` パターンが正しく適用され、コールバック内で `peer` の再参照が残っていないことを確認
- 既存テスト: `sora-android-sdk/src/test/` 以下の既存テストがすべて通過すること

## 完了条件

- `getStatsTimer` のコールバック内で `peer` の再参照が行われず、一貫した参照が使われること
- `handleReqStats` の非同期コールバック内で `peer` の再参照が行われず、一貫した参照が使われること
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も忘れずに追記する）

## 解決方法

### 1. getStatsTimer: ローカルスナップショットをキャプチャ

```kotlin
getStatsTimer?.schedule(0L, peerConnectionOption.getStatsIntervalMSec) {
    val currentPeer = peer ?: return@schedule
    currentPeer.getStats(
        RTCStatsCollectorCallback {
            listener?.onPeerConnectionStatsReady(this@SoraMediaChannel, it)
        },
    )
}
```

### 2. handleReqStats: ローカルスナップショットをキャプチャ

```kotlin
private fun handleReqStats(dataChannel: DataChannel) {
    val currentPeer = peer ?: return
    currentPeer.getStats { reports ->
        if (reports != null) {
            currentPeer.sendStats(dataChannel, reports)
        }
    }
}
```
