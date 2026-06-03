# Bluetooth イヤホン対応方法をドキュメント化する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## pending 理由

SDK 本体の機能追加とするか、サンプルアプリでの実装例とするか、ドキュメントでの解説に留めるかというスコープの切り分けが前提となるため pending とする。スコープが定まらないと完了条件が定義できない。

## 目的

Sora Android SDK を利用するアプリで Bluetooth イヤホンを音声入出力デバイスとして利用する方法を整理し、ドキュメント化する。

Bluetooth イヤホンの利用は音声通話アプリで一般的なニーズであり、音声入出力先を Bluetooth デバイスへ切り替える方法のノウハウを利用者に提供したい。また、Bluetooth ヘッドセット系デバイスはステレオ音声対応にも関連するため、ここで得たノウハウは他の音声機能の検討にも活用できる。

## 優先度根拠

- 音声入出力デバイスの切り替えに関するニーズが確認されており、音声系の課題として優先度を上げてよいという意見がある。
- 一方で SDK 本体に制御機能を追加するか、ドキュメント／サンプルで対応するかが未確定であり、まず方針整理が必要なため Medium とする。

## 現状

- Sora Android SDK には Bluetooth イヤホンへ音声入出力を切り替える専用 API は用意されていない。
- 音声入出力は `org.webrtc.audio.JavaAudioDeviceModule` を経由しており、デフォルトの `AudioDeviceModule` は `RTCComponentFactory.kt` の `createJavaAudioDevice()` で生成される。
  - `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/RTCComponentFactory.kt`
- 音声ソース（`audioSource`）は `SoraAudioOption` で指定でき、デフォルトは `MediaRecorder.AudioSource.VOICE_COMMUNICATION` である。
  - `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/option/SoraAudioOption.kt`
- 音声ルーティングに関わる処理は以下のクラスに存在する。
  - `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/RTCLocalAudioManager.kt`
- 出力先の切り替えは Android プラットフォームの `AudioManager` を通じてアプリ側で制御する形になっており、SDK としての手順がまとまっていない。

## 設計方針

- まず対応スコープ（SDK 本体への制御機能追加か、サンプルアプリでの実装例か、ドキュメントでの解説か）を切り分ける。
- ドキュメントとして対応する場合は、Android プラットフォームの `AudioManager` を利用して音声入出力先を Bluetooth デバイスへ切り替える手順を解説する。
- 出力先の切り替えのみを行う最小構成の実装例を起点とし、必要に応じてより踏み込んだデバイス制御を別途検討する。
- 他社の SDK における Bluetooth デバイス制御の提供方法も参考にし、SDK 本体への機能追加が妥当かどうかを判断する。

## 完了条件

- 対応スコープが切り分けられていること。
- Bluetooth イヤホンを音声入出力デバイスとして利用する方法（少なくとも出力先の切り替え）が、利用者が手順を追って実装できる粒度で整理されていること。

## 解決方法
