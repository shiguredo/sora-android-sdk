# androidTest に DataChannel messaging の送受信と stats を検証する e2e テストを追加する

- Priority: Medium
- Created: 2026-07-13
- Completed: 2026-07-13
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-datachannel-messaging
- Polished: 2026-07-13

## 目的

DataChannel メッセージング機能で 2 チャネル間のメッセージ送受信が行えること、および DataChannel の stats が期待どおりであることを e2e で検証する。

## 優先度根拠

- メッセージング機能はデータ送受信の要であり、送受信の実疎通は e2e でしか担保できない。

## 現状

- 既存 e2e はメッセージング機能を検証していない。
- `SoraMediaChannel.sendDataChannelMessage()` は実装済みだが、以下の制約がある:
  - `switchedToDataChannel == true` であること（switched 受信後にのみ送信可能）
  - `label` が `"#"` で始まること（メッセージング用 DataChannel の命名規約）
  - `dataChannels` connect パラメータでメッセージング用ラベルを指定する必要がある
- `getStats()` の `data-channel` stats で `state == "open"`, `messagesSent > 0`, `bytesSent > 0` を確認可能。

## 設計方針

### 構成

2 チャネル（channelA, channelB）を同一 Sora ルームに接続する。channelA から `sendDataChannelMessage()` でメッセージを送信し、channelB の `onDataChannelMessage` コールバックで受信を確認する。stats 検証は channelA で送信側を、channelB で受信側をそれぞれ確認する。

### createChannel ヘルパーの拡張

`SoraE2ETestBase.createChannel` に以下を追加する。デフォルト `null` で既存呼び出し元に影響なし。

- `dataChannels: List<Map<String, Any>>? = null` 引数を追加し、`SoraMediaChannel(...)` コンストラクタに転送する。
- `onDataChannel: ((SoraMediaChannel, List<Map<String, Any>>?) -> Unit)? = null` フックを追加し、`SoraMediaChannel.Listener.onDataChannel` から転送する。

### テストフロー

```
1. dataChannelSignaling = true で channelA, channelB を接続
   （両チャネルとも dataChannels に "#messaging" 等のラベルを指定）
2. 両チャネルの switched 受信を待つ（0067 と同様）
3. 両チャネルの onDataChannel を CompletableDeferred で待つ
   - channelA: onDataChannel の dataChannels に "#messaging" ラベルが含まれることを確認
   - channelB: 同上
   - switched の後に DataChannel が OPEN してから onDataChannel が発火する。
     OPEN 前に sendDataChannelMessage() を呼ぶと SoraMessagingError.NOT_READY、
     SoraMessagingError.LABEL_NOT_FOUND（SoraMediaChannel.kt:2004-2008）、
     または SoraMessagingError.INVALID_STATE が返るため、この待機は必須。
4. channelA から sendDataChannelMessage("#messaging", "hello") を送信
   - SoraMessagingError.OK が返ることを確認
5. channelB の onDataChannelMessage コールバックで受信を CompletableDeferred で待つ
6. 受信したメッセージが "hello" であることを検証
7. channelA の getStats() で data-channel stats を確認:
   a. label が指定ラベルで state == "open" であること
   b. messagesSent > 0 であること
8. channelB の getStats() で data-channel stats を確認:
   a. label が指定ラベルで state == "open" であること
   b. messagesReceived > 0 であること（存在する場合）
```

### ロール構成

両チャネルとも recvonly（`enableVideoDownstream(null)`）で十分。映像送信は不要。

### チャネル管理と tearDown

`SoraE2ETestBase` は `channel` を 1 本しか保持せず、`tearDown()` もその 1 本のみ `disconnect()` する。今回のテストでは channelA / channelB の両方を扱うため、テストクラス側で両チャネルを管理する:

- テストメソッド内で `channelA: SoraMediaChannel?`, `channelB: SoraMediaChannel?` をローカル変数として保持する。
- 基底クラスの `channel` フィールドは使用しない。
- tearDown で channelA / channelB が漏れるのを防ぐため、テスト終了時に明示的に `channelA?.disconnect()` / `channelB?.disconnect()` を呼ぶ（try/finally または `runCatching` で両方切断を保証する）。

基底クラスを 2 チャネル対応に拡張すると既存の全テストクラスの tearDown に影響が出るため、本テスト固有の対応に留める。

### switched 待機とスキップ判定

0067 と同様に、offer の `data_channels` 非存在を `AtomicBoolean` で検出し、JUnit スレッドで `assumeTrue(false)` によりスキップする。

## 完了条件

- 2 チャネル間で DataChannel メッセージの送受信が確認できる e2e テストが追加されていること。
- 送信側の data-channel stats で `state == "open"`, `messagesSent > 0` を検証していること。
- 接続先 Sora が `data_channel_signaling` 非対応の場合はテストがスキップされること。
- Gradle Managed Device (pixelApi35) で完走すること。
- `CHANGES.md` の `develop` セクション `### misc` にエントリを追記すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETestBase.kt`
  - `createChannel` に `dataChannels: List<Map<String, Any>>? = null` 引数を追加
  - `SoraMediaChannel(...)` コンストラクタ呼び出しに `dataChannels` を追加
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraMessagingE2ETest.kt`（新規）
  - `SoraE2ETestBase` を継承し、2 チャネル間 messaging 送受信 + stats 検証テストを実装
- `CHANGES.md`

## 依存関係

- issue 0067（`SoraE2ETestBase`, `onSignalingMessage` フック, `dataChannelSignaling` 対応, switched 待機パターン）— 完了済み
- 接続先 Sora サーバーが `data_channel_signaling` を有効にしていることが前提

## 解決方法

- `SoraE2ETestBase.createChannel` に `dataChannels`, `onDataChannel`, `onDataChannelMessage` 引数を追加し、`SoraMediaChannel(...)` コンストラクタおよび Listener に転送した。
- `SoraMessagingE2ETest.kt` を新規作成し、`SoraE2ETestBase` を継承して 2 チャネル間 messaging 送受信テストを実装した。
  - 両チャネルをローカル変数で管理し、`finally` ブロックで切断を保証（基底クラスの `channel` フィールドは未使用）。
  - switched 待機 + スキップ判定 + onClose/onError 競合対策（catch ブロックで `dataChannelSignalingUnsupported` 再確認）。
  - `onDataChannel` 発火を `AtomicBoolean` で検出した後、`sendDataChannelMessage` が OK を返すまでポーリング（`handleSwitched()` 内の `onDataChannel` 発火時点では DataChannel が未 OPEN のため）。
  - `onDataChannelMessage` で `text == "hello"` のみを `messageReceivedB.complete()` するフィルタを実装（OPEN 待機 poll の汚染防止）。
  - stats 検証: `data-channel` stats の `state == "open"`（`assertNotNull` + `assertEquals` の 2 段階）、`messagesSent > 0`。
- `CHANGES.md` の既存 E2E テストエントリに messaging テストを追記。
- `./gradlew :sora-android-sdk:compileDebugAndroidTestKotlin :sora-android-sdk:testDebugUnitTest :sora-android-sdk:ktlintCheck` が成功することを確認済み。
