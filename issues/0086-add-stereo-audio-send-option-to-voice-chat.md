# sora-android-sdk-samples のボイスチャットにステレオ音声送信オプションを追加する

- Created: 2026-09-04
- Completed: {YYYY-MM-DD}
- Branch: feature/add-stereo-audio-send-option-to-voice-chat
- Polished: {YYYY-MM-DD}

## 目的

`sora-android-sdk-samples` のボイスチャットから、モノラル / ステレオを選択して音声を送信できるようにする。

SDK 本体の `SoraAudioOption.useStereoInput` は利用できるため、ボイスチャットの設定画面からステレオ音声送信を指定し、実際の接続で動作を確認できるようにする。本 issue はボイスチャットの設定追加に限定し、他のサンプル、SDK 本体の API 変更、受信音声の分析・再生は対象外とする。

## 現状

- SDK 側のステレオ音声入力設定は `issues/closed/0085-add-sdk-stereo-audio-send.md` の対応で利用可能になっている。
- `VideoChatRoomSetupActivity` と `SimulcastSetupActivity` には既に `モノラル` / `ステレオ` の選択項目があり、`AUDIO_STEREO` を経由して `SoraVideoChannel.audioStereo` に渡している。
- `VoiceChatRoomSetupActivity` と `VoiceChatRoomActivity` にはステレオ送信の選択項目と設定の受け渡しがない。
- `SoraAudioChannel` の `connect` は `SoraAudioOption` の音声処理設定を構成しているが、`useStereoInput`、ステレオ送信時の `audioSource`、自動ゲイン調整の切り替えを設定していない。
- `activity_voice_chat_room_setup.xml` には、ステレオ音声を選択する `signaling_selection` がない。

## 設計方針

1. `VoiceChatRoomSetupActivity` に `モノラル` / `ステレオ` の選択項目を追加する。初期値はモノラルとする。
2. 選択値を `AUDIO_STEREO` として `VoiceChatRoomActivity` へ渡し、`VoiceChatRoomActivity` から `SoraAudioChannel` へ明示的に渡す。
3. `SoraAudioChannel` に `audioStereo: Boolean = false` を追加し、ステレオの場合だけ `SoraAudioOption.useStereoInput = true` を設定する。ステレオ送信時の `audioProcessingAutoGainControl` と `audioSource` は `SoraVideoChannel` の既存実装とそろえる。
4. Extra が未指定の場合や `モノラル` を選択した場合は `useStereoInput = false` とし、既存のボイスチャットの接続挙動を維持する。`RECVONLY` では音声上りがないため、ステレオ入力設定が送信処理に影響しないことを確認する。
5. スポットライト、RPC チャット、スクリーンキャストなど他のサンプルへの対応は本 issue に含めず、別の対応単位として扱う。

## 完了条件

- ボイスチャットの設定画面から、モノラル / ステレオを選択して接続を開始できること。
- ステレオを選択した場合、`SoraAudioChannel` が接続に利用する `SoraAudioOption` の `useStereoInput` が `true` になること。
- ステレオを選択した場合、`audioProcessingAutoGainControl = false` と `audioSource = MediaRecorder.AudioSource.CAMCORDER` が既存のステレオ送信実装と同じように設定されること。
- モノラルを選択した場合、および `AUDIO_STEREO` が未指定の場合は、`useStereoInput = false` のまま既存挙動になること。
- `SENDRECV` / `SENDONLY` / `RECVONLY` の各ロールで接続設定が破綻せず、音声上りのない `RECVONLY` に不要な送信処理が発生しないこと。
- ステレオ入力に対応した実機でステレオを選択して接続し、受信側で左右 2 チャネルの音声が送信されていることを確認できること。
- サンプル集のビルドと既存テストが成功すること。テストを追加する場合もモックやスタブを使用しないこと。
- `sora-android-sdk-samples/CHANGES.md` にボイスチャットのステレオ音声送信オプション追加を記載すること。

## 解決方法

- `sora-android-sdk-samples/samples/src/main/res/layout/activity_voice_chat_room_setup.xml` に `audioStereoSelection` を追加する。
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/VoiceChatRoomSetupActivity.kt` に選択肢、表示名、ドロップダウン設定、`AUDIO_STEREO` の Intent 追加を実装する。
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/VoiceChatRoomActivity.kt` で `AUDIO_STEREO` を `Boolean` に変換し、`SoraAudioChannel` へ渡す。
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/facade/SoraAudioChannel.kt` のコンストラクターおよび `connect` を拡張し、`SoraMediaOption.audioOption` にステレオ入力設定を反映する。
- ステレオ送信時の音声設定は `SoraVideoChannel` の既存実装と整合させ、モノラルおよび Extra 省略時の既定値を維持する。
- 実機で 3 つのロールの接続とステレオ / モノラル切り替えを確認し、サンプル集をビルドして既存テストを実行する。必要な変更を `CHANGES.md` に記載する。
