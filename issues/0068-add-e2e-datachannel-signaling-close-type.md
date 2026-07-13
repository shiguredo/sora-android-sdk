# androidTest に DataChannel signaling only での切断 close type を検証する e2e テストを追加する

- Priority: Medium
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-datachannel-signaling-close-type

## 目的

DataChannel signaling only (WebSocket 切断を無視する構成) で切断したとき、切断経路が DataChannel であることを e2e で検証する。sora-js-sdk の `e2e-tests/tests/type_close.test.ts` に相当する。

## 優先度根拠

- `dataChannelSignaling` と `ignoreDisconnectWebSocket` の組み合わせは切断挙動が通常と異なり、回帰しやすい経路であるため e2e で担保する価値がある。
- ただし基礎疎通 (0065) や新規 API (0067) より優先度は下がるため Medium とする。

## 現状

- 既存 e2e は WebSocket 経由の切断のみを扱い、DataChannel signaling only での切断経路を検証していない。

## 設計方針

- `dataChannelSignaling = true` かつ `ignoreDisconnectWebSocket = true` で接続する。
- switched 後に `disconnect()` を呼び、`SoraCloseEvent` および切断経路が DataChannel であることを確認する。
- 切断経路種別は `onSignalingMessage` の `SoraSignalingTransportType` (DataChannel 経由の `disconnect` 送信) や close イベントの内容から判定する。

## 完了条件

- DataChannel signaling only の構成で切断し、切断が DataChannel 経由で行われることを検証する e2e テストが追加されていること。
- Gradle Managed Device (pixelApi35) で完走すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt`

## 依存関係

- issue 0067 (onSignalingMessage / switched の検証基盤があると実装しやすい)

## 解決方法
