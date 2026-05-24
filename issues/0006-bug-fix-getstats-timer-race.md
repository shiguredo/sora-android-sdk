# getStatsTimer と handleReqStats で切断後に dispose 済み PeerConnection にアクセスする

- Priority: High
- Created: 2026-05-24
- Model: deepseek-v4-pro
- Branch: feature/fix-getstats-timer-race

## 目的

`getStatsTimer` による定期実行と `handleReqStats` による非同期 `getStats` の両方で、`peer` が mutable なフィールドであることと切断処理との競合により、dispose 済み `PeerConnection` へのアクセスが発生する可能性がある。

## 優先度根拠

- `getStatsTimer.schedule()` の実行中に `internalDisconnect()` が走ると、`peer` が null になる前にキャプチャされたコールバックが dispose 済み `PeerConnection` にアクセスする
- `peer` は `@Volatile` でないため、別スレッドからの可視性も保証されていない（ただし今回の修正ではローカルスナップショットパターンで回避するため `@Volatile` は不要）
- `Timer.cancel()` は進行中のタスクを止められないため、タスク実行と切断のタイミングによっては競合が不可避

## 現状

### 問題 1: getStatsTimer

```kotlin
// SoraMediaChannel.kt:1242-1251
getStatsTimer?.schedule(0L, peerConnectionOption.getStatsIntervalMSec) {
    peer?.getStats(                           // (A) peer を読み取り
        RTCStatsCollectorCallback {
            listener?.onPeerConnectionStatsReady(this@SoraMediaChannel, it)  // (B)
        },
    )
}

// internalDisconnect() 内 (SoraMediaChannel.kt:1549-1592):
getStatsTimer?.cancel()                       // (C) 進行中タスクは止まらない
getStatsTimer = null
// ...
peer?.disconnect(null)                        // (D)
peer = null                                   // (E)
listener = null                               // (F)
```

(A) で `peer` が非 null と評価された直後に (D)-(E) で disconnect + null 化されると、`getStats()` が dispose 済み PeerConnection にアクセスする。(B) のコールバック実行時に (F) で `listener = null` になっている可能性もある。

### 問題 2: handleReqStats

```kotlin
// SoraMediaChannel.kt:1439-1445
private fun handleReqStats(dataChannel: DataChannel) {
    peer?.getStats {                          // (A)
        it?.let { reports ->
            peer?.sendStats(dataChannel, reports)  // (B) 別の peer か null の可能性
        }
    }
}
```

(A) で安全に `peer` を評価しても、非同期コールバック内 (B) では `peer` が別の値（null または別インスタンス）に変わっている可能性がある。

## 設計方針

各非同期呼び出しの前に `peer` のローカルスナップショットを取得し、コールバック内ではそのスナップショットを使用する。`peer` フィールドの再参照を避けることで、参照の一貫性を確保する。

`peer` に `@Volatile` は追加しない。ローカルスナップショットをコールバッククロージャにキャプチャすることで、スレッド間の可視性問題も同時に回避されるため。

## スレッド安全性に関する判断

`peer` フィールドに `@Volatile` を追加せず、ローカルスナップショットパターンで対応する。根拠:

- `getStatsTimer` のコールバックは Timer スレッドで実行される。ローカル変数 `currentPeer` に `peer` の値をキャプチャした時点で、その値はスタックに固定され、以降の `peer = null` の影響を受けない
- `handleReqStats` の非同期コールバック（Kotlin ラムダ）も同様に、`val currentPeer = peer` でキャプチャした値は不変
- 可視性の問題はタイミング依存だが、キャプチャ後の値が一貫して使われるため、最悪でも「ティック判定で取得した peer が dispose 前に取得されていたが、コールバック実行時には dispose 済み」というケースに限られる。これは `peer?.disconnect(null)` の後に短い遅延でコールバックが実行されるレースであり、確率的に低い

## テスト戦略

本修正は `private` メンバーの変更のみであり、公開 API 経由での直接検証は困難。以下の方針で検証する:

- コードレビュー: `val currentPeer = peer ?: return` パターンが正しく適用されていることを確認
- 既存テスト: `sora-android-sdk/src/test/` 以下の既存テストがすべて通過すること
- 結合テスト: `getStatsIntervalMSec` を短い値に設定し、接続・切断を繰り返してクラッシュが発生しないことを確認

## 完了条件

- `getStatsTimer` のタスク実行中に `internalDisconnect()` が呼ばれても、dispose 済みの `PeerConnection` にアクセスしないこと
- `handleReqStats` の非同期コールバック内で、切断後に取得した `peer` に対して `sendStats` が呼ばれないこと
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

### 2. handleReqStats: ローカルスナップショット + closing チェック

```kotlin
private fun handleReqStats(dataChannel: DataChannel) {
    val currentPeer = peer ?: return
    currentPeer.getStats { reports ->
        if (reports != null && !closing) {
            currentPeer.sendStats(dataChannel, reports)
        }
    }
}
```
