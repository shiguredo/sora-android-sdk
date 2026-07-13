# androidTest に onSignalingMessage で type: "switched" を受信する e2e テストを追加する

- Priority: High
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-signaling-switched-message
- Polished: 2026-07-13

## 目的

DataChannel signaling 有効時に Sora から `switched` メッセージを受信し、`SoraMediaChannel.Listener.onSignalingMessage` で通知されることを e2e で検証する。sora-js-sdk の switched メッセージ受信テストに相当する。

## 優先度根拠

- `onSignalingMessage` は追加された API であり、e2e レベルの回帰テストが存在しない。
- switched はシグナリング経路が WebSocket から DataChannel へ切り替わる要所であり、回帰検出の価値が高い。

## 現状

- DataChannel signaling（`dataChannelSignaling = true`）を用いた接続経路の e2e が存在しない。
- 既存の `createChannel` ヘルパー (`SoraE2ETest.kt:275-311`) は `dataChannelSignaling` 引数も `onSignalingMessage` コールバックも受け付けない。

## 設計方針

### Sora サーバー側の前提とスキップ判定

switched は Sora サーバー側で `data_channel_signaling` が有効である場合にのみ送信される。`dataChannelSignaling = true` を送っても、Sora がそれを受理し offer に `data_channels` を含めなければ実際には DataChannel signaling にならず、switched は来ない (`SoraMediaChannel.kt:1383-1389` で `offerDataChannelSignaling` が `false` になる)。

テストでは offer メッセージ受信時に以下を判定する:

1. `dataChannelSignalingUnsupported = AtomicBoolean(false)` を用意
2. `onSignalingMessage` 内で `type == "offer"` のメッセージを捕捉し、`rawMessage` を JSON パースして `data_channels` フィールドの有無と非空を確認する
3. `data_channels` が存在しないまたは空リストの場合、`dataChannelSignalingUnsupported.set(true)` をセットする
4. テスト本体側（JUnit スレッド）で `withTimeout(30_000)` 内の待機ループ中に `dataChannelSignalingUnsupported.get()` を監視し、true になったら `assumeTrue(false, "接続先 Sora が data_channel_signaling 非対応のためテストをスキップします")` でスキップする

`assumeTrue` を JUnit スレッドで呼ぶことで `AssumptionViolatedException` が正しく skip として処理される。onSignalingMessage 内で直接 `assumeTrue` を呼ぶと SDK のコールバックスレッドで非同期例外になり、skip として扱われないため避ける。

### createChannel ヘルパーの改修

現在の `createChannel` は `dataChannelSignaling` も `onSignalingMessage` コールバックも受け付けないため、以下を実施する:

1. `createChannel` に `dataChannelSignaling: Boolean? = null` 引数を追加する（デフォルト `null` で既存呼び出し元に影響なし）
2. `createChannel` に汎用フック `onSignalingMessage: ((direction: SoraSignalingDirection, transportType: SoraSignalingTransportType, rawMessage: String) -> Unit)? = null` 引数を追加し、`SoraMediaChannel.Listener.onSignalingMessage` から転送する

```kotlin
private fun createChannel(
    mediaOption: SoraMediaOption,
    onConnect: (SoraMediaChannel) -> Unit,
    onClose: (SoraMediaChannel, SoraCloseEvent) -> Unit,
    onError: (SoraMediaChannel, SoraErrorReason, String) -> Unit,
    dataChannelSignaling: Boolean? = null,
    onSignalingMessage: ((SoraSignalingDirection, SoraSignalingTransportType, String) -> Unit)? = null,
): SoraMediaChannel {
    // ...
    listener = object : SoraMediaChannel.Listener {
        // ...
        override fun onSignalingMessage(
            mediaChannel: SoraMediaChannel,
            direction: SoraSignalingDirection,
            transportType: SoraSignalingTransportType,
            rawMessage: String,
        ) {
            onSignalingMessage?.invoke(direction, transportType, rawMessage)
        }
    },
    // ...
}
```

既存テスト 2 本はデフォルト引数 `null` でそのままコンパイル可能であり、改修の影響を受けない。後続 issue（0068 close-type、0069 switched 後切断検証）でもこの汎用フックを再利用できる。

### switched メッセージの待機戦略

テスト側では以下の流れで switched を待つ（既存テスト `SoraE2ETest.kt:101-117` のパターンに従い、接続失敗や早期 close を `completeExceptionally` で伝搬する）:

1. `switchedReceived = CompletableDeferred<Unit>()` を用意
2. `createChannel` に `onConnect` / `onClose` / `onError` / `onSignalingMessage` を渡す
   - `onConnect`: `Log.d` のみ。switched 用の待機は別 defer で行うためここでは `complete` しない
   - `onClose`: `switchedReceived.completeExceptionally(RuntimeException("closed before switched: code=${closeEvent.code}"))`
   - `onError`: `switchedReceived.completeExceptionally(RuntimeException("$reason: $message"))`
   - `onSignalingMessage`: `type == "switched"` かつ `transportType == WEBSOCKET` なら `switchedReceived.complete(Unit)`
3. 接続後、`withTimeout(30_000) { switchedReceived.await() }` で待つ（js-sdk の `waitForSelector` 相当）

`onClose` / `onError` を `switchedReceived` に伝搬することで、接続失敗時に 30 秒のタイムアウトを待たず即座に原因を特定できる。

### switched メッセージの検証

`onSignalingMessage` 内で switched を検出した時点で以下を確認する（コールバック内でアサーションするか、受信後に値を保持して後段でアサーションする）:

- `transportType == SoraSignalingTransportType.WEBSOCKET`（switched は常に WebSocket 経由で届く）
- `direction == SoraSignalingDirection.RECEIVED`（受信メッセージであること）
- `rawMessage` を JSON パースし、`type` フィールドが `"switched"` であること

### ロール構成

recvonly（`enableVideoDownstream(null)`）で十分。映像送信は不要であり `DummyVideoCapturer` も不要。

## 完了条件

- DataChannel signaling 有効（`dataChannelSignaling = true`）で接続し、`onSignalingMessage` に `switched` が `transportType == WEBSOCKET`、`direction == RECEIVED`、`rawMessage` 中の `type` が `"switched"` であることを検証する e2e テストが追加されていること。
- 接続先 Sora が `data_channel_signaling` 非対応の場合はテストがスキップされること。
- Gradle Managed Device (pixelApi35) で完走すること。
- `CHANGES.md` の `develop` セクション `### misc` にエントリを追記すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt`
  - `createChannel` に `dataChannelSignaling: Boolean? = null` 引数と汎用フック `onSignalingMessage: ((SoraSignalingDirection, SoraSignalingTransportType, String) -> Unit)? = null` 引数を追加
  - `createChannel` 内の `SoraMediaChannel.Listener` に `onSignalingMessage` 実装を追加し、フックに転送する
  - テスト `DataChannel signaling 有効時に onSignalingMessage で switched を受信すること` を追加
- `CHANGES.md`

## 依存関係

- なし（recvonly 接続で検証可能）
- 接続先 Sora サーバーが `data_channel_signaling` を有効にしていることが前提

## 解決方法
