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

### Sora サーバー側の前提

switched は Sora サーバー側で `data_channel_signaling` が有効である場合にのみ送信される。さらに `dataChannelSignaling = true` を送っても、Sora がそれを受理し offer に `dataChannels` を含めなければ実際には DataChannel signaling にならず、switched は来ない (`SoraMediaChannel.kt:1383-1389` で `offerDataChannelSignaling` が `false` になる)。

テストの前提として「接続先 Sora が `data_channel_signaling` を有効にしていること」を環境要件として明記する。有効でない環境では `assumeTrue` でテストをスキップできる設計にしておく。

### createChannel ヘルパーの改修

現在の `createChannel` は `dataChannelSignaling` も `onSignalingMessage` コールバックも受け付けないため、以下を実施する:

1. `createChannel` に `dataChannelSignaling: Boolean? = null` 引数を追加する（デフォルト `null` で既存呼び出し元に影響なし）
2. `createChannel` 内の `SoraMediaChannel.Listener` に `onSignalingMessage` を実装し、switched 受信時に `CompletableDeferred` を完了させるために `switchedReceived: CompletableDeferred<Unit>? = null` 引数を追加する

テスト側では `switchedReceived = CompletableDeferred<Unit>()` を渡し、`onSignalingMessage` 内で `transportType == WEBSOCKET` かつ `type == "switched"` のメッセージを受信したら `switchedReceived.complete(Unit)` を呼ぶ。テストは `withTimeout(30_000) { switchedReceived.await() }` で待つ（js-sdk の `waitForSelector` 相当）。

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
  - `createChannel` に `dataChannelSignaling: Boolean? = null` 引数と `switchedReceived: CompletableDeferred<Unit>? = null` 引数を追加
  - `createChannel` 内の `SoraMediaChannel.Listener` に `onSignalingMessage` 実装を追加し、switched 受信時に `switchedReceived?.complete(Unit)` を呼ぶ
  - テスト `DataChannel signaling 有効時に onSignalingMessage で switched を受信すること` を追加
- `CHANGES.md`

## 依存関係

- なし（recvonly 接続で検証可能）
- 接続先 Sora サーバーが `data_channel_signaling` を有効にしていることが前提

## 解決方法
