# deprecated な onAddStream への対応を行う

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch: feature/refactor-deprecated-on-add-stream

## 目的

libwebrtc で deprecated となっている `PeerConnection.Observer#onAddStream` / `onRemoveStream` への依存を解消し、`onTrack` ベースのリモートストリーム通知へ移行する。

`onAddStream` / `onRemoveStream` は Plan B 由来の API であり、Unified Plan では deprecated 扱いとなっている。将来的に libwebrtc から削除される可能性があるため、`onTrack` / `onRemoveTrack` ベースへ移行しておく。

## 優先度根拠

- 現状では `onAddStream` / `onRemoveStream` も動作しており、即座の不具合はない。
- 一方で deprecated な API への依存であり、将来の libwebrtc 更新で削除されると動作しなくなるリスクがある。
- 緊急性は低く、移行には設計検討が必要なため Low とする。

## 現状

`PeerChannel.kt` の `PeerConnection.Observer` 実装でリモートストリームの追加・削除を `onAddStream` / `onRemoveStream` で扱っている。

```kotlin
// PeerChannel.kt（抜粋）
override fun onAddStream(ms: MediaStream?) {
    SoraLogger.d(TAG, "[rtc] @onAddStream msid=${ms?.id}")
    ms?.let { listener?.onAddRemoteStream(ms) }
}

override fun onAddTrack(
    receiver: RtpReceiver?,
    ms: Array<out MediaStream>?,
) {
    SoraLogger.d(TAG, "[rtc] @onAddTrack")
}

override fun onTrack(transceiver: RtpTransceiver) {
    SoraLogger.d(TAG, "[rtc] @onTrack direction=${transceiver.direction}")
    // ...
    // TODO: Unified plan に onRemoveTrack が来たらこっちで対応する。
    // 今は SDP semantics に関わらず onAddStream/onRemoveStream でシグナリングに通知している
}
```

- `onAddStream` は `listener?.onAddRemoteStream(ms)` を呼び、`onRemoveStream` は `listener?.onRemoveRemoteStream(it.id)` を呼んでいる。
- `onAddRemoteStream` / `onRemoveRemoteStream` は `SoraMediaChannel.kt` を経由して `SoraMediaChannel.Listener` の同名コールバックへ通知される（公開 API）。
- 一方 `onTrack` / `onAddTrack` / `onRemoveTrack` はログ出力のみで、ストリーム通知には使われていない。`onTrack` 内のコメントにも `onAddStream/onRemoveStream` で通知している旨が記載されている。

## 設計方針

- `onTrack` / `onRemoveTrack` ベースへ移行し、リモートストリームの追加・削除通知を組み立てる。
- 移行時の課題を整理する。
  - `onTrack` / `onRemoveTrack` から stream ID（msid）が引けるかを確認する（公開 API の `onAddRemoteStream` / `onRemoveRemoteStream` は `MediaStream` / ラベルを渡しているため、互換のため stream 単位の情報が必要）。
  - ネイティブ（mobile）では stream を構成する track が揃ったタイミングの判定が難しい。audio・video が別々の `onTrack` で上がる場合に、どのタイミングで `onAddRemoteStream` を通知するかを決める。
- 公開 API の `SoraMediaChannel.Listener#onAddRemoteStream` / `onRemoveRemoteStream` のシグネチャ互換性を維持できるかを確認し、互換が崩れる場合は後方互換のない変更として扱う（その場合はブランチ prefix を `feature/change-` に変更する）。

## 完了条件

- `onAddStream` / `onRemoveStream` への依存を解消し、`onTrack` / `onRemoveTrack` ベースでリモートストリームの追加・削除通知を行えること。
- 既存のリモートストリーム受信動作（音声・映像の受信）が変わらないこと。
- 公開 API への影響有無を明確にし、`CHANGES.md` の `develop` セクションに該当する種別のエントリを追記すること。

## 解決方法
