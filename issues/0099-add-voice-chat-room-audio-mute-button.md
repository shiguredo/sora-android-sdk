# sora-android-sdk-samples の VoiceChatRoom に音声ミュートボタンを追加する

- Created: 2026-09-11
- Completed: {YYYY-MM-DD}
- Branch: feature/add-voice-chat-room-audio-mute-button
- Polished: {YYYY-MM-DD}

## 目的

`sora-android-sdk-samples` の VoiceChatRoom は現状 Close ボタンしかなく、音声をミュートする手段がない。音声のみの通話で送信音声のミュートを切り替えられるように、音声ミュートボタンを追加する。

## 現状

- `VoiceChatRoomActivity` は `ActivityVoiceChatRoomBinding` の `closeButton` にしかリスナーを登録しておらず、音声ミュートの操作ができない。
- `activity_voice_chat_room.xml` は `closeButton` のみを持ち、ミュートボタンがない。
- `SoraAudioChannel` は `connect` / `disconnect` / `dispose` のみを公開しており、音声ミュートを操作する API を持たない。
- SDK の `SoraMediaChannel` には音声用の `setAudioSoftMute(muted)` と `setAudioHardMute(muted)` があるが、`SoraAudioChannel` はこれらを公開していない。
- 一方、`VideoChatRoomActivity` と `SimulcastActivity` は `MicMuteController` を使い、`ON` -> `SOFT_MUTED` -> `HARD_MUTED` -> `ON` の 3 状態でマイクミュートを切り替えている。

## 設計方針

- VoiceChatRoom にも音声ミュートボタンを追加し、既存サンプルと同じ `MicMuteController` を使って `ON` -> `SOFT_MUTED` -> `HARD_MUTED` -> `ON` の 3 状態を切り替えられるようにする。
- `SoraAudioChannel` に音声ミュート用のメソッドを追加し、`SoraMediaChannel.setAudioSoftMute` / `setAudioHardMute` へ委譲する。ソフトミュートは同期、ハードミュートは suspend 関数になる。
- `VoiceChatRoomActivity` で `MicMuteController` を生成し、`setSoftMute` / `setHardMute` を `SoraAudioChannel` のミュートメソッドへつなぐ。
- ミュート状態のアイコン表示は `VideoChatRoomActivity` / `SimulcastActivity` と揃え、`ON` / `SOFT_MUTED` / `HARD_MUTED` に対応する drawable を切り替える。
- `activity_voice_chat_room.xml` に Close ボタンと並べてミュートボタンを追加する。既存の `ic_mic_white_48dp` / `ic_mic_off_white_48dp` / `ic_mic_off_black_48dp` を流用する。
- `role` が `RECVONLY` のときは上流音声が無いためミュート操作は実質無効になる。この場合の扱い（ボタンの無効化や状態表示）は実装時に既存サンプルの流儀に合わせて判断する。

## 完了条件

- VoiceChatRoom の画面に音声ミュートボタンが表示されること。
- ボタンを押すたびに `ON` -> `SOFT_MUTED` -> `HARD_MUTED` -> `ON` と状態が切り替わり、アイコンと実際の送信音声の状態が一致すること。
- `role` が `SENDRECV` / `SENDONLY` のとき、ミュート状態が実際の送信音声に反映されること。
- 既存の接続・切断・画面遷移の挙動が変わらないこと。
- `sora-android-sdk-samples/CHANGES.md` に機能追加を記載すること。

## 変更対象ファイル

- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/VoiceChatRoomActivity.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/facade/SoraAudioChannel.kt`
- `sora-android-sdk-samples/samples/src/main/res/layout/activity_voice_chat_room.xml`
- `sora-android-sdk-samples/CHANGES.md`

## 解決方法
