# トラックからストリーム ID を取得できるようにする

- Priority: Medium
- Created: 2026-06-03
- Completed: 2026-06-12
- Polished: 2026-06-12
- Model: Opus 4.8
- Branch: feature/add-track-stream-id

## 目的

リモートの映像・音声トラックから、そのトラックが所属するストリーム ID（`stream_id`）を取得できるようにする。Sora ではリモートストリームのストリーム ID はリモート接続の `connection_id` と同一であり、この機能により connection ID とトラックの対応付けが可能になる。

マルチストリームでリモート映像にユーザー名を重畳する、スポットライトでフォーカスされた接続の映像トラックを特定するなどの用途に利用できる。

本 issue は `0024-refactor-deprecated-on-add-stream`（`onAddStream` 依存の解消）の前提となる。

## 優先度根拠

- マルチストリーム・スポットライトを利用するアプリケーションでは必須の機能だが、SDK のコア接続機能には影響しないため Medium とする
- issue 0024（`onAddStream` deprecated 対応）の前提であり、先に解決する必要がある

## 現状

リモートトラックの受信は `PeerChannel.kt` の `PeerConnection.Observer` で扱っているが、ストリーム ID をトラック側から取得して上位へ伝える経路がない。

- `PeerChannel.kt:267-269`: `onAddStream` で `MediaStream` を受け取る
- `PeerChannel.kt:272-277`: `onAddTrack(receiver, ms: Array<out MediaStream>?)` — ログ出力のみ。`ms` 配列からストリーム ID が取得可能だが未使用
- `PeerChannel.kt:283-290`: `onTrack(transceiver)` — ログ出力のみ。Unified Plan への移行を見据えた TODO コメントが残っている
- `SoraMediaChannel.kt:880-887`: `onAddRemoteStream` も `MediaStream` 単位で上位リスナーに通知。マルチストリーム時に `ms.id == connectionId` の場合は自己ストリームとしてフィルタリング

`PeerChannel.Listener`（`PeerChannel.kt:93-143`）および `SoraMediaChannel.Listener`（`SoraMediaChannel.kt:232-311`）にはトラック単位のコールバックが存在せず、新規追加が必要である。

## 設計方針

### スコープ

本 issue の対象はリモートトラックのみ。ローカルトラックのストリーム ID は `PeerChannelImpl.localStreamId` で既に管理されている。

### トラック → ストリーム ID 対応付け

`onAddTrack(receiver, ms)` の第 2 引数 `ms: Array<out MediaStream>?` の先頭要素 `ms[0]?.id` をストリーム ID として使用する。

- `ms` が null、空配列、または先頭要素が null の場合はそのトラックのマッピングを設定しない。また `ms[0]?.id` が空文字列の場合もマッピングを設定しない（`isNullOrEmpty()` でガード）
- `closeInternal` との競合を防ぐため、`onAddTrack` 内で `closing` フラグをチェックする
- 本 SDK は Unified Plan 固定（`PeerNetworkConfig.kt:36`）であり、Plan B の考慮は不要

### データ構造

`PeerChannelImpl` 内に以下の 2 つのマッピングを持つ:

- `private val trackToStreamId = ConcurrentHashMap<String, String>()`: キーをトラック ID、値をストリーム ID とする。`PeerChannelImpl` の内部状態としてのみ扱う
- `private val receiverToTrackId = ConcurrentHashMap<RtpReceiver, String>()`: `onRemoveTrack` で `receiver.track()` が null の場合に `RtpReceiver` から `trackId` を逆引きするために使用する

### 公開 API

以下の新規コールバックを `PeerChannel.Listener` に追加し、`SoraMediaChannel.Listener` にも同名で追加する。新規メソッドには空のデフォルト実装 `{}` を付与し、後方互換性を維持する。

`PeerChannel.Listener` への追加（`PeerChannel.kt:97` の直後）:

```kotlin
fun onAddRemoteTrack(
    track: MediaStreamTrack,
    streamId: String,
) {}
```

`SoraMediaChannel.Listener` への追加:

```kotlin
fun onAddRemoteTrack(
    mediaChannel: SoraMediaChannel,
    track: MediaStreamTrack,
    streamId: String,
) {}
```

既存の `onAddRemoteStream(ms: MediaStream)` コールバックは変更せず、併存させる。

### 自己ストリームフィルタリング

マルチストリーム時、`mediaOption.multistreamEnabled != false && connectionId != null && streamId == connectionId` の場合は自己ストリームとしてフィルタリングし、`SoraMediaChannel.Listener.onAddRemoteTrack` を発火しない。既存の `onAddRemoteStream`（`SoraMediaChannel.kt:882`）と同一の条件式で、トラックレベルでも同等の挙動を維持する。

フィルタリングは `SoraMediaChannel` 側の `peerListener` 実装で行い、`isSelfStreamId(id: String): Boolean` に抽出する。`PeerChannelImpl` は `connectionId` を持たないため、`PeerChannel.Listener.onAddRemoteTrack` ではフィルタリングせず常に発火する。

### マッピングのライフサイクル

