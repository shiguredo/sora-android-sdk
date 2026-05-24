# requestClientOfferSdp の clientOfferPeer が internalDisconnect でキャンセルされない

- Priority: High
- Created: 2026-05-24
- Model: deepseek-v4-pro
- Branch: feature/fix-client-offer-peer-cancellation

## 目的

`requestClientOfferSdp()` 内で生成される `clientOfferPeer` はローカル変数であり、`internalDisconnect()` から到達できない。切断時に `clientOfferPeer` の WebRTC リソースが解放されず、処理が継続されて `onSuccess` コールバックから `connectSignalingChannel()` が呼ばれる可能性がある。

本修正は #0001（Rx 購読解除）および #0002（closing ガード + @Volatile）と合わせて、切断時の多層防御を構成する。各層の役割:
- #0001: `compositeDisposable.dispose()` による Rx 購読解除
- #0002: `connectSignalingChannel()` の `closing` ガードによる再接続防止 + `closing` の可視性保証
- #0003: `clientOfferPeer.disconnect()` による WebRTC リソースの早期解放

## 優先度根拠

- 切断処理が完了した後も `clientOfferPeer` の非同期処理が継続し、WebRTC リソースを消費し続ける
- `ReusableCompositeDisposable` の修正（#0001）で購読管理が機能しても、既に発火済みの `onSuccess` コールバックは止められない
- #0002 の `closing` ガードで `connectSignalingChannel()` の再接続は防げるが、`clientOfferPeer` 自体の WebRTC リソース解放は別途必要
- 接続確立前のタイムアウト (`onTimeout`) や切断操作によって `internalDisconnect` が呼ばれた場合に顕在化する

## 現状

### バグのあるコード

```kotlin
// SoraMediaChannel.kt:1121-1173 (抜粋)
private fun requestClientOfferSdp() {
    val clientOfferPeer = PeerChannelImpl(...)  // ローカル変数、メソッド外から到達不能
    clientOfferPeer.run {
        val subscription = requestClientOfferSdp()
            .observeOn(Schedulers.io())
            .subscribeBy(
                onSuccess = {
                    disconnect(null)     // ← clientOfferPeer.disconnect(null)
                    val handler = Handler(Looper.getMainLooper())
                    clientOffer = it.getOrNull()
                    handler.post {
                        connectSignalingChannel(clientOffer)
                    }
                },
                onError = {
                    disconnect(SoraDisconnectReason.SIGNALING_FAILURE)
                },
            )
        compositeDisposable.add(subscription)
    }
}

// SoraMediaChannel.kt:1549-1592
// internalDisconnect() 内に clientOfferPeer への参照はない
```

正常系では `onSuccess` 内で `clientOfferPeer.disconnect(null)` が呼ばれる。しかし `internalDisconnect()` が先に呼ばれた場合（タイムアウト等）、`clientOfferPeer` はクラスのフィールドではないため到達できず、WebRTC リソースが解放されないまま残る。

## 設計方針

1. `clientOfferPeer` をクラスのフィールドとして保持する
2. `internalDisconnect()` 内で `clientOfferPeer?.disconnect(null)` を呼び、WebRTC リソースを解放する
3. disconnect 後は `clientOfferPeer = null` を設定する（既存の `peer` や `signaling` と同じパターン）

`requestClientOfferSdp()` は `connect()` の `closing` チェック（1084 行）により 1 インスタンスで 1 回しか呼ばれないため、フィールドの競合は発生しない。

## スレッド安全性に関する判断

`clientOfferPeer` をフィールド化することで新たにクロススレッドアクセスが発生するが、既存の `peer` フィールドと同じパターンであり `@Volatile` は追加しない。

根拠:
- `requestClientOfferSdp()` は IO スレッドで `clientOfferPeer = PeerChannelImpl(...)` を書き込むが、`connect()` の `closing` チェックにより複数回の書き込みは発生しない
- `internalDisconnect()` は任意のスレッドから `clientOfferPeer?.disconnect(null)` → `clientOfferPeer = null` を実行する
- `PeerChannel.disconnect()` は内部で `closing` チェックを持つため、重複呼び出しは安全
- 既存の `peer` フィールドも `@Volatile` なしで同一パターンで運用されており、本修正も同じ信頼性レベルで動作する

## 挙動変化と影響範囲

- `internalDisconnect()` 内で `clientOfferPeer?.disconnect(null)` が呼ばれるようになる。正常系では `onSuccess` 内で既に disconnect 済みのため、`PeerChannel.disconnect()` の `closing` チェックにより no-op となる
- `clientOfferPeer` の WebRTC リソースが切断時に即座に解放されるようになる（修正前は `requestClientOfferSdp()` のスコープを抜けるまで GC に依存）

## テスト戦略

`clientOfferPeer` は `private` であり、公開 API 経由での直接観測は困難。以下の方針で検証する:

- コードレビュー: フィールド化、`internalDisconnect()` 内の disconnect + null 代入が正しいことを確認
- 既存テスト: `sora-android-sdk/src/test/` 以下の既存テストがすべて通過すること
- 結合テスト: タイムアウト切断時に WebRTC リソースがリークしないことを手動確認（メモリプロファイラ等）

## 完了条件

- `internalDisconnect()` が呼ばれた場合、`clientOfferPeer` の WebRTC リソースが `disconnect()` により解放されること
- `clientOfferPeer` が適切に null クリアされること（`peer` や `signaling` と同じパターン）
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も忘れずに追記する）

## 解決方法

3 箇所を修正する:

### 1. clientOfferPeer フィールドを追加

`peer` フィールド（489 行）の近くに追加する:

```kotlin
private var clientOfferPeer: PeerChannel? = null
```

### 2. requestClientOfferSdp() 内でフィールドに代入

ローカル変数 `val clientOfferPeer` をフィールド代入に変更する:

```kotlin
private fun requestClientOfferSdp() {
    // ...
    clientOfferPeer = PeerChannelImpl(...)
    clientOfferPeer!!.run {
        // ...
    }
}
```

### 3. internalDisconnect() 内で disconnect と null クリア

`peer?.disconnect(null)` (1578 行) の直前に追加する:

```kotlin
clientOfferPeer?.disconnect(null)
clientOfferPeer = null
```
