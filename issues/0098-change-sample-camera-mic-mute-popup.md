# sora-android-sdk-samples のカメラ・マイクのミュート切替時にポップアップを表示する

- Created: 2026-09-11
- Completed: {YYYY-MM-DD}
- Branch: feature/change-sample-camera-mic-mute-popup
- Polished: {YYYY-MM-DD}

## 目的

`sora-android-sdk-samples` のカメラ・マイクのミュートボタンは、押すたびに状態が巡回するだけで、いまどのミュート状態なのかがアイコンの色違いだけでは分かりづらい。切替時にポップアップ（Toast）でメッセージを表示し、現在のミュート状態を明確に把握できるようにする。

## 現状

カメラ・マイクのミュートは、いずれもボタンを押すたびに複数状態を巡回する。

- カメラミュートは `ON` -> `SOFT_MUTED` -> `HARD_MUTED` -> `ON` の 3 状態を巡回する。
  - `VideoChatRoomActivity.toggleCamera` と `SimulcastActivity.toggleCamera` が `channel.setCameraSoftMuted` / `channel.setCameraHardMuted` を呼び分けている。
  - 状態の反映は各 Activity の `SoraVideoChannel.Listener.onCameraMuteStateChanged` で行い、アイコンを差し替えているだけ。
  - `RpcChatActivity.handleToggleCamera` は `ON` と `SOFT_MUTED` の 2 状態のみを切り替える。
- マイクミュートは `MicMuteController` が `ON` -> `SOFT_MUTED` -> `HARD_MUTED` -> `ON` の 3 状態を巡回する。
  - `MicMuteController` は `showMicOn` / `showMicSoft` / `showMicHard` コールバックでアイコン更新のみを行っている。
  - `RpcChatActivity.handleToggleMute` は `channel.mute` による 2 状態のミュートのみで、`MicMuteController` を使っていない。
- 各 Activity は接続エラー表示などで `Toast` を既に利用している。`RpcChatActivityUI` には `showToastOnUI` があるが、`VideoChatRoomActivityUI` / `SimulcastActivityUI` には Toast 表示用のヘルパーがない。
- そのため、どのミュート状態に遷移したのかを目視で判別しづらく、ソフトミュートとハードミュートの違いが伝わらない。

## 設計方針

- カメラ・マイクのミュート状態が変わるたびに、遷移後の状態を `Toast` で表示する。
  - カメラ: `カメラ: ON` / `カメラ: ソフトミュート` / `カメラ: ハードミュート`
  - マイク: `マイク: ON` / `マイク: ソフトミュート` / `マイク: ハードミュート`
- 状態遷移を一元的に扱っている箇所で表示する。カメラは `onCameraMuteStateChanged`、マイクは `MicMuteController` の状態更新コールバックを起点にする。
  - 状態の確定値を起点にすることで、`setCameraHardMuted` / `setAudioHardMuted` の失敗時にソフトミュートへ戻る場合も、実際の状態と表示が一致する。
- Toast は UI スレッドで表示する。既存の `RpcChatActivityUI.showToastOnUI` と同じ方針に揃え、`VideoChatRoomActivityUI` / `SimulcastActivityUI` にも表示用のヘルパーを追加するか、各 Activity で `runOnUiThread` を使って表示する。
- `RpcChatActivity` はカメラ 2 状態・マイク 2 状態だが、対象の状態遷移で同じく Toast を表示する。
- 状態名の文言はソフトミュートとハードミュートの違いが伝わるようにする。文言は日本語で統一し、全角と半角の間には半角スペースを入れる。

## 完了条件

- `VideoChatRoomActivity` / `SimulcastActivity` で、カメラボタンを押すたびに遷移後のカメラ状態が Toast で表示されること。
- `VideoChatRoomActivity` / `SimulcastActivity` で、マイクボタンを押すたびに遷移後のマイク状態が Toast で表示されること。
- `RpcChatActivity` でも、カメラ・マイクボタンを押すたびに遷移後の状態が Toast で表示されること。
- いずれのサンプルでも、実際のミュート状態と表示される文言が一致すること。
- 既存のミュートの状態遷移・アイコン表示・送受信の挙動が変わらないこと。
- `sora-android-sdk-samples/CHANGES.md` に変更を記載すること。

## 変更対象ファイル

- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/VideoChatRoomActivity.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/SimulcastActivity.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/RpcChatActivity.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/util/MicMuteController.kt`
- `sora-android-sdk-samples/CHANGES.md`

## 解決方法
