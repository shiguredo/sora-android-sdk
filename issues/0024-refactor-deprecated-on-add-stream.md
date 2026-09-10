# deprecated な onAddStream への対応を行う

- Priority: Low
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/refactor-deprecated-on-add-stream

## 目的

libwebrtc で deprecated となっている `PeerConnection.Observer#onAddStream` / `onRemoveStream` への依存を解消し、`onTrack` ベースのリモートストリーム通知へ移行する。

`onAddStream` / `onRemoveStream` は Plan B 由来の API であり、Unified Plan では deprecated 扱いとなっている。将来的に libwebrtc から削除される可能性があるため、`onTrack` / `onRemoveTrack` ベースへ移行しておく。

## 依存関係

本 issue は `0016-add-track-stream-id`（トラックからストリーム ID を取得できるようにする）に依存する。0016 で追加される `trackToStreamId` マッピングと `onAddRemoteTrack` コールバックを利用して、`onTrack` ベースのストリーム通知を実現する。0016 の完了後に着手すること。

## 現状

`PeerChannel.kt` の `PeerConnection.Observer` 実装でリモートストリームの追加・削除を `onAddStream` / `onRemoveStream` で扱っている。

- `onAddStream` は `listener?.onAddRemoteStream(ms)` を呼び、`onRemoveStream` は `listener?.onRemoveRemoteStream(it.id)` を呼んでいる。
- `onAddRemoteStream` / `onRemoveRemoteStream` は `SoraMediaChannel.kt` を経由して `SoraMediaChannel.Listener` の同名コールバックへ通知される（公開 API）。
- 一方 `onTrack` / `onAddTrack` / `onRemoveTrack` はログ出力のみで、ストリーム通知には使われていない。

## 設計方針

- `onTrack` / `onRemoveTrack` ベースへ移行し、リモートストリームの追加・削除通知を組み立てる。
- 以下の設計課題について事前に方針を決定する必要がある（未決定の場合は pending とする）。
  1. **track 到着タイミング**: audio と video が別々の `onTrack` で上がる場合、どのタイミングで `onAddRemoteStream` を通知するか。両方揃うまで待つのか、個別に通知するのか。
  2. **stream ID の取得**: 0016 で追加される `trackToStreamId` マッピングと `onAddRemoteTrack` コールバックを利用する。
- 公開 API のシグネチャ互換性を維持できるかを確認する。
  - 維持できる場合: `[UPDATE]` として CHANGES.md に記載する。
  - 崩れる場合: `[CHANGE]` として扱い、ブランチ prefix を `feature/change-` に変更する。
- 自ストリームフィルタリング（`ms.id == connectionId`）の挙動を `onTrack` ベースでも維持する。

## 追加調査

libwebrtc m150 のソースを確認した結果を反映する。

- libwebrtc m150 の Unified Plan 経路 `ApplyRemoteDescriptionUpdateTransceiverState` は `OnTrack` -> `OnAddTrack` -> `OnAddStream` の順で発火する。
- `onAddTrack` の `ms` は、JNI の `PeerConnectionObserverJni::OnAddTrack` が `receiver->streams()` を `NativeToJavaMediaStreamArray` で渡すため、Unified Plan でも populate される。`0016-add-track-stream-id` が前提とした「Unified Plan では `ms` が常に空」は m150 では正確ではなく、add 側は `onAddTrack` の `ms` からでも stream ID を取得できる。
- `RtpReceiver.getStreams()` は `android_rtp_receiver_get_streams.patch` で追加した API で、libwebrtc 150.7871.3.0 に適用されている。upstream は m151 で対応したためパッチは削除済みで、SDK が m151 以降へ上がればパッチ依存は消える。m151 移行時に `onAddTrack` の `ms` へ寄せられるか再確認する。
- `SetAssociatedRemoteStreams` が `receiver->SetStreams(...)` を呼び、`AudioRtpReceiver::SetStreams` / `VideoRtpReceiver::SetStreams` が `stream->AddTrack(...)` するため、Java `MediaStream` の `audioTracks` / `videoTracks` は Unified Plan でも populate される。`JavaMediaStream` の `MediaStreamObserver` が native の増減を同期する。
- `onAddStream` / `onRemoveStream` は libwebrtc 側で Plan B 廃止マクロに囲まれたレガシー経路から呼ばれており、移行の必要性は変わらない。
- `0016-add-track-stream-id` で追加した `onAddRemoteTrack` / `onRemoveRemoteTrack` と既存の `onAddRemoteStream` / `onRemoveRemoteStream` を併存させるか、stream ベースを非推奨化するかを本 issue で確定する。

## 完了条件

- `onAddStream` / `onRemoveStream` への依存を解消し、`onTrack` / `onRemoveTrack` ベースでリモートストリームの追加・削除通知を行えること。
- 既存のリモートストリーム受信動作が変わらないこと。実機での動作確認テストを含むこと。
- 公開 API への影響有無を明確にし、`CHANGES.md` の `develop` セクションに該当する種別のエントリを追記すること。

## 解決方法
