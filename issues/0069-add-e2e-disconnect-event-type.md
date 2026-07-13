# androidTest に切断時の event type / reason を検証する e2e テストを追加する

- Priority: Medium
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-disconnect-event-type

## 目的

切断時に `SoraCloseEvent` と `SoraDisconnectReason` が期待どおりに決定されることを e2e で検証する。sora-js-sdk の `e2e-tests/tests/disconnect_event_type.test.ts` に相当し、特に switched 後の切断で timeout サブ経路を踏んだ場合の event type / reason を確認する。

## 優先度根拠

- 切断理由の分類はアプリ側のエラーハンドリングに直結し、回帰するとユーザー影響が大きい。
- 正常系疎通 (0065) の次に確認すべき挙動として Medium とする。

## 現状

- 既存 e2e は正常切断で `onClose` が呼ばれることのみを確認しており、event type / reason の妥当性を検証していない。
- 異常切断 (timeout など) の経路が e2e で網羅されていない。

## 設計方針

- 正常切断 (アプリからの `disconnect()`) と、switched 後に timeout 経路を踏む異常切断の双方を検証する。
- `onClose` の `SoraCloseEvent` (code / reason) と、`onError` / `onDisconnect` 相当で得られる `SoraDisconnectReason` が期待値になることを確認する。
- 異常系の再現方法 (WebSocket 切断のシミュレート方法) は実装時に確定する。ネットワーク遮断が難しい場合は、Sora 側の挙動に依存しない範囲で検証可能なケースに絞る。

## 完了条件

- 正常切断で期待どおりの close event が得られることを検証する e2e テストが追加されていること。
- 可能なら異常切断で `SoraDisconnectReason` が abend 相当になることを検証すること (再現が困難な場合はその旨を issue に記録し、正常系のみで完了とする)。
- Gradle Managed Device (pixelApi35) で完走すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt`

## 依存関係

- issue 0067 (switched 後の経路を扱うため)

## 解決方法
