# androidTest に sendrecv 双方向の RTP 送受信を検証する e2e テストを追加する

- Priority: High
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-sendrecv-rtp-stats

## 目的

送受信の双方向で RTP が実際に流れることを e2e で検証する。現在の `SoraE2ETest.kt` は「recvonly の接続/切断」と「映像送信 (outbound-rtp)」の 2 本のみで、受信側 (inbound-rtp) の疎通も、音声を含む双方向の疎通も検証できていない。sora-js-sdk の `e2e-tests/tests/sendrecv.test.ts` および `sendonly_recvonly.test.ts` に相当する疎通確認を android にも用意する。

## 優先度根拠

- 送受信双方向の疎通は最も基礎的な動作確認であり、他の e2e の土台になる。
- 本 issue で導入する「同一プロセスで送信用と受信用の 2 チャネルを扱う共通ヘルパー」は、simulcast / messaging / spotlight など後続の e2e でも再利用するため、優先的に整備する価値が高い。

## 現状

- 既存 e2e はチャネル 1 個のみを接続する構成で、受信側 (inbound-rtp) を検証していない。
- 送信/受信を同一テスト内で同時に扱うヘルパーが未整備。
- 映像送信は `DummyVideoCapturer` (issue 0058) で実現済み。音声送信はダミー音声 (issue 0059) が前提。

## 設計方針

- 同一テスト内で送信用チャネル (sendonly もしくは sendrecv) と受信用チャネル (recvonly もしくは sendrecv) を接続する。
- 映像は `DummyVideoCapturer`、音声はダミー音声で送信する。
- `getStats()` の結果から以下を確認する。
  - 送信側: `outbound-rtp` の `kind == "audio"` / `"video"` で `bytesSent > 0`, `packetsSent > 0`
  - 受信側: `inbound-rtp` の `kind == "audio"` / `"video"` で `bytesReceived > 0`, `packetsReceived > 0`
- 2 チャネル構成を扱う共通ヘルパーを `SoraE2ETest.kt` に追加し、後続 issue から再利用できるようにする。

## 完了条件

- sendrecv 双方向で送信側の outbound-rtp と受信側の inbound-rtp がともに 0 より大きいことを確認する e2e テストが追加されていること。
- 実機マイク/カメラ権限を要求せず、Gradle Managed Device (pixelApi35) で完走すること。
- 音声を含める場合は issue 0059 のダミー音声を利用すること (映像のみ先行実装も可)。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt` (テストと 2 チャネルヘルパーを追加)

## 依存関係

- issue 0058 (DummyVideoCapturer, 完了済み)
- issue 0059 (ダミー音声。音声を含む検証の前提)

## 解決方法
