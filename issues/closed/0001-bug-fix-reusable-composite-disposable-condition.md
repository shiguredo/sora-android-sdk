# ReusableCompositeDisposable の初期化条件誤りにより全 Rx 購読が管理されていない

- Priority: High
- Created: 2026-05-24
- Completed: 2026-05-26
- Model: deepseek-v4-pro
- Branch: feature/fix-reusable-composite-disposable

## 目的

`ReusableCompositeDisposable.add()` の初期化条件が誤っており、`compositeDisposable` が `null` でなければ `CompositeDisposable` を生成するコードになっている。初回 `add()` 時には `null` のため生成されず、以降も同様。結果として全 Rx 購読が管理されず、`dispose()` が常に no-op になり、切断時の購読解除が一切機能していない。

## 優先度根拠

- 全 Rx 購読が管理されていないため、切断後も SDP 処理等の非同期処理が生き続ける
- 切断後の非同期処理が dispose 済み `PeerConnection` に JNI アクセスし、セグフォを引き起こす可能性がある
- 修正は 1 文字 (`!= null` → `== null`) で完了する
- 本修正は #0002 の `connectSignalingChannel` の `closing` ガードおよび #0003 の `clientOfferPeer` キャンセルと多層防御として補完関係にあり、これら 3 件を組み合わせることで切断時の堅牢性が向上する

## 現状

### バグのあるコード

```kotlin
// sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/util/ReusableCompositeDisposable.kt

class ReusableCompositeDisposable {
    private var compositeDisposable: CompositeDisposable? = null

    fun add(subscription: Disposable) {
        if (compositeDisposable != null) {          // ← バグ: null でないときに初期化している
            compositeDisposable = CompositeDisposable()
        }
        compositeDisposable?.add(subscription)      // ← compositeDisposable は null なので常に no-op
    }

    fun dispose() {
        compositeDisposable?.dispose()              // ← compositeDisposable は null なので常に no-op
        compositeDisposable = null
    }
}
```

## 設計方針

条件式を `if (compositeDisposable == null)` に修正する。同期機構は現状通り追加しない（根拠は「スレッド安全性に関する判断」参照）。単体テストを追加して修正の正しさを検証する。

## スレッド安全性に関する判断

本クラスはもともと同期機構 (`@Synchronized`, `@Volatile` 等) を持たない。この修正後も新たに同期機構は追加しない。

考慮した競合シナリオ:

- `add()` と `dispose()`: `handleUpdateOffer` や `handleReOffer` 等のシグナリングハンドラ内で `add()` が呼ばれるが、これらは `onInitialOffer`/`onUpdatedOffer`/`onReOffer` が発火したタイミングで実行される。`internalDisconnect()` が `closing` フラグを立てた後も、すでに発火済みのハンドラが `add()` を実行する可能性はある。このタイミングで競合した場合、一部の購読が dispose されずに残る可能性がある
- `add()` 同士の競合: シグナリングハンドラは `Schedulers.io()` 上で逐次実行されるため、複数の `add()` が同時に呼ばれることはない（RxJava の Observer 契約による逐次保証）
- `dispose()` 直後の `add()`: `compositeDisposable.dispose()` と `signaling?.disconnect()` の間に WebSocket メッセージが到達した場合、ハンドラが `add()` を実行して新たな `CompositeDisposable` を生成する可能性がある。これは二度と `dispose()` されず購読リークとなる

いずれの競合シナリオでも、最悪の結果（一部の購読が dispose されずに残る、購読リーク）は、現状（全購読が dispose されない）より改善している。完全なスレッド安全性が必要になった場合は、別 issue で対応する。

## 挙動変化と影響範囲

修正前は全購読が管理されていなかったため、切断後も非同期処理が生存していた。修正後は `internalDisconnect()` 内の `compositeDisposable.dispose()` が実際に機能し、切断時に全購読が解除される。これは意図した正しい動作であり、切断後の意図しない処理継続を防ぐという点で改善である。

ただし、これまで「切断後も購読が生き続ける」状態を暗黙の前提としたコードパスが存在する可能性に注意する。修正後の切断時挙動変化が問題を引き起こさないことを結合テストで確認すること。

## テスト戦略

`ReusableCompositeDisposable` は RxJava の実オブジェクトのみに依存しており、モック不要で単体テストが可能。`Disposable` の生成には `Disposables.fromRunnable {}` を使用する。

追加するテストケース（テストファイルは `sora-android-sdk/src/test/kotlin/jp/shiguredo/sora/sdk/util/ReusableCompositeDisposableTest.kt` に新規作成。`util` ディレクトリも新規作成が必要）:

- `add()` 直後: 購読が解除されていないこと (`Disposable.isDisposed` が false)
- `dispose()` 後: 登録済みの全購読が解除されること (`Disposable.isDisposed` が true)
- `dispose()` → 再度 `add()` → 再度 `dispose()`: 2 サイクル目も正しく購読管理されること
- 空状態 (`add()` 未実行) で `dispose()`: 例外が発生しないこと

## 完了条件

- `ReusableCompositeDisposableTest.kt` の全テストが通過すること
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も忘れずに追記する）

## 解決方法

`ReusableCompositeDisposable.kt` の `add()` メソッドの条件式を `if (compositeDisposable == null)` に修正し、初回 `add()` 時に `CompositeDisposable` が正しく初期化されるようにした。`dispose()` メソッドは変更していない。

また、`sora-android-sdk/src/test/kotlin/jp/shiguredo/sora/sdk/util/ReusableCompositeDisposableTest.kt` を追加し、以下をモックなしで検証した。

- `add()` 直後は購読が解除されていないこと
- `dispose()` 後は登録済みの全購読が解除されること
- `dispose()` 後に再度 `add()` と `dispose()` ができること
- `add()` 未実行で `dispose()` しても例外が発生しないこと

テストは `./gradlew :sora-android-sdk:testDebugUnitTest --tests jp.shiguredo.sora.sdk.util.ReusableCompositeDisposableTest` で通過を確認した。

```kotlin
fun add(subscription: Disposable) {
    if (compositeDisposable == null) {
        compositeDisposable = CompositeDisposable()
    }
    compositeDisposable?.add(subscription)
}
```
