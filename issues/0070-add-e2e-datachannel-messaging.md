# androidTest に DataChannel messaging の送受信と stats を検証する e2e テストを追加する

- Priority: Medium
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-datachannel-messaging

## 目的

DataChannel メッセージング機能で 2 チャネル間のメッセージ送受信が行えること、および DataChannel の stats が期待どおりであることを e2e で検証する。sora-js-sdk の `e2e-tests/tests/messaging.test.ts` および `message_header.test.ts` に相当する。

## 優先度根拠

- メッセージング機能はデータ送受信の要であり、送受信の実疎通は e2e でしか担保できない。
- 基礎疎通や新規 API より優先度は下がるため Medium とする。

## 現状

- 既存 e2e はメッセージング機能を検証していない。
- サンプルアプリ (`MessagingActivity`) では利用しているが、自動テストは存在しない。

## 設計方針

- メッセージング用ラベルを設定した 2 チャネルを接続し、一方から `sendDataChannelMessage()` で送信、他方で受信できることを確認する。
- `getStats()` の `data-channel` stats で、対象ラベルが `state == "open"` であること、送信側で `messagesSent > 0`, `bytesSent > 0` であることを確認する。
- 送受信の確認は 2 チャネル構成の共通ヘルパー (issue 0065 で整備) を利用する。

## 完了条件

- 2 チャネル間で DataChannel メッセージの送受信が確認できる e2e テストが追加されていること。
- DataChannel stats (label open, messagesSent / bytesSent) を検証していること。
- Gradle Managed Device (pixelApi35) で完走すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt`

## 依存関係

- issue 0065 (2 チャネル構成の共通ヘルパー)

## 解決方法
