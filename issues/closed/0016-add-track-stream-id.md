# トラックからストリーム ID を取得できるようにする

- Priority: Medium
- Created: 2026-06-03
- Completed: 2026-06-18
- Polished: 2026-06-18
- Model: Opus 4.8
- Branch: feature/add-track-stream-id

## 目的

リモートの映像・音声トラックから、そのトラックが所属するストリーム ID （`stream_id`）を取得できるようにする。Sora ではリモートストリームのストリーム ID はリモート接続の `connection_id` と同一である。この機能により connection ID とトラックの対応付けが可能になる。

具体的なユースケース:

- マルチストリームでリモート映像にユーザー名を重畳する
- スポットライトでフォーカスされた接続の映像トラックを特定する

本 issue は `0024-refactor-deprecated-on-add-stream`（`onAddStream` 依存の解消）の前提となる。

## 現状

リモートトラックの受信は `PeerChannel.kt` の `PeerConnection.Observer` で扱っているが、ストリーム ID をトラック側から取得して上位へ伝える経路がない。

- `PeerChannel.kt:267-269`: `onAddStream` で `MediaStream` を受け取り、`listener?.onAddRemoteStream(ms)` でストリーム単位のまま渡している。
- `PeerChannel.kt:272-277`: `onAddTrack(receiver, ms: Array<out MediaStream>?)` — ログ出力のみ。`ms` 配列からストリーム ID が直接取得可能だが、未使用。
- `PeerChannel.kt:283-290`: `onTrack(transceiver)` — ログ出力のみ。`transceiver.receiver` から `RtpReceiver` を得られる。以下の TODO が残っている:
  ```
  // TODO(shino): Unified plan に onRemoveTrack が来たらこっちで対応する。
  // 今は SDP semantics に関わらず onAddStream/onRemoveStream でシグナリングに通知している
  ```
- `SoraMediaChannel.kt:880-887`: `onAddRemoteStream` も `MediaStream` 単位で上位リスナーに通知。マルチストリーム時に `ms.id == connectionId` の場合、自己ストリームとしてフィルタリングしている。

`PeerChannel.Listener` および `SoraMediaChannel.Listener` にはトラック単位のコールバックが存在せず、新規追加が必要である。

## 設計方針

### スコープ

本 issue の対象はリモートトラックのみとする。ローカルトラックのストリーム ID は `PeerChannelImpl.localStreamId` で既に管理されている。

### トラック→ストリーム ID 対応付け

`onTrack(transceiver)` の `transceiver.receiver` から `getStreams()` でストリーム ID を取得する。libwebrtc 側に `android_rtp_receiver_get_streams.patch` を適用することで `RtpReceiver.getStreams()` が利用可能になる。

`onAddTrack(receiver, ms)` の `ms[0].id` 経路は Plan B semantics でのみ有効であり、Unified Plan では `ms` が常に空となるため使用しない。

### データ構造

`PeerChannelImpl` 内に `private val trackToStreamId = ConcurrentHashMap<String, String>()` を持ち、キーをトラック ID、値をストリーム ID とする。スレッドセーフなデータ構造とし、`closeInternal()` でクリアする。

### 公開 API

以下の新規コールバックを `PeerChannel.Listener` に追加し、`SoraMediaChannel.Listener` にも同名で追加する:

```kotlin
// PeerChannel.Listener
fun onAddRemoteTrack(track: MediaStreamTrack, streamId: String)

// SoraMediaChannel.Listener
fun onAddRemoteTrack(mediaChannel: SoraMediaChannel, track: MediaStreamTrack, streamId: String)
```

既存の `onAddRemoteStream(ms: MediaStream)` コールバックは変更せず、併存させる。

### 自己ストリームフィルタリング

マルチストリーム時、`streamId == connectionId` の場合は自己ストリームとしてフィルタリングし、`onAddRemoteTrack` を発火しない。既存の `onAddRemoteStream` と同等の挙動をトラックレベルでも維持する。

