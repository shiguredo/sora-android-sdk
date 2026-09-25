# onAddRemoteTrack / onRemoveRemoteTrack を検証する E2E テストを追加する

- Priority: Medium
- Created: 2026-09-25
- Completed: {YYYY-MM-DD}
- Branch: feature/add-e2e-remote-track-callbacks
- Polished: {YYYY-MM-DD}

## 目的

`SoraMediaChannel.Listener.onAddRemoteTrack` / `onRemoveRemoteTrack` の通知経路が壊れても既存テストでは検出できないため、E2E で検証できるようにする。2026.3.0 では `onTrack` と独自パッチの `RtpReceiver.getStreams()` から `onAddTrack` の `MediaStream` 引数へストリーム ID の取得元を移行しており、この経路の回帰を検出したい。

## 現状

- `SoraE2ETestBase.createChannel` は `onConnect` / `onClose` / `onError` / `onSignalingMessage` / `onDataChannel` 系のコールバックしか受け取らず、`onAddRemoteTrack` / `onRemoveRemoteTrack` をテストから観測できない。
- `sora-android-sdk/src/test` と `sora-android-sdk/src/androidTest` を検索しても `onAddRemoteTrack` / `onRemoveRemoteTrack` への参照がない。
- 通知処理は `PeerChannelImpl` の `onAddTrack` で `trackToStreamId` に登録し、`onRemoveTrack` / `onRemoveStream` で削除通知を出す。`SoraMediaChannel` 側では `isSelfStreamId` で自身のストリームを除外する。
- 既存の E2E はフレーム生成や stats を検証しており、受信トラックの通知がなくても成功する。

## 設計方針

- `SoraE2ETestBase.createChannel` に `onAddRemoteTrack` / `onRemoveRemoteTrack` の optional lambda を追加し、リスナーから委譲する。
- 2 チャネル構成の既存テストと同様の構成で、受信側が相手の connection id と一致する streamId で `onAddRemoteTrack` を受け取ること、送信側の切断で `onRemoveRemoteTrack` が同じ trackId / streamId で 1 回だけ発火することを assert する。
- 自身のストリームが通知されないこと (`isSelfStreamId` のフィルタ) も確認する。
- `AGENTS.md` の「モックやスタブは絶対に利用しないこと」に従い、実 Sora 接続の E2E で検証する。

## 完了条件

- 当該 E2E テストが CI (pixelApi35 の Gradle Managed Device) で安定して成功すること。
- 通知経路が壊れた場合にテストが失敗すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETestBase.kt`
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraSpotlightE2ETest.kt` など受信を伴うテスト
- `CHANGES.md`

## 解決方法
