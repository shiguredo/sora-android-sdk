# PeerChannel と SoraMediaChannel の KDoc とコメントを実装に合わせて整備する

- Priority: Low
- Created: 2026-09-25
- Completed: {YYYY-MM-DD}
- Branch: feature/refactor-peer-channel-doc-comments
- Polished: {YYYY-MM-DD}

## 目的

`PeerChannelImpl` のリモートトラック通知周りについて、KDoc・コメント・ログが実装と一致していない箇所を整備し、後から挙動を追いやすくする。機能の挙動は変えない。

## 現状

- `PeerChannel.Listener.onRemoveRemoteTrack` と `SoraMediaChannel.Listener.onRemoveRemoteTrack` の KDoc は `PeerConnection.Observer.onRemoveTrack` のみを発火元として説明しているが、実際は `onRemoveStream` からも発火する。
- `closing` ガード時、`onAddTrack` / `onRemoveTrack` / `onRemoveStream` は無言で `return` するが、`onTrack` だけは `ignored because closing=true` をログ出力する。切断時の追跡性が揃っていない。
- `onAddTrack` の `ms.firstOrNull()` を採用する根拠 (Sora は 1 ストリーム 1 トラックを前提とするため先頭を採用する) がコメントから失われている。
- `onAddTrack` は upstream では Plan B との後方互換用の API であり、Unified Plan では `OnTrack` が推奨である。Java API に代替がないため現状の実装を維持するが、将来の libwebrtc 更新時の判断材料としてコメントに残す。
- `onTrack` は通知処理がなくなりログ専用になっている。direction / sender / receiver のログに加えて `closing` 判定ログがある。

## 設計方針

- 挙動は変更せず、KDoc とコメントを実装に合わせる。
- `closing` ガードのログは、通知を抑止したことを追えるよう対象箇所で方針を揃える。
- `onTrack` のログは必要な情報に絞る。

## 完了条件

- `onRemoveRemoteTrack` の発火経路が KDoc から読み取れること。
- `onAddTrack` の `firstOrNull()` 採用理由と `onAddTrack` の位置づけがコメントから読み取れること。
- `closing` ガードのログ方針が揃っていること。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannel.kt`
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/SoraMediaChannel.kt`
- `CHANGES.md`

## 解決方法
