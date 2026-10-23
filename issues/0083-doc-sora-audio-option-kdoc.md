# SoraAudioOption の KDoc を整備する

- Created: 2026-09-03
- Completed: {YYYY-MM-DD}
- Branch: feature/update-sora-audio-option-kdoc
- Polished: {YYYY-MM-DD}

## 目的

`SoraAudioOption` の KDoc にある文書欠落と誤記を直し、誤設定時の無音失敗と公開 API 文書の誤読を防ぐ。

## 現状

- `SoraAudioOption` の `audioDeviceModule` の KDoc は、`useHardwareAcousticEchoCanceler` と `useHardwareNoiseSuppressor` の設定が無視される旨だけを書いている。カスタム ADM 指定時は `RTCComponentFactory` の `createPeerConnectionFactory` が内部生成を迂回するため、`audioSource` / `useStereoInput` / `useStereoOutput` / `audioAttributes` を含む Builder 設定が黙って無視されるが、無視される側の KDoc に記載がない
  - 分岐箇所: `RTCComponentFactory` の `createPeerConnectionFactory` 内の `audioDeviceModule` の null 判定。内部生成側の処理は `createJavaAudioDevice`
- `SoraAudioOption` の `useHardwareAcousticEchoCanceler` と `useHardwareNoiseSuppressor` の KDoc にある参照 `org.webrtc.JavaAudioDeviceModule` は誤記で、正しくは `org.webrtc.audio.JavaAudioDeviceModule` である (実装側の import は `org.webrtc.audio.JavaAudioDeviceModule`)

## 設計方針

1. 0081 のマージ後に着手する (同一ファイルへの変更の競合を避けるため)
2. `audioSource` / `useStereoInput` / `useStereoOutput` / `audioAttributes` の KDoc に、カスタム `audioDeviceModule` 指定時は無視される旨を追記する。または `audioDeviceModule` 側 KDoc の無視リストに加える。いずれか一方に絞る
3. 誤記の 2 か所を `org.webrtc.audio.JavaAudioDeviceModule` に修正する。新規追加の `audioAttributes` の KDoc は正しい表記のため対象外とする

## 完了条件

- カスタム ADM 指定時に無視される設定が KDoc から読み取れること
- KDoc のパッケージ参照が正しいこと
