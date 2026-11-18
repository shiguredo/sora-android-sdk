# onAddRemoteTrack / onRemoveRemoteTrack を検証する E2E テストを追加する

- Priority: Medium
- Created: 2026-09-25
- Completed: 2026-11-18
- Branch: feature/add-e2e-remote-track-callbacks
- Polished: 2026-11-18
- Refreshed: 2026-11-18

## 目的

`SoraMediaChannel.Listener.onAddRemoteTrack` / `onRemoveRemoteTrack` の通知経路が壊れても既存テストでは検出できないため、E2E で検証できるようにする。2026.4.0 (2026-10-23 リリース、libwebrtc 156.8078.5.1) には、libwebrtc 151 以降 `RtpReceiver.getStreams()` が利用できなくなったことに伴い、ストリーム ID の取得元を `onTrack` から `onAddTrack` の `MediaStream` 引数へ移行した変更が含まれており (`CHANGES.md` の 2026.4.0 の libwebrtc 更新の項目)、この経路の回帰を検出したい。

## 現状

- `SoraE2ETestBase.createChannel` は `onConnect` / `onClose` / `onError` / `onSignalingMessage` / `onDataChannel` 系のコールバックしか受け取らず、`onAddRemoteTrack` / `onRemoveRemoteTrack` をテストから観測できない。
- `sora-android-sdk/src/test` と `sora-android-sdk/src/androidTest` を検索しても `onAddRemoteTrack` / `onRemoveRemoteTrack` への参照がない。
- 通知処理は `PeerChannelImpl` の `onAddTrack` で `trackToStreamId` に登録し、`onRemoveTrack` / `onRemoveStream` で削除通知を出す。`SoraMediaChannel` 側では `isSelfStreamId` で自身のストリームを除外する。
- 既存の E2E の一部はフレーム生成や stats を検証しているが、受信トラックの通知を観測しているテストはなく、通知がなくても成功する。

## 設計方針

- `SoraE2ETestBase.createChannel` に `onAddRemoteTrack` / `onRemoveRemoteTrack` の optional lambda を追加し、リスナーから委譲する。
- テストを実装したら、SDK 側の修正に入る前に、現行実装で `onAddRemoteTrack` / `onRemoveRemoteTrack` が実際に発火するかを GMD 上で確認する。特に `onAddTrack` の `MediaStream` 引数から streamId を取得できることと、削除時に `receiver.track()?.id()` が dispose 済みトラックで例外にならないことを確認する。発火しない場合は SDK 側の不具合として扱い、テストの skip や期待値の緩和で回避せず、`PeerChannel.kt` / `SoraMediaChannel.kt` も変更対象に加えて原因を修正する。
- 2 チャネル構成の既存テストと同様の構成で、送信側は映像のみを有効にし (`enableVideoUpstream`)、受信側は `enableVideoDownstream` のみを有効にする。音声は送信・受信とも無効とする。期待値は映像トラック 1 件につき `onAddRemoteTrack` と `onRemoveRemoteTrack` が各 1 回である。
- 受信側が相手の connection id と一致する streamId で `onAddRemoteTrack` を受け取ること、送信側の切断で `onRemoveRemoteTrack` が同じ trackId / streamId で 1 回だけ発火することを assert する。コールバックでは受信した trackId / streamId をスレッドセーフなコレクションや `CompletableDeferred` に記録するだけにし、assert はテストスレッドで行う。通知が来ない場合は `assumeTrue` で skip せず、タイムアウトをテスト失敗として扱う。
- 受信側に `enableMultistream()` は不要。Sora は connect の `multistream` 未指定でもマルチストリームで動作し、リモートストリームの ID は配信側の connection id と一致する (2023 年 6 月リリースの Sora で `default_multistream` が廃止され、未指定時は常にマルチストリームになる)。
- `SoraSpotlightE2ETest` から流用するのは 2 チャネルの生成・接続順・待機・`finally` の切断のみとし、spotlight 固有の設定 (`enableSpotlight` / `softwareVideoEncoderOnly` / VP8 / stats ポーリング) は流用しない。channelId は基底クラスの値を使う。
- `isSelfStreamId` による自身のストリームの除外は、受信側 (recvonly) には自分自身のストリームが Sora から届かないため本構成では検証できない。そのため本 issue では検証対象としない。なお、フィルタが誤って常に通知を抑止する形の回帰は `onAddRemoteTrack` が一度も発火せず本テストがタイムアウトすることで検出される。
- `AGENTS.md` の「モックやスタブは絶対に利用しないこと」に従い、実 Sora 接続の E2E で検証する。

## 完了条件

- 当該 E2E テストが CI (pixelApi35 の Gradle Managed Device) で安定して成功すること。
- 通知経路が壊れた場合にテストが失敗すること。通知が来ない場合に `assumeTrue` で skip せず、タイムアウトを失敗として扱うこと。
- 既存の E2E テストが引き続き成功すること (createChannel へのコールバック追加はデフォルト引数のため既存呼び出しに影響しない)。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraRemoteTrackE2ETest.kt` (新規)
  - sendonly + recvonly の 2 チャネル構成は `SoraSpotlightE2ETest` を雛形とする
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETestBase.kt`
- `CHANGES.md`
- 現行実装で通知が発火しない場合は `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannel.kt` と `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/SoraMediaChannel.kt`

## 解決方法

- `SoraE2ETestBase.createChannel` に `onAddRemoteTrack` / `onRemoveRemoteTrack` の optional lambda を追加し、`SoraMediaChannel.Listener` から委譲するようにした。
- `SoraRemoteTrackE2ETest` を追加し、sendonly（映像のみ）+ recvonly（映像のみ）の 2 チャネル構成で、テスト専用の channelId を使って検証するようにした。
- 受信側が送信側の `connectionId` と一致する `streamId` で `onAddRemoteTrack` を 1 回受け取り、送信側の切断で同じ trackId / streamId の `onRemoveRemoteTrack` を 1 回受け取ることを assert する。通知が来ない場合は skip せずにテストを失敗させる。
- CI (pixelApi35 の Gradle Managed Device) の E2E で成功した (https://github.com/shiguredo/sora-android-sdk/actions/runs/44228079890)。
- `CHANGES.md` の `## develop` に `### misc` のエントリを追加した。
- なお、`onRemoveTrack` 単独経路の回帰は `onRemoveStream` のフォールバックで隠れるため、本 E2E では区別しない。経路を分離する単体テストは別途必要。
