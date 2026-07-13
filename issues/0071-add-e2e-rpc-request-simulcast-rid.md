# androidTest に RPC (RequestSimulcastRid) の e2e テストを追加する

- Priority: Medium
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-rpc-request-simulcast-rid

## 目的

RPC 機能で simulcast の受信 rid を切り替えられることを e2e で検証する。sora-js-sdk の `e2e-tests/tests/rpc.test.ts` (RequestSimulcastRid) に相当する。

## 優先度根拠

- RPC は比較的新しい機能で、`rpc()` の実接続における往復と副作用 (rid 切替) は e2e でしか担保できない。
- simulcast 疎通 (0066) を前提とするため、その後続として Medium とする。

## 現状

- 既存 e2e は RPC 機能を検証していない。
- `SoraMediaChannel.rpc()` は実装済みだが、実際の RPC 往復を検証する自動テストがない。

## 設計方針

- simulcast を有効にした送信チャネルと受信チャネルを接続する。
- 受信側で `rpc()` により `RequestSimulcastRid` 相当の RPC を発行し、受信する rid を切り替える。
- 切替の効果を `getStats()` の受信映像解像度 (inbound-rtp の frameWidth / frameHeight) の変化、または有効になっている rid の変化で確認する。
- RPC の対応可否は Sora のバージョンに依存するため、非対応環境ではスキップ判定を入れる。

## 完了条件

- `rpc()` で rid を切り替え、その効果を stats で確認する e2e テストが追加されていること。
- Sora が RPC 非対応の場合はテストをスキップする判定が入っていること。
- Gradle Managed Device (pixelApi35) で完走すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt`

## 依存関係

- issue 0066 (simulcast 送信の e2e 基盤)
- issue 0065 (2 チャネル構成の共通ヘルパー)

## 解決方法
