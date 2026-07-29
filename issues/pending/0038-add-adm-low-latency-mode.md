# ADM の low latency mode 設定を追加する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## pending 理由

libwebrtc 側が low latency mode を設定する API を提供しているかどうかの再確認が前提となるため pending とする。

## 目的

`AudioDeviceModule`（ADM）に対して low latency mode を設定できるようにし、音声の遅延を低減する。

Android O 以降では低遅延の音声出力モードが利用できるため、これを ADM に設定できるようにしたい。

## 優先度根拠

- 低遅延の音声出力は利用者にとって有用だが、libwebrtc 側で対応 API が提供されていることが前提となる。
- 前提条件が満たされているか不確実なため、現時点では Low とする。

## 現状

`RTCComponentFactory.kt` の `createJavaAudioDevice()` で `JavaAudioDeviceModule.builder(appContext)` を用いて ADM を生成しているが、low latency mode を設定する箇所は存在しない。現状ビルダーで設定しているのは以下の項目に留まる。

- `setUseHardwareAcousticEchoCanceler`
- `setUseHardwareNoiseSuppressor`
- `setAudioRecordErrorCallback` / `setAudioTrackErrorCallback`
- `setAudioSource`
- `setUseStereoInput` / `setUseStereoOutput`

## 設計方針

- libwebrtc の `JavaAudioDeviceModule.Builder` に low latency mode を設定する API が存在するかを確認する。
- API が存在する場合、`RTCComponentFactory.createJavaAudioDevice()` のビルダー設定に追加する。
- low latency mode を有効化するかどうかを `SoraAudioOption` で指定できるようにするかを検討する。

## 完了条件

- libwebrtc が low latency mode 設定 API を提供しているかを確認すること。
- API が提供されている場合、ADM の生成時に low latency mode を設定できること。

## 解決方法
