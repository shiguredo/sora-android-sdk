# Sora Android SDK サンプルのビットレート選択肢を Sora DevTools と揃える

- Created: 2026-09-10
- Completed: {YYYY-MM-DD}
- Branch: feature/change-align-sample-bitrate-options-with-sora-devtools
- Polished: 2026-11-16

## 目的

接続設定画面ごとにビットレートの選択肢が異なると、Sora DevTools と同じ条件でビットレートを検証できない。映像ビットレートと音声ビットレートの選択肢を Sora DevTools に一致させ、サンプル間および Sora DevTools との間で検証条件を揃えられるようにする。

## 現状

`sora-android-sdk-samples` の接続設定画面で映像ビットレートの選択肢が画面ごとに異なっている。

- `VideoChatRoomSetupActivity` の `videoBitRateOptions`: `未指定`, `100`, `300`, `500`, `800`, `1000`, `1500`, `2000`, `2500`, `3000`, `5000`, `10000`, `15000`, `20000`, `30000`
- `SpotlightRoomSetupActivity` の `videoBitRateOptions`: `500`, `200`, `700`, `1200`, `2500`, `4000`, `5000`, `10000`, `15000`, `20000`, `30000` (先頭 2 つの順序も入れ替わっている)
- `SimulcastSetupActivity` の `videoBitRateOptions`: `200`, `500`, `700`, `1200`, `2500`, `4000`, `5000`, `10000`, `15000`, `20000`, `30000`
- `RpcChatSetupActivity` の `videoBitRateOptions`: `200`, `500`, `700`, `1200`, `2500`, `4000`, `5000`, `10000`, `15000`, `20000`, `30000`

`audioBitRateOptions` は `VideoChatRoomSetupActivity` / `SpotlightRoomSetupActivity` / `SimulcastSetupActivity` / `RpcChatSetupActivity` / `VoiceChatRoomSetupActivity` の 5 画面で `未指定`, `8`, `16`, `24`, `32`, `64`, `96`, `128`, `256` に統一されているが、Sora DevTools には `384` もある。

Sora DevTools リポジトリ (`sora-devtools`) の `src/constants.ts` の選択肢は次のとおり。

- `VIDEO_BIT_RATES`: 未指定, `10`, `30`, `50`, `100`, `300`, `500`, `800`, `1000`, `1500`, `2000`, `2500`, `3000`, `5000`, `10000`, `15000`, `20000`, `30000`, `50000`
- `AUDIO_BIT_RATES`: 未指定, `8`, `16`, `24`, `32`, `64`, `96`, `128`, `256`, `384`

## 設計方針

- 映像ビットレートと音声ビットレートの選択肢を Sora DevTools の `VIDEO_BIT_RATES` / `AUDIO_BIT_RATES` に一致させる。Sora DevTools の `""` はサンプルでは `未指定` と表示する。
- 対象は次のとおり。
  - 映像ビットレート: `VideoChatRoomSetupActivity` / `SpotlightRoomSetupActivity` / `SimulcastSetupActivity` / `RpcChatSetupActivity`
  - 音声ビットレート: 上記 4 画面と `VoiceChatRoomSetupActivity`
- 映像ビットレートの選択肢は `未指定`, `10`, `30`, `50`, `100`, `300`, `500`, `800`, `1000`, `1500`, `2000`, `2500`, `3000`, `5000`, `10000`, `15000`, `20000`, `30000`, `50000` とする。
- 音声ビットレートの選択肢は `未指定`, `8`, `16`, `24`, `32`, `64`, `96`, `128`, `256`, `384` とする。
- `SpotlightRoomSetupActivity` / `SimulcastSetupActivity` / `RpcChatSetupActivity` の映像ビットレートは先頭が数値になっているため、`未指定` を先頭に追加する。音声ビットレートは全画面ですでに `未指定` が先頭にあり、`384` の追加のみでよい。
- Sora DevTools は映像ビットレートの `15000` より大きい値に「以下はサポート外です」と表示するが、値自体は選択できる。本 issue は選択肢の一致を優先し、`20000` / `30000` / `50000` も選択肢に含める。サンプル側にサポート外の注記を追加するかは本 issue の対象外とする。
- 選択肢を画面ごとに重複定義しているため、共通の定数へ切り出すかは実装時に判断する。
- 選択肢の並びが変わることで `DropdownConfig` の `defaultIndex` が指す値も変わる。本 issue は選択肢の一致を目的とし、接続条件を変えないため、既存の実効デフォルトを維持する。新しい映像ビットレートのリストでは、`SpotlightRoomSetupActivity` は `defaultIndex = 6` で `500`、`SimulcastSetupActivity` は `defaultIndex = 13` で `5000`、`RpcChatSetupActivity` は `defaultIndex = 6` で `500`、`VideoChatRoomSetupActivity` は先頭の `未指定` (index 0) とする。音声ビットレートは全画面で先頭の `未指定` (index 0) のままとする。

## 完了条件

- `VideoChatRoomSetupActivity` / `SpotlightRoomSetupActivity` / `SimulcastSetupActivity` / `RpcChatSetupActivity` の映像ビットレートの選択肢が Sora DevTools の `VIDEO_BIT_RATES` と一致している (`""` は `未指定` として扱う)。
- `VideoChatRoomSetupActivity` / `SpotlightRoomSetupActivity` / `SimulcastSetupActivity` / `RpcChatSetupActivity` / `VoiceChatRoomSetupActivity` の音声ビットレートの選択肢が Sora DevTools の `AUDIO_BIT_RATES` と一致している (`""` は `未指定` として扱う)。
- `SpotlightRoomSetupActivity` / `SimulcastSetupActivity` / `RpcChatSetupActivity` の映像ビットレートで `未指定` を選択できる。
- 初期選択が既存の実効デフォルトのままである (`VideoChatRoomSetupActivity` の映像は `未指定`、`SpotlightRoomSetupActivity` の映像は `500`、`SimulcastSetupActivity` の映像は `5000`、`RpcChatSetupActivity` の映像は `500`、音声は全画面 `未指定`)。
