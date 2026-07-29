# androidTest に音声のみ送信の e2e テストを追加する

- Priority: Medium
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-sendonly-audio

## 目的

音声のみを送信する sendonly 構成で、音声の RTP が実際に送出されることを e2e で検証する。sora-js-sdk の `e2e-tests/tests/sendonly_audio.test.ts` に相当する。

## 優先度根拠

- 音声送信は基本機能でありながら、現状は `initialAudioHardMute = true` で回避され自動テストがない。
- ダミー音声 (issue 0059) の整備が前提となるため、その後続として Medium とする。

## 現状

- 既存 e2e は音声送信を検証しておらず、音声はハードミュートで回避している。
- ダミー音声入力 (issue 0059) が未完了の間は実装できない。

## 設計方針

- `enableAudioUpstream()` かつ映像なしの sendonly で接続する。
- 音声はダミー音声 (issue 0059 の `AudioDeviceModule` 注入) で送信する。
- `getStats()` の `outbound-rtp` で `kind == "audio"` の `bytesSent > 0`, `packetsSent > 0` を確認する。
- 実機マイク権限を要求しないこと。

## 完了条件

- 音声のみ送信で audio outbound-rtp が 0 より大きいことを確認する e2e テストが追加されていること。
- 実機マイク権限を要求しないこと。
- Gradle Managed Device (pixelApi35) で完走すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt`

## 依存関係

- issue 0059 (ダミー音声。本 issue の前提)

## 解決方法
