# トラックからストリーム ID を取得できるようにする

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
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

`onTrack(transceiver)` で受け取った `RtpTransceiver` から `transceiver.receiver.getStreams()` でストリーム ID を取得する。この API を追加するために webrtc-build の `android_rtp_receiver_get_streams.patch` が必要。

`android_rtp_receiver_get_streams.patch` による変更内容:

| ファイル | 内容 |
|----------|------|
| `sdk/android/api/org/webrtc/RtpReceiver.java` | `getStreams()` メソッド追加、`import java.util.List` 追加、`nativeGetStreams()` native 宣言追加 |
| `sdk/android/src/jni/pc/rtp_receiver.cc` | `JNI_RtpReceiver_GetStreams()` JNI 実装追加。`RtpReceiverInterface::stream_ids()` を呼び Java の `List<String>` に変換 |

このパッチは独立した新規パッチであり、既存パッチとの競合はない。`run.py` の `android` / `android_sdk` ターゲットに登録して適用する。

> **Note**: `onAddTrack(receiver, ms)` の `ms[0].id` 経路は Plan B SDP semantics でのみ有効であり、Unified Plan では `ms` が常に空となるため使用不可。本 issue では `onTrack + getStreams()` のみを経路とする。

### SDK 側の実装

#### ストリーム ID 取得

`onTrack(transceiver)` コールバック内で `transceiver.receiver.getStreams()` を呼び出してストリーム ID を取得する。

```kotlin
override fun onTrack(transceiver: RtpTransceiver) {
    val track = transceiver.receiver.track() ?: return
    val streamIds = transceiver.receiver.getStreams()
    val streamId = streamIds.firstOrNull() ?: return
    // ...
}
```

#### 変更対象ファイル

- **`PeerChannel.kt`**
  - `Listener` に `onAddRemoteTrack(track, streamId)` を追加
  - `PeerChannelImpl` に `trackToStreamId: ConcurrentHashMap<String, String>` を追加
  - `onTrack` ハンドラーを実装: `transceiver.receiver.getStreams()` から streamId を取得、`trackToStreamId` へ登録、`listener?.onAddRemoteTrack()` を発火
  - `onRemoveTrack` で `trackToStreamId.remove(track.id())`
  - `onRemoveStream` でストリームに属する全トラックのマッピングを削除
  - `closeInternal()` で `trackToStreamId.clear()`

- **`SoraMediaChannel.kt`**
  - `Listener` に `onAddRemoteTrack(mediaChannel, track, streamId)` を追加
  - `PeerChannel.Listener` 実装に `onAddRemoteTrack` ハンドラーを追加: 自己ストリームフィルタリング (`streamId == connectionId` かつ multistreamEnabled) 後、`listener?.onAddRemoteTrack()` を発火

- **`CHANGES.md`**
  - `develop` セクションに `[ADD]` エントリを追記

#### 自己ストリームフィルタリング

```kotlin
override fun onAddRemoteTrack(mediaChannel: SoraMediaChannel, track: MediaStreamTrack, streamId: String) {
    if (mediaOption.multistreamEnabled != false && connectionId != null && streamId == connectionId) {
        SoraLogger.d(TAG, "[channel:$role] this stream is mine, ignore: $streamId")
        return
    }
    listener?.onAddRemoteTrack(this@SoraMediaChannel, track, streamId)
}
```

既存の `onAddRemoteStream` と同等のロジックをトラックレベルで再実装する。

#### マッピングのライフサイクル（再掲）

- **追加**: `onTrack` 発火時に `trackToStreamId[track.id()] = streamId` を設定する。
- **削除**: `onRemoveTrack` 発火時に `trackToStreamId.remove(track.id())` でマッピングを削除する。
- **ストリーム削除**: `onRemoveStream` 発火時にそのストリームに属する全トラックのマッピングを削除する。
- **切断時**: `closeInternal()` で `trackToStreamId.clear()` する。
