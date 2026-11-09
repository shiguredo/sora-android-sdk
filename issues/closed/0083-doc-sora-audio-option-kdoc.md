# SoraAudioOption の KDoc を整備する

- Created: 2026-09-03
- Completed: 2026-11-09
- Branch: feature/update-sora-audio-option-kdoc
- Polished: 2026-11-09

## 目的

`SoraAudioOption` の KDoc にある文書欠落と誤記を直し、カスタム `audioDeviceModule` 指定時に設定が ADM へ反映されないことを読み取れるようにして、設定が有効だという誤解と公開 API 文書の誤読を防ぐ。

## 現状

- `SoraAudioOption` の `audioDeviceModule` の KDoc は、`useHardwareAcousticEchoCanceler` と `useHardwareNoiseSuppressor` の設定が無視される旨だけを書いている。カスタム ADM 指定時は `RTCComponentFactory` の `createPeerConnectionFactory` が `createJavaAudioDevice` を呼ばずに内部生成を迂回するため、`audioSource` / `useStereoInput` / `useStereoOutput` / `audioAttributes` の Builder 設定は ADM に反映されない
  - `audioAttributes` の KDoc には、0081 のマージによりカスタム ADM 指定時に無視される旨が既に記載されている
  - `useStereoOutput` は ADM の Builder 設定だけでなく、answer SDP の Opus fmtp 書き換えの制御にも使われる。`PeerChannel` の `applyStereoRewriteIfNeeded` は `audioDeviceModule` を参照せず、カスタム ADM 指定時も書き換えを行う
  - 分岐箇所: `RTCComponentFactory` の `createPeerConnectionFactory` 内の `audioDeviceModule` の null 判定。内部生成側の処理は `createJavaAudioDevice`
- `SoraAudioOption` の `useHardwareAcousticEchoCanceler` と `useHardwareNoiseSuppressor` の KDoc にある参照 `org.webrtc.JavaAudioDeviceModule` は誤記で、正しくは `org.webrtc.audio.JavaAudioDeviceModule` である (実装側の import は `org.webrtc.audio.JavaAudioDeviceModule`)
- `SoraAudioOption` の `audioDeviceModule` の KDoc にある参照 `org.webrtc.AudioDeviceModule` も誤記で、正しくは `org.webrtc.audio.AudioDeviceModule` である (同ファイルの import は `org.webrtc.audio.AudioDeviceModule`)

## 設計方針

1. 0081 はマージ済みのため着手可能 (0081 に由来する変更は develop に取り込まれている)
2. `audioSource` / `useStereoInput` / `useStereoOutput` の KDoc に、カスタム `audioDeviceModule` 指定時は SDK が ADM を生成しないため ADM の Builder 設定へ反映されない旨を追記する。`audioAttributes` は 0081 で追記済みのため対象外とする。追記先は各プロパティの KDoc とし (既存の `audioAttributes` / `initialAudioHardMute` と同じ自フィールド方式)、`audioDeviceModule` 側の無視リストへの集約は行わない
   - `useStereoOutput` は、カスタム ADM 指定時は ADM の `setUseStereoOutput` に反映されないが、answer SDP の Opus fmtp 書き換えは有効であることを区別して書く
3. パッケージ参照の誤記 3 か所を修正する
   - `useHardwareAcousticEchoCanceler` / `useHardwareNoiseSuppressor` の `org.webrtc.JavaAudioDeviceModule` を `org.webrtc.audio.JavaAudioDeviceModule` に修正する
   - `audioDeviceModule` の `org.webrtc.AudioDeviceModule` を `org.webrtc.audio.AudioDeviceModule` に修正する
   - `audioAttributes` の `org.webrtc.audio.JavaAudioDeviceModule.Builder#setAudioAttributes` は正しい表記のため変更しない

## 完了条件

- `audioSource` / `useStereoInput` / `useStereoOutput` の KDoc から、カスタム ADM 指定時に ADM へ反映されないことが読み取れること
- `useStereoOutput` の KDoc から、answer SDP の Opus fmtp 書き換えはカスタム ADM 指定時も有効であることが読み取れること
- `SoraAudioOption` の KDoc に現れる `org.webrtc.*` の参照が実在するクラスを指していること (誤記 3 か所が解消されていること)

## 解決方法

### 実装

- `SoraAudioOption` の KDoc のパッケージ参照の誤記 3 か所を修正した
  - `useHardwareAcousticEchoCanceler` / `useHardwareNoiseSuppressor` の `org.webrtc.JavaAudioDeviceModule` を `org.webrtc.audio.JavaAudioDeviceModule` に修正した
  - `audioDeviceModule` の `org.webrtc.AudioDeviceModule` を `org.webrtc.audio.AudioDeviceModule` に修正した
- `audioSource` / `useStereoInput` の KDoc に、`audioDeviceModule` が非 null の場合、 SDK は AudioDeviceModule を内部生成しないため本設定が無視される旨を追記した
- `useStereoOutput` の KDoc に、ADM の `setUseStereoOutput` には反映されないが、answer SDP の Opus fmtp への `stereo=1` / `sprop-stereo=1` の追記は本設定のみで制御され、カスタム ADM 指定時も行われる旨を追記した
- `audioAttributes` は 0081 で同旨が追記済みのため変更していない

### テスト

- KDoc のみの変更でロジックに影響しないため、テストの追加・変更は行っていない
- `./gradlew :sora-android-sdk:ktlintCheck` が通ることを確認した
- `./gradlew :sora-android-sdk:testDebugUnitTest` が通ることを確認した (89 テスト、スキップ 2、失敗 0)

### 変更履歴

- KDoc のみの変更で挙動に影響しないため、 `CHANGES.md` は更新していない
