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

## 完了条件

- `onAddStream` / `onRemoveStream` への依存を解消し、`onTrack` / `onRemoveTrack` ベースでリモートストリームの追加・削除通知を行えること。
- 既存のリモートストリーム受信動作が変わらないこと。実機での動作確認テストを含むこと。
- 公開 API への影響有無を明確にし、`CHANGES.md` の `develop` セクションに該当する種別のエントリを追記すること。

## 解決方法
