# Sora Android SDK のステレオ音声送信に対応する

- Created: 2026-09-04
- Completed: 2026-09-04
- Branch: feature/add-sdk-stereo-audio-send
- Polished: {YYYY-MM-DD}

## 目的

Sora Android SDK からステレオ音声を送信できるようにする。

利用者が `SoraAudioOption` で入力音声のステレオ化を指定でき、SDK が標準の `JavaAudioDeviceModule` にその設定を反映できるようにする。本 issue は音声送信側の対応に限定し、受信音声の再生や分析は対象外とする。

## 現状

- `SoraAudioOption` から音声入力のチャンネル数を指定できず、標準の `JavaAudioDeviceModule` はモノラル入力で初期化される。
- `RTCComponentFactory.createJavaAudioDevice` は `JavaAudioDeviceModule.Builder` を生成しているが、ステレオ入力を指定する `setUseStereoInput` を利用者の設定から反映していない。
- ステレオ入力を利用する端末や音声ソースを選択できず、ステレオ音声を送信するアプリは SDK の内部実装に依存する必要がある。
- 音声設定の内容が `SoraMediaChannel` の接続時ログに含まれておらず、`audioSource` とステレオ入力設定の確認が難しい。
- `issues/0076-add-e2e-stereo-audio.md` はステレオ音声の e2e 検証、`issues/0084-add-stereo-audio-receive-analysis-sample.md` は受信音声の分析が目的であり、SDK の入力設定を公開する本 issue とは目的が異なる。

## 設計方針

1. `SoraAudioOption` に標準の `JavaAudioDeviceModule` 生成時に利用する `audioSource` と `useStereoInput` を追加する。`useStereoInput` のデフォルト値は `false` とし、既存のモノラル送信を維持する。
2. `RTCComponentFactory.createJavaAudioDevice` で `audioSource` と `useStereoInput` を `JavaAudioDeviceModule.Builder` に渡す。カスタム `audioDeviceModule` が指定されている場合は SDK が標準 ADM を生成しないため、設定の反映をカスタム ADM の責務とする。
3. `SoraMediaChannel` の接続時設定サマリに `audioSource` と `useStereoInput` を含め、実際に利用される音声入力設定を確認できるようにする。
4. ステレオ出力や受信時の SDP / AudioAttributes 設定は本 issue に含めず、入力音声をステレオで取得して送信するための SDK 設定に絞る。

## 完了条件

- `SoraAudioOption.useStereoInput` を公開 API として指定できること。
- `useStereoInput = true` の場合、標準 `JavaAudioDeviceModule` の `setUseStereoInput(true)` に設定が渡されること。
- `audioSource` を指定した場合、標準 `JavaAudioDeviceModule` の `setAudioSource` に設定が渡されること。
- `useStereoInput` のデフォルト値が `false` であり、既存利用者の音声送信がモノラルのまま維持されること。
- 接続時ログから `audioSource` と `useStereoInput` の設定値を確認できること。
- SDK のビルドと既存テストが成功し、変更履歴にステレオ入力設定の追加が記載されていること。

## 解決方法

### 実装

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/option/SoraAudioOption.kt` に `audioSource` と `useStereoInput` を追加した。
  - `audioSource` の既定値は既存の音声入力経路に合わせた。
  - `useStereoInput` の既定値は `false` とした。
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/RTCComponentFactory.kt` の `createJavaAudioDevice` で、`audioSource` と `useStereoInput` を `JavaAudioDeviceModule.Builder` に設定するようにした。
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/SoraMediaChannel.kt` の接続時設定サマリに `audioSource` と `useStereoInput` を追加した。

### 変更履歴

- `CHANGES.md` に `SoraAudioOption` から `audioSource`、`useStereoInput`、`useStereoOutput` を指定できるようにしたことを記載した。
