# トラックからストリーム ID を取得できるようにする

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch: feature/add-track-stream-id

## 目的

リモートの映像・音声トラックから、そのトラックが所属するストリーム ID（`stream_id`）を取得できるようにする。これにより connection ID とトラックを結びつけられるようにする。

現状の Android SDK は公開 API も内部実装もストリームベースだが、内部実装をトラックベースへ寄せていきたい。そのためにはトラックが所属するストリーム ID を知る必要がある。また、ユースケースによっては connection ID からトラックを特定する必要があり、トラックからストリーム ID を取得できれば特定が可能になる。

具体的なユースケース例:

- マルチストリームでリモート映像の枠の中にユーザー名を出す場合、ユーザーの connection ID とトラックを結びつける必要がある。
- スポットライトでフォーカスされた人の映像に枠を出す / 大きくする場合、通知された connection ID のユーザーの映像トラックを特定する必要がある。

## 優先度根拠

- 内部実装をストリームベースからトラックベースへ移行するための前提となる機能であり、技術的な重要度は高い。
- 一方で現行のストリームベース API でも主要なユースケースは成立しており、緊急性は中程度のため Medium とする。

## 現状

リモートトラックの受信は `PeerChannel.kt` の `PeerConnection.Observer` で扱っているが、ストリーム ID をトラック側から取得して上位へ伝える経路がない。

- `PeerChannel.kt` の `onAddStream` で `MediaStream` を受け取り、`listener?.onAddRemoteStream(ms)` でストリーム単位のまま上位に渡している。
- `onAddTrack(receiver: RtpReceiver?, ms: Array<out MediaStream>?)` と `onTrack(transceiver: RtpTransceiver)` は現状ログ出力のみで、トラックとストリーム ID の対応付けは行っていない。
- `SoraMediaChannel.kt` の `onAddRemoteStream(ms: MediaStream)` も `MediaStream` 単位で上位リスナーに通知しており、トラックからストリーム ID を引く API は公開していない。

参考として、他言語の SDK では受信側の `RtpReceiver` が持つ `stream_ids()` の先頭要素をトラックのストリーム ID として公開する実装がある。`org.webrtc.RtpReceiver` にも対応する API が存在するかを実装時に確認する。

## 設計方針

- リモートトラックに対してストリーム ID を引けるようにする。`onAddTrack` / `onTrack` で得られる `RtpReceiver` の `streamIds`（`stream_ids()` 相当）を利用してトラックとストリーム ID を対応付ける。
- 既存のストリームベース公開 API は維持したまま、トラックからストリーム ID を取得する手段を追加する形を基本とする。公開 API の形は実装時に検討する。
- `org.webrtc` 側で `RtpReceiver` から `streamIds` を取得できるかを確認し、取得できない場合の代替経路（`onAddStream` で得た `MediaStream` の `id` とトラックの対応付け）も検討する。

## 完了条件

- リモートの映像・音声トラックから、そのトラックが所属するストリーム ID を取得できること。
- 既存のストリームベース公開 API の動作が変わらないこと。

## 解決方法
