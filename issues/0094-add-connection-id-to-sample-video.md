# sora-android-sdk-samples のリモート映像に connection id を表示する

- Created: 2026-09-10
- Completed: {YYYY-MM-DD}
- Branch: feature/add-connection-id-to-sample-video
- Polished: {YYYY-MM-DD}

## 目的

`sora-android-sdk-samples` のリモート映像に Sora の connection id を表示する。`connection.created` 通知と受信ストリーム/トラックの紐付けが正しく行われているかを目視で確認できるようにする。

## 現状

- `SoraRemoteRendererSlot.onAddRemoteStream` は `ms.id` (リモート接続の connection id) をキーに `SurfaceViewRenderer` と `VideoTrack` を管理している。
- `SoraVideoChannel.Listener.onAddRemoteRenderer` は `SurfaceViewRenderer` のみを UI に渡すため、UI 側は connection id を受け取れない。
- `VideoChatRoomActivity` / `SimulcastActivity` / `RpcChatActivity` は返された renderer をタイル配置するだけで、connection id は表示していない。
- `SoraMediaChannel.Listener.onAddRemoteTrack` で video track に対応する connection id (`streamId`) を取得できる。

## 設計方針

- リモート映像の renderer に対応する connection id を UI に渡し、各タイルに重ねて表示する。
- connection id は `onAddRemoteTrack` の `streamId`、または `onAddRemoteStream` の `ms.id` から取得する。どちらもリモート接続の connection id になる。
- `SoraRemoteRendererSlot` は `ms.id` を保持しているため、`Listener.onAddRenderer` に connection id を追加して伝搬する。
- 対象は `VideoChat` のリモート映像を主とし、`Simulcast` / `RPC` も同じ経路で対応する。

## テスト方針

- 実機で sendrecv 接続し、リモート映像に connection id が表示されることを確認する。
- マルチストリームで複数のリモート映像を受信したとき、各映像に正しい connection id が表示されることを確認する。

## 完了条件

- `VideoChat` のリモート映像に connection id が表示されること。
- 複数のリモート映像を受信したときに、各映像へ正しい connection id が表示されること。
- 既存のタイル配置、映像の追加と削除、接続と切断の挙動が変わらないこと。
- `sora-android-sdk-samples/CHANGES.md` に変更を記載すること。

## 変更対象ファイル

- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/facade/SoraVideoChannel.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/util/SoraRemoteRendererSlot.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/VideoChatRoomActivity.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/SimulcastActivity.kt`
- `sora-android-sdk-samples/samples/src/main/kotlin/jp/shiguredo/sora/sample/ui/RpcChatActivity.kt`

## 解決方法
