# Sora Android SDK サンプルのビットレート選択肢を Sora DevTools と揃える

- Created: 2026-09-10
- Completed: {YYYY-MM-DD}
- Branch: feature/change-align-sample-bitrate-options-with-sora-devtools
- Polished: {YYYY-MM-DD}

## 目的

接続設定画面ごとにビットレートの選択肢が異なると、Sora DevTools と同じ条件でビットレートを検証できない。映像ビットレートと音声ビットレートの選択肢を Sora DevTools に一致させ、サンプル間および Sora DevTools との間で検証条件を揃えられるようにする。

## 現状

`sora-android-sdk-samples` の接続設定画面で映像ビットレートの選択肢が画面ごとに異なっている。

- `VideoChatRoomSetupActivity` の `videoBitRateOptions`: `100`, `300`, `500`, `800`, `1000`, `1500`, `2000`, `2500`, `3000`, `5000`, `10000`, `15000`, `20000`, `30000`
- `SpotlightRoomSetupActivity` の `videoBitRateOptions`: `500`, `200`, `700`, `1200`, `2500`, `4000`, `5000`, `10000`, `15000`, `20000`, `30000` (先頭 2 つの順序も入れ替わっている)
- `SimulcastSetupActivity` の `videoBitRateOptions`: `200`, `500`, `700`, `1200`, `2500`, `4000`, `5000`, `10000`, `15000`, `20000`, `30000`
- `RpcChatSetupActivity` の `videoBitRateOptions`: `200`, `500`, `700`, `1200`, `2500`, `4000`, `5000`, `10000`, `15000`, `20000`, `30000`

`audioBitRateOptions` は各画面で `8`, `16`, `24`, `32`, `64`, `96`, `128`, `256` に統一されているが、Sora DevTools には `384` もある。

Sora DevTools (`sora-devtools` の `src/constants.ts`) の選択肢は次のとおり。

- `VIDEO_BIT_RATES`: 未指定, `10`, `30`, `50`, `100`, `300`, `500`, `800`, `1000`, `1500`, `2000`, `2500`, `3000`, `5000`, `10000`, `15000`, `20000`, `30000`, `50000`
- `AUDIO_BIT_RATES`: 未指定, `8`, `16`, `24`, `32`, `64`, `96`, `128`, `256`, `384`

## 設計方針

- 映像ビットレートと音声ビットレートの選択肢を Sora DevTools の `VIDEO_BIT_RATES` / `AUDIO_BIT_RATES` と完全に一致させる。
- 選択肢の先頭は Sora DevTools と同じく `未指定` にする。Spotlight / Simulcast / RpcChat は先頭が数値になっているため、`未指定` を選べるようにする。
- 選択肢を画面ごとに重複定義しているため、共通の定数へ切り出すかは実装時に判断する。
- 選択肢の並びが変わることで `DropdownConfig` の `defaultIndex` が指す値も変わる。Simulcast は `defaultIndex = 6` で `5000`、RpcChat は `defaultIndex = 1` で `500`、Spotlight は `defaultIndex` 未指定で先頭の `500` を選んでいる。既存のデフォルト値を維持するか、意図した初期値に変更するかを実装時に決める。

## 完了条件

- VideoChat / Spotlight / Simulcast / RpcChat の映像ビットレートの選択肢が Sora DevTools の `VIDEO_BIT_RATES` と一致している。
- 各画面の音声ビットレートの選択肢が Sora DevTools の `AUDIO_BIT_RATES` と一致している。
- 各画面のデフォルト選択が意図した値になっている。
