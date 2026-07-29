# androidTest に DataChannel signaling only での切断経路を検証する e2e テストを追加する

- Priority: Medium
- Created: 2026-07-13
- Completed: 2026-07-13
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-datachannel-signaling-close-type
- Polished: 2026-07-13

## 目的

DataChannel signaling only（`ignoreDisconnectWebSocket = true`）の構成で切断したとき、切断シグナリング (`type: "disconnect"` 送信) が DataChannel 経由で行われることを e2e で検証する。sora-js-sdk の `type_close` テストに相当する。

## 優先度根拠

- `dataChannelSignaling` と `ignoreDisconnectWebSocket` の組み合わせは切断挙動が通常と異なり、回帰しやすい経路であるため e2e で担保する価値がある。

## 現状

- 既存 e2e は WebSocket 経由の切断のみを扱い、DataChannel signaling only での切断経路を検証していない。
- `SoraE2ETestBase.createChannel` は `dataChannelSignaling` に対応済み（0067）だが、`ignoreDisconnectWebSocket` には未対応。
- `SoraCloseEvent` は `code` と `reason` のみを持ち、切断経路の情報は含まれない。切断経路の検証には `onSignalingMessage` で送信される `type: "disconnect"`（送信）および `type: "close"`（受信）の `transportType` を見る必要がある。

## 設計方針

### Sora サーバー側の前提

- Sora サーバー側で `data_channel_signaling` が有効であること（0067 と同様の offer `data_channels` 検査でスキップ判定可能）。
- `type: "close"` メッセージの受信は Sora サーバー側の `data_channel_signaling_close_message` 設定に依存する（`SoraMediaChannel.kt:1501-1504`）。この設定が無効な場合、`type: "close"` は送信されず DataChannel が単に閉じられるだけになる。本テストでは `type: "close"` の受信検証は必須とせず、検証できた場合のみ確認する方針とする。

### createChannel ヘルパーの拡張

`SoraE2ETestBase.createChannel` に `ignoreDisconnectWebSocket: Boolean? = null` 引数を追加する。既存の `dataChannelSignaling` / `onSignalingMessage` は 0067 で追加済みのため変更不要。追加差分は以下の 1 行のみ。

```diff
 protected fun createChannel(
     ...
     dataChannelSignaling: Boolean? = null,
+    ignoreDisconnectWebSocket: Boolean? = null,
     onSignalingMessage: ((SoraMediaChannel, SoraSignalingDirection, SoraSignalingTransportType, String) -> Unit)? = null,
 ): SoraMediaChannel
```

`SoraMediaChannel(...)` コンストラクタ呼び出しにも `ignoreDisconnectWebSocket = ignoreDisconnectWebSocket` を追加する。デフォルト `null` で既存呼び出し元に影響なし。

### テストクラスと待機戦略

`SoraCloseTypeE2ETest.kt` を新規作成し、`SoraE2ETestBase` を継承する。テストフロー:

```
1. dataChannelSignaling = true, ignoreDisconnectWebSocket = true で接続
2. onSignalingMessage でメッセージを蓄積（mutableListOf）
3. switched 受信を CompletableDeferred で待つ（0067 と同様）
4. disconnect() を呼ぶ
5. onClose を CompletableDeferred で待つ
6. 蓄積した onSignalingMessage から以下を検証:
   a. 【必須】type: "disconnect" (direction=SENT, transportType=DATA_CHANNEL) が送信されていること。
      これを満たすことで DataChannel signaling only の切断経路が成立したと見なす。
      単に `onClose` が来ただけでは WebSocket 経由切断と区別がつかないため、この検証が本テストの中核である。
   b. 【任意】type: "close" (direction=RECEIVED, transportType=DATA_CHANNEL) が存在すれば受信していること。
      `data_channel_signaling_close_message` が Sora 側で有効かどうかに依存するため、非存在でもテスト失敗とはしない。
```

### switched 待機とスキップ判定

0067 と同様に、offer の `data_channels` 非存在を `AtomicBoolean` で検出し、JUnit スレッドで `assumeTrue(false)` によりスキップする。

### ロール構成

recvonly（`enableVideoDownstream(null)`）で十分。映像送信は不要であり `DummyVideoCapturer` も不要。

## 完了条件

- DataChannel signaling only（`dataChannelSignaling = true`, `ignoreDisconnectWebSocket = true`）で接続し、切断時に `type: "disconnect"` が `direction == SENT`, `transportType == DATA_CHANNEL` で送信されることを検証する e2e テストが追加されていること。
- 接続先 Sora が `data_channel_signaling` 非対応の場合はテストがスキップされること。
- Gradle Managed Device (pixelApi35) で完走すること。
- `CHANGES.md` の `develop` セクション `### misc` にエントリを追記すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETestBase.kt`
  - `createChannel` に `ignoreDisconnectWebSocket: Boolean? = null` 引数を追加
  - `SoraMediaChannel(...)` コンストラクタ呼び出しに `ignoreDisconnectWebSocket` を追加
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraCloseTypeE2ETest.kt`（新規）
  - `SoraE2ETestBase` を継承し、切断経路検証テストを実装
- `CHANGES.md`

## 依存関係

- issue 0067（`SoraE2ETestBase`, `onSignalingMessage` フック, `dataChannelSignaling` 対応）— 完了済み
- 接続先 Sora サーバーが `data_channel_signaling` を有効にしていることが前提

## 解決方法

- `SoraE2ETestBase.createChannel` に `ignoreDisconnectWebSocket: Boolean? = null` 引数を追加し、`SoraMediaChannel(...)` コンストラクタ呼び出しに転送した。デフォルト `null` で既存呼び出し元に影響なし。
- `SoraCloseTypeE2ETest.kt` を新規作成し、`SoraE2ETestBase` を継承して切断経路検証テストを実装した。
  - `dataChannelSignaling = true`, `ignoreDisconnectWebSocket = true` で接続。
  - `onSignalingMessage` で全メッセージを `CopyOnWriteArrayList` に蓄積（SDK コールバックスレッドと JUnit スレッドのスレッドセーフ対応）。
  - `CapturedMessage` はテストメソッド内のローカル `data class`（`SoraSignalingE2ETest` の `SwitchedInfo` と一貫）。
  - switched 受信を `CompletableDeferred` で待機し、`onClose` / `onError` を `completeExceptionally` で伝搬。catch ブロックで `dataChannelSignalingUnsupported` を再確認し、非対応環境では例外より skip を優先する競合対策を実施。
  - `disconnect()` 後に `onClose` を待機し、code=1000 の正常切断を確認。
  - 蓄積メッセージから `type: "disconnect"` が `direction=SENT, transportType=DATA_CHANNEL` で送信されたことを検証。
- `SoraSignalingE2ETest.kt` にも同様の switched 待機競合対策を適用した。
- `CHANGES.md` の既存 E2E テストエントリに切断経路検証テストを追記。
- `./gradlew :sora-android-sdk:compileDebugAndroidTestKotlin :sora-android-sdk:testDebugUnitTest :sora-android-sdk:ktlintCheck` が成功することを確認済み。
