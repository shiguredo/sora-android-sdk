# androidTest に onSignalingMessage で type: switched を受信する e2e テストを追加する

- Priority: High
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-signaling-switched-message

## 目的

DataChannel signaling 有効時に Sora から `switched` メッセージを受信し、`SoraMediaChannel.Listener.onSignalingMessage` で `type == "switched"` が通知されることを e2e で検証する。sora-js-sdk の `e2e-tests/tests/type_switched.test.ts` および `switched_callback.test.ts` に相当する。

## 優先度根拠

- `onSignalingMessage` は今サイクルで追加された新規 API であり、e2e レベルの回帰テストが存在しない。
- switched はシグナリング経路が WebSocket から DataChannel へ切り替わる要所であり、コールバック通知の回帰を検出できるようにする価値が高い。

## 現状

- 既存 e2e は `onSignalingMessage` を検証していない。
- DataChannel signaling (`dataChannelSignaling = true`) を用いた接続経路の e2e が存在しない。

## 設計方針

- `dataChannelSignaling = true` で接続する。
- `Listener.onSignalingMessage` で通知されるメッセージを収集し、`SoraSignalingTransportType.WEBSOCKET` で `type == "switched"` が通知されることを確認する。
- 併せて switched 後にシグナリング経路が DataChannel に切り替わっていること (以降のシグナリングが DataChannel 経由になること) を確認できると望ましい。

## 完了条件

- DataChannel signaling 有効で接続し、`onSignalingMessage` に `switched` が通知されることを検証する e2e テストが追加されていること。
- Gradle Managed Device (pixelApi35) で完走すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt`

## 依存関係

- なし (recvonly 接続で検証可能なため映像/音声ダミーは必須ではない)

## 解決方法
