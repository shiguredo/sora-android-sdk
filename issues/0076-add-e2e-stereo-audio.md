# androidTest にステレオ音声送受信の e2e テストを追加する

- Priority: Low
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-stereo-audio

## 目的

ステレオ音声の送受信が行えることを e2e で検証する。sora-js-sdk の `e2e-tests/tests/stereo_audio.test.ts` および `stereo_audio_sendrecv.test.ts` に相当する。

## 優先度根拠

- js-sdk のステレオ検証は左右チャネルの周波数解析 (Web Audio API) に依存しており、android には Web Audio 相当の解析手段が無いため、同等の検証をそのまま移植できない。
- 検証手段の代替設計が必要で難度が高く、優先度は Low とする。

## 現状

- 既存 e2e はステレオ音声を検証していない。
- `SoraAudioOption` に `useStereoInput` / `useStereoOutput` / `opusParams` があり、ステレオ設定自体は可能。
- 関連: issue 0022 (ステレオ音声受信の調査)。

## 設計方針

- `useStereoInput` / `useStereoOutput` と必要な `opusParams` を設定して送受信チャネルを接続する。
- ステレオであることの確認手段として、以下のいずれかを検討する。
  - SDP の Opus (`sprop-stereo` / `stereo`) パラメータの確認
  - `getStats()` の audio コーデック stats における `channels == 2` の確認
  - 音声トラックの PCM を取得して左右チャネルを解析する方法 (Web Audio の代替。実現可能性は要調査)
- 周波数解析まで行うかは実装難度を踏まえて決める。まずは設定の反映 (channels=2 / SDP) の確認に絞る案を優先する。

## 完了条件

- ステレオ音声で送受信し、ステレオであること (channels=2 もしくは SDP の stereo パラメータ) を確認する e2e テストが追加されていること。
- 実機マイク権限を要求しないこと (ダミー音声を利用)。
- Gradle Managed Device (pixelApi35) で完走すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt`

## 依存関係

- issue 0059 (ダミー音声)
- issue 0065 (2 チャネル構成の共通ヘルパー)
- 関連: issue 0022 (ステレオ音声受信の調査)

## 解決方法
