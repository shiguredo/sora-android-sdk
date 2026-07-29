# androidTest に spotlight 接続の e2e テストを追加する

- Priority: Medium
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-spotlight

## 目的

spotlight 機能での接続と RTP 疎通を e2e で検証する。sora-js-sdk の `e2e-tests/tests/spotlight_sendrecv.test.ts` および `spotlight_sendonly_recvonly.test.ts` に相当する。

## 優先度根拠

- spotlight は多人数配信で使われる主要機能であり、接続経路が通常と異なるため e2e で担保する価値がある。
- 基礎疎通や新規 API より優先度は下がるため Medium とする。

## 現状

- 既存 e2e は spotlight を検証していない。
- `SoraSpotlightOption` は実装済みだが自動テストがない。

## 設計方針

- `SoraSpotlightOption` を指定した送信チャネルと受信チャネルを接続する。
- 映像は `DummyVideoCapturer`、音声はダミー音声で送信する。
- `getStats()` で送信側 outbound-rtp と受信側 inbound-rtp の疎通を確認する。
- spotlight は Sora 側で有効化されている必要があるため、非対応環境ではスキップ判定を入れる。

## 完了条件

- spotlight での接続と RTP 疎通を確認する e2e テストが追加されていること。
- Sora が spotlight 非対応の場合はスキップする判定が入っていること。
- Gradle Managed Device (pixelApi35) で完走すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt`

## 依存関係

- issue 0065 (2 チャネル構成の共通ヘルパー)
- issue 0059 (音声を含める場合のダミー音声)

## 解決方法