- **追加**: `onAddTrack` 発火時に `ms` が non-null かつ non-empty かつ `ms[0]?.id` が non-null かつ非空文字列の場合、`trackToStreamId[track.id()] = streamId` と `receiverToTrackId[r] = track.id()` の両方を設定し、`listener?.onAddRemoteTrack(track, streamId)` を呼び出す。`closing` が true の場合、`receiver` が null の場合、`receiver.track()` が null の場合はマッピングもコールバックも行わない
- **トラック削除**: `onRemoveTrack` 発火時に `resolveTrackId(receiver)` で削除すべきトラック ID を解決する。`receiver.track()` が non-null ならその ID を返し、null の場合は `receiverToTrackId` から逆引きする。解決できた trackId で `trackToStreamId.remove(trackId)` を実行する。最後に `receiverToTrackId.remove(receiver)` で receiver 側のエントリも削除する
- **ストリーム削除**: `onRemoveStream` 発火時は `trackToStreamId` の全エントリを走査し、`value == ms.id` のエントリを収集した後 `remove(key)` で個別に削除する。対応する `receiverToTrackId` のエントリも削除する。`ms.audioTracks` / `ms.videoTracks` による列挙は JNI 側ですでに無効化されている可能性があるため使用しない
- **切断時**: `closeInternal()` で `trackToStreamId.clear()` と `receiverToTrackId.clear()` を行う。クリアは `listener = null` より前、かつ `conn?.dispose()` より前に行い、dispose 中のコールバックからのアクセス競合を防ぐ。`closeInternal()` は明示的 `disconnect()` と `FAILED` / `CLOSED` 遷移時のみ呼ばれる
- **再接続**: `DISCONNECTED` 遷移（`PeerChannel.kt:372-377`）ではクリアしない。`DISCONNECTED` は一時的な不安定状態であり、現行実装でも切断処理を行わず `onWarning` 通知のみである。接続復帰後に既存トラックのマッピングを維持する必要がある

## 完了条件

- リモートの映像・音声トラックからストリーム ID が取得できること
- トラック削除時に `receiver.track()` が null でも `receiverToTrackId` 経由でマッピングがクリーンアップされること
- ストリーム削除時にマッピングがクリーンアップされること
- 既存の `onAddRemoteStream` コールバックの動作が変わらないこと
- 既存の `Listener` 実装がコンパイルエラーにならないこと（デフォルト実装 `{}` 付きのため）
- 動作確認テストが存在すること（エミュレーター可、マルチストリーム環境での検証を含む）
- `CHANGES.md` の `develop` セクションにエントリを追記すること

## 変更対象ファイル

- `PeerChannel.kt` — `Listener` インターフェースに `onAddRemoteTrack` 追加、`trackToStreamId` / `receiverToTrackId` マッピング追加、`onAddTrack` / `onRemoveTrack` / `onRemoveStream` / `closeInternal` 内でマッピング操作とコールバック発火を実装。`resolveTrackId()` を抽出し `onTrack` の古い TODO コメントを削除
- `SoraMediaChannel.kt` — `Listener` インターフェースに `onAddRemoteTrack` 追加、`peerListener` に実装追加。`isSelfStreamId()` に抽出し `onAddRemoteStream` / `onAddRemoteTrack` で共通利用
- `CHANGES.md` — `[ADD]` エントリ追記

## 解決方法

### PeerChannel.kt

`PeerChannel.Listener` に `onAddRemoteTrack(track: MediaStreamTrack, streamId: String)` をデフォルト実装 `{}` 付きで追加した。

`PeerChannelImpl` に以下の変更を加えた:

- `trackToStreamId` (`ConcurrentHashMap<String, String>`) と `receiverToTrackId` (`ConcurrentHashMap<RtpReceiver, String>`) を追加
- `onAddTrack` 内で `ms[0]?.id` からストリーム ID を取得し、マッピングに登録して `listener?.onAddRemoteTrack` を発火する処理を実装した。`closing` ガード、`ms[0]` の null 安全、空文字列ガード (`isNullOrEmpty`) を導入した
- `onRemoveTrack` を `resolveTrackId(receiver)` に抽出し、本体を簡略化した。`receiver.track()` が null の場合は `receiverToTrackId` から逆引きする
- `onRemoveStream` で `trackToStreamId` を走査し、該当ストリームのエントリと対応する `receiverToTrackId` エントリを `remove(key)` で削除する処理を実装した（`removeIf` からの変更）
- `closeInternal` で `listener = null` / `conn?.dispose()` より前に `clear()` を実行し、競合アクセスを防止した
- `onTrack` の古い TODO コメントを削除した

### SoraMediaChannel.kt

`SoraMediaChannel.Listener` に `onAddRemoteTrack(mediaChannel: SoraMediaChannel, track: MediaStreamTrack, streamId: String)` をデフォルト実装 `{}` 付きで追加した。

`peerListener` に `onAddRemoteTrack` の実装を追加し、自己ストリームフィルタリング (`isSelfStreamId`) を適用した上で上位リスナーに通知するようにした。`isSelfStreamId` は `onAddRemoteStream` とも共通化した。

### CHANGES.md

`[ADD]` エントリを `## develop` セクションに追記した。