### マッピングのライフサイクル

- **追加**: `onTrack` 発火時に `trackToStreamId[track.id()] = streamId` を設定する。
- **削除**: `onRemoveTrack` 発火時に `trackToStreamId.remove(track.id())` でマッピングを削除する。
- **ストリーム削除**: `onRemoveStream` 発火時にそのストリームに属する全トラックのマッピングを削除する。
- **切断時**: `closeInternal()` で `trackToStreamId.clear()` する。

## 完了条件

- リモートの映像・音声トラックからストリーム ID が取得できること。
- トラック削除時にマッピングがクリーンアップされること。
- 既存の `onAddRemoteStream` コールバックの動作が変わらないこと。
- 実機での動作確認テストが存在すること（マルチストリーム環境での検証を含む）。
- `CHANGES.md` の `develop` セクションに `[ADD]` エントリを追記すること。

## 解決方法

### libwebrtc 側の対応

`RtpReceiver.getStreams()` を追加する `android_rtp_receiver_get_streams.patch` を適用した libwebrtc 150.7871.2.1 を使用する。

### PeerChannel.kt

`PeerChannel.Listener` に `onAddRemoteTrack(track: MediaStreamTrack, streamId: String)` と `onRemoveRemoteTrack(trackId: String, streamId: String)` をデフォルト実装 `{}` 付きで追加した。

`PeerChannelImpl` に以下の変更を加えた:

- `trackToStreamId: ConcurrentHashMap<String, String>` を追加。
- `onTrack` で `transceiver.receiver.getStreams().firstOrNull()` からストリーム ID を取得し、`trackToStreamId[track.id()] = streamId` でマッピングに登録後、`listener?.onAddRemoteTrack(track, streamId)` を発火する。`closing` ガード、`receiver.track()` の null チェック、`getStreams()` 空時の早期 return を実装した。
- `onRemoveTrack` で `trackToStreamId.remove(trackId)` によりマッピングを削除し、`listener?.onRemoveRemoteTrack(trackId, streamId)` を発火する。`closing` ガード、`receiver.track()` の null チェックを実装した。`trackToStreamId` に対象エントリが存在しない場合は通知しない。
- `onRemoveStream` で `trackToStreamId` を走査し、該当ストリームに属する全トラックのマッピングを削除して `onRemoveRemoteTrack` を発火する。`closing` ガードを実装した。`ms.audioTracks` / `ms.videoTracks` による列挙は JNI 側で無効化済みの可能性があるため使用しない。
- `closeInternal()` で `trackToStreamId.clear()` を実行し、切断時にマッピングを解放する。
- `onConnectionChange(CONNECTED)` に `closing` ガードを追加した。
- `closing` に `@Volatile` を付与し、複数スレッド間の可視性を保証した。
- 古い `TODO(shino)` コメントを削除した。

### SoraMediaChannel.kt

`SoraMediaChannel.Listener` に `onAddRemoteTrack(mediaChannel: SoraMediaChannel, track: MediaStreamTrack, streamId: String)` と `onRemoveRemoteTrack(mediaChannel: SoraMediaChannel, trackId: String, streamId: String)` をデフォルト実装 `{}` 付きで追加した。

`peerListener` に `onAddRemoteTrack` / `onRemoveRemoteTrack` の実装を追加し、自己ストリームフィルタリング (`isSelfStreamId`) を適用した上で上位リスナーに通知するようにした。

`isSelfStreamId` メソッドを抽出し、`onAddRemoteStream` / `onRemoveRemoteStream` / `onAddRemoteTrack` / `onRemoveRemoteTrack` で共通利用するようにした。`onRemoveRemoteStream` の既存のインライン条件判定との一貫性のため、`multistreamEnabled != false` の判定を含む。

### CHANGES.md

`## develop` セクションに `[ADD]` エントリを追記し、libwebrtc バージョンを 150.7871.2.1 に更新した。
