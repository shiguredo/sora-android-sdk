# onAddRemoteTrack / onRemoveRemoteTrack を検証する E2E テストを追加する

- Priority: Medium
- Created: 2026-09-25
- Completed: {YYYY-MM-DD}
- Branch: feature/add-e2e-remote-track-callbacks
- Polished: 2026-09-25

## 目的

`SoraMediaChannel.Listener.onAddRemoteTrack` / `onRemoveRemoteTrack` の通知経路が壊れても既存テストでは検出できないため、E2E で検証できるようにする。`develop` (libwebrtc を 151.7922.0.0 へ上げた更新、2026.3.0 リリース後の変更で未リリース) では `onTrack` と独自パッチの `RtpReceiver.getStreams()` から `onAddTrack` の `MediaStream` 引数へストリーム ID の取得元を移行しており、この経路の回帰を検出したい。

## 現状

- `SoraE2ETestBase.createChannel` は `onConnect` / `onClose` / `onError` / `onSignalingMessage` / `onDataChannel` 系のコールバックしか受け取らず、`onAddRemoteTrack` / `onRemoveRemoteTrack` をテストから観測できない。
- `sora-android-sdk/src/test` と `sora-android-sdk/src/androidTest` を検索しても `onAddRemoteTrack` / `onRemoveRemoteTrack` への参照がない。
- 通知処理は `PeerChannelImpl` の `onAddTrack` で `trackToStreamId` に登録し、`onRemoveTrack` / `onRemoveStream` で削除通知を出す。`SoraMediaChannel` 側では `isSelfStreamId` で自身のストリームを除外する。
- 既存の E2E はフレーム生成や stats を検証しており、受信トラックの通知がなくても成功する。

## 設計方針

- `SoraE2ETestBase.createChannel` に `onAddRemoteTrack` / `onRemoveRemoteTrack` の optional lambda を追加し、リスナーから委譲する。
- 2 チャネル構成の既存テストと同様の構成で、受信側が相手の connection id と一致する streamId で `onAddRemoteTrack` を受け取ること、送信側の切断で `onRemoveRemoteTrack` が同じ trackId / streamId で 1 回だけ発火することを assert する。受信側に `enableMultistream()` は不要。Sora は connect の `multistream` 未指定でもマルチストリームで動作し、リモートストリームの ID は配信側の connection id と一致する (2023 年 6 月リリースの Sora で `default_multistream` が廃止され、未指定時は常にマルチストリームになる)。
- `isSelfStreamId` による自身のストリームの除外は、受信側 (recvonly) には自分自身のストリームが Sora から届かないため本構成では検証できない。そのため本 issue では検証対象としない。なお、フィルタが誤って常に通知を抑止する形の回帰は `onAddRemoteTrack` が一度も発火せず本テストがタイムアウトすることで検出される。
- `AGENTS.md` の「モックやスタブは絶対に利用しないこと」に従い、実 Sora 接続の E2E で検証する。

## 完了条件

- 当該 E2E テストが CI (pixelApi35 の Gradle Managed Device) で安定して成功すること。
- 通知経路が壊れた場合にテストが失敗すること。
- 既存の E2E テストが引き続き成功すること (createChannel へのコールバック追加はデフォルト引数のため既存呼び出しに影響しない)。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraRemoteTrackE2ETest.kt` (新規)
  - sendonly + recvonly の 2 チャネル構成は `SoraSpotlightE2ETest` を雛形とする
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETestBase.kt`
- `CHANGES.md`

## 解決方法
