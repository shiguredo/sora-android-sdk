# SoraAudioOption に AudioAttributes を追加する

- Created: 2026-09-03
- Completed: 2026-09-03
- Branch: feature/add-android-stereo-audio-receive-audio-attributes
- Polished: 2026-09-03

## 目的

Android のステレオ音声受信を実機で成立させるための対応の一環として、`SoraAudioOption` に `audioAttributes: AudioAttributes?` を追加し、`JavaAudioDeviceModule.Builder#setAudioAttributes` に渡す。利用者が `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC` を指定できるようにすることで、libwebrtc の既定 `USAGE_VOICE_COMMUNICATION` + `CONTENT_TYPE_SPEECH` によって Android の AudioPolicy 側でステレオ音声がモノラルへダウンミックスされている可能性を回避する。

本 issue 単体では実機ステレオ受信は成立しない。0082-add-android-stereo-audio-receive-sdp と併用して初めて実機での受信ステレオを検証できる。

関連 issue: 0022 (ステレオ音声受信の調査), 0031 (受信音声が Bluetooth 接続機器に入力音声として認識される問題の調査。AudioAttributes の usage を同一 API で扱う), 0076 (androidTest にステレオ音声送受信の e2e テストを追加する)。

## 現状

- `SoraAudioOption` (`sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/option/SoraAudioOption.kt`) には `useStereoInput` / `useStereoOutput` / `audioSource` / `opusParams` があるが、`AudioAttributes` を指定する API が無い
- `RTCComponentFactory.createJavaAudioDevice` (`sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/RTCComponentFactory.kt`) は `JavaAudioDeviceModule.Builder` に対して `setAudioSource` / `setUseStereoInput` / `setUseStereoOutput` を呼ぶが、`setAudioAttributes` は呼んでいない
- libwebrtc の `sdk/android/api/org/webrtc/audio/JavaAudioDeviceModule.java` には `Builder#setAudioAttributes(AudioAttributes)` が既に存在する
- libwebrtc の `sdk/android/src/java/org/webrtc/audio/WebRtcAudioTrack.java` は `overrideAttributes` を渡さない場合、`AudioAttributes.USAGE_VOICE_COMMUNICATION` + `CONTENT_TYPE_SPEECH` を組み立てる
- 0022 の調査で「answer SDP の Opus fmtp を書き換えて `channels=2` が出るところまでは到達しても、実機イヤホンではステレオ受信できない」現象が確認されている。上記の `USAGE_VOICE_COMMUNICATION` + `CONTENT_TYPE_SPEECH` による AudioPolicy 側のモノラルダウンミックスが最有力仮説であり、0031 の調査仮説 (usage による Bluetooth プロファイル選択) と同系統である。本 issue の対応 (オプション追加 + 既定挙動維持) は仮説の正否に依存せず成立する

## 設計方針

1. `SoraAudioOption` に `audioAttributes: AudioAttributes? = null` を追加する
2. `RTCComponentFactory.createJavaAudioDevice` で `audioAttributes` が非 null のときのみ `JavaAudioDeviceModule.Builder#setAudioAttributes(audioAttributes)` を呼ぶ
3. 既定 (`audioAttributes = null`) では libwebrtc の従来挙動 (`USAGE_VOICE_COMMUNICATION` + `CONTENT_TYPE_SPEECH`) を維持する
4. `SoraMediaChannel` の設定サマリログに `audioAttributes` を追加する
5. KDoc に「ステレオ受信を有効にする際は、answer SDP の Opus fmtp へ `stereo=1` / `sprop-stereo=1` を追記する SDP 書き換えと併用する必要があること」および「`USAGE_MEDIA` + `CONTENT_TYPE_MUSIC` を指定するとステレオ再生が期待できるが、Bluetooth SCO や通話ルーティングとの相互作用は利用側で確認する必要があること」を明記する

## 完了条件

- `SoraAudioOption.audioAttributes` を設定すると `WebRtcAudioTrack` の `AudioAttributes` が指定値になること
- 未設定 (null) では従来どおり `USAGE_VOICE_COMMUNICATION` + `CONTENT_TYPE_SPEECH` が使われること
- 0082 と併用した実機検証で、`useStereoOutput = true` + `audioAttributes = AudioAttributes.Builder().setUsage(USAGE_MEDIA).setContentType(CONTENT_TYPE_MUSIC).build()` の状態でイヤホンからステレオ音声が聴こえること (実機検証結果は 0082 側にも記録する)
- CHANGES.md に追記があること

## 変更履歴案

- [ADD] `SoraAudioOption` に `audioAttributes` を追加する

## 解決方法

### 実装

- `SoraAudioOption` に `audioAttributes: AudioAttributes? = null` を追加した
  - KDoc にステレオ受信時の SDP 書き換え併用の必要性と `USAGE_MEDIA` + `CONTENT_TYPE_MUSIC` の注意を明記した
  - カスタム `audioDeviceModule` 指定時は無視される旨を明記した
- `RTCComponentFactory` の `createJavaAudioDevice` で `audioAttributes` が非 null のときのみ `JavaAudioDeviceModule.Builder#setAudioAttributes` を呼ぶようにした
  - 未指定 (null) では従来どおり libwebrtc の既定挙動を維持する
- `SoraMediaChannel` の設定サマリログに `audioAttributes` を追加した

### テスト

- `SoraAudioOptionTest` を新規追加し、既定 null・値保持・`useStereoOutput` との併用保持を検証する
- Builder 分岐の自動検証はネイティブ依存のため見送り、実機検証は 0082 との併用で行う
- ローカル実行は Android SDK 不足のため未実施とし、CI での検証に委ねる

### 変更履歴

- `CHANGES.md` の `## develop` に `[ADD]` エントリを追加した
