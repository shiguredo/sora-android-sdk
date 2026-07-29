# requestClientOfferSdp の clientOfferPeer が internalDisconnect でキャンセルされない

- Priority: Medium
- Created: 2026-05-24
- Completed: 2026-05-27
- Model: deepseek-v4-pro
- Branch: feature/fix-client-offer-peer-cancellation

## 目的

`requestClientOfferSdp()` 内で生成される `clientOfferPeer` はローカル変数であるため、`internalDisconnect()` から到達できず WebRTC リソースを解放できない。切断後も `clientOfferPeer` の非同期処理が完了するまでリソースが保持され続ける。

なお再接続防止は #0002（`closing` ガード + `@Volatile`）が本丸であり、本修正は WebRTC リソースの早期解放を目的とした補強策として位置づける。

本修正は #0001 および #0002 と合わせて、切断時の多層防御を構成する。各層の役割:
- #0001: `compositeDisposable.dispose()` による Rx 購読解除
- #0002: `connectSignalingChannel()` の `closing` ガードによる再接続防止 + `closing` の可視性保証（本丸）
- #0003: `clientOfferPeer.disconnect()` による WebRTC リソースの早期解放（補強）

## 優先度根拠

- #0002 の `closing` ガードが入れば再接続の実害は大幅に抑えられるため、本修正単体の緊急性は High ではない
- ただし切断後も `clientOfferPeer` の非同期処理（ICE 収集等）が完了するまで WebRTC リソースが保持され続ける問題は残る
- 短命な切断・再接続を繰り返すユースケースでは、リソース解放の遅延が積み重なりうる
- `ReusableCompositeDisposable` の修正（#0001）で購読管理が機能しても、既に発火済みの `onSuccess` コールバックは止められない

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

正常系では `onSuccess` 内で `clientOfferPeer.disconnect(null)` が呼ばれる。しかし `internalDisconnect()` が先に呼ばれた場合（タイムアウトや明示的切断）、`clientOfferPeer` はクラスのフィールドではないため到達できず、`disconnect()` を明示的に呼べない。`clientOfferPeer` の WebRTC リソースは `requestClientOfferSdp()` のスコープを抜けるまで参照が維持され、非同期処理（ICE 収集等）の完了タイミングや、Rx 購読が dispose されるタイミングに実質的な解放が依存している。

## 設計方針

1. `clientOfferPeer` をクラスのフィールドとして保持する
2. `internalDisconnect()` 内で `clientOfferPeer?.disconnect(null)` を呼び、WebRTC リソースを解放する
3. disconnect 後は `clientOfferPeer = null` を設定する（既存の `peer` や `signaling` と同じパターン）

`requestClientOfferSdp()` は `connect()` の `closing` チェック（1084 行）により 1 インスタンスで 1 回しか呼ばれないため、フィールドの競合は発生しない。

## スレッド安全性に関する判断

`clientOfferPeer` をフィールド化することで新たにクロススレッドアクセスが発生する。`@Volatile` を追加せず、既存の `peer` フィールドと同じパターンで運用する。

NPE 対策として、`requestClientOfferSdp()` 内では `clientOfferPeer!!.run` を使用せず、ローカル変数にインスタンスを受けてからフィールドに代入し、ローカル変数経由で処理を続ける。これにより、フィールドが別スレッドから `null` にされても NPE は発生しない。

この選択の背景:
- `requestClientOfferSdp()` は IO スレッドで `clientOfferPeer = PeerChannelImpl(...)` を書き込むが、`connect()` の `closing` チェックにより複数回の書き込みは発生しない
- `internalDisconnect()` は任意のスレッドから `clientOfferPeer?.disconnect(null)` → `clientOfferPeer = null` を実行する
- `PeerChannel.disconnect()` は内部で `closing` チェックを持つため、重複呼び出しや競合は安全
- クロススレッド可視性の問題は理論上残る（IO スレッドでの書き込みが `internalDisconnect()` を実行するスレッドから見えない可能性）が、`requestClientOfferSdp()` が完了する前に `internalDisconnect()` が呼ばれるタイミングは限定的であり、実用上のリスクは低い

なお既存の `peer` フィールドも `@Volatile` なしで同一パターンで運用されているが、これが既存コードの安全性を証明するわけではない。本修正で導入する `clientOfferPeer` も既存の `peer` と同程度の信頼性にとどまり、完全なスレッド安全性が必要であれば別 issue で両フィールドをまとめて対応する。

## 挙動変化と影響範囲

- `internalDisconnect()` 内で `clientOfferPeer?.disconnect(null)` が呼ばれるようになる。正常系では `onSuccess` 内で既に disconnect 済みのため、`PeerChannel.disconnect()` の `closing` チェックにより no-op となる
- `clientOfferPeer` の WebRTC リソースが切断時に即座に解放されるようになる（修正前は `requestClientOfferSdp()` のスコープを抜け、かつ非同期処理が完了するまで解放が遅延していた）

## テスト戦略

`clientOfferPeer` は `private` であり、公開 API 経由での直接観測は困難。以下の方針で検証する:

- コードレビュー: フィールド化、`internalDisconnect()` 内の disconnect + null 代入が正しいことを確認
- 既存テスト: `sora-android-sdk/src/test/` 以下の既存テストがすべて通過すること
- 結合テスト: タイムアウト切断時に WebRTC リソースがリークしないことを手動確認（メモリプロファイラ等）

## 完了条件

- `internalDisconnect()` が呼ばれた場合、`clientOfferPeer` の WebRTC リソースが `disconnect()` により解放されること
- `clientOfferPeer` が適切に null クリアされること（`internalDisconnect()`、`onSuccess`、`onError` の全経路で null が設定されること）
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も忘れずに追記する）

## 解決方法

4 箇所を修正する:

### 1. clientOfferPeer フィールドを追加

`peer` フィールド（489 行）の近くに追加する:

```kotlin
private var clientOfferPeer: PeerChannel? = null
```

### 2. requestClientOfferSdp() 内でフィールドに代入

ローカル変数 `val clientPeer` を生成してインスタンスを保持し、フィールドに代入した上でローカル変数経由で処理を続ける。`!!` による NPE のリスクを避けるため、`clientOfferPeer` の参照には直接アクセスしない。

```kotlin
private fun requestClientOfferSdp() {
    // ...
    val clientPeer = PeerChannelImpl(...)
    clientOfferPeer = clientPeer
    clientPeer.run {
        // ...
    }
}
```

### 3. onSuccess / onError 内で clientOfferPeer を null クリア

正常完了・異常完了のいずれの場合も `disconnect()` 呼び出し後に `clientOfferPeer = null` を設定する。`internalDisconnect()` 経由に加えて、これらの経路でも切断済みインスタンスへの参照を確実に切る。

```kotlin
onSuccess = {
    disconnect(null)
    clientOfferPeer = null
    // ...
},
onError = {
    disconnect(SoraDisconnectReason.SIGNALING_FAILURE)
    clientOfferPeer = null
},
```

### 4. internalDisconnect() 内で disconnect と null クリア

`peer?.disconnect(null)` (1583 行) の直前に追加する:

```kotlin
clientOfferPeer?.disconnect(null)
clientOfferPeer = null
```
