# androidTest に onDataChannel の発火タイミングを検証する e2e テストを追加する

- Priority: Medium
- Created: 2026-08-06
- Completed:
- Model: DeepSeek V4 Flash
- Branch: feature/add-e2e-on-data-channel-fire-timing
- Polished:

## 目的

`SoraMediaChannel.Listener.onDataChannel` の発火タイミングを検証する e2e テストを追加する。発火タイミングは「サーバからの `type: switched` 受信時」から「メッセージング用ラベル（`#` で始まるラベル）の DataChannel がすべてクライアント側で OPEN になった時点」へ変更されたため、その挙動を実疎通で担保する。

## 優先度根拠

- `onDataChannel` の発火タイミングはユーザーが「DataChannel を利用できる」と判断する根拠であり、実疎通での検証が必要。
- 発火タイミングの誤りはメッセージング機能の利用開始タイミングの誤解を招くため、Medium とする。

## 現状

- `SoraMessagingE2ETest.kt` は `onDataChannel` コールバックを利用しているが、メッセージ送受信テストの前提条件として「待機」に使っているだけであり、発火タイミング自体を検証していない。
- issue 0051 の変更により、`onDataChannel` の発火タイミングが「`switched` 受信時」から「全メッセージング用ラベルがクライアント側で OPEN になった時点」に変更された。この挙動を検証するテストがない。

## 設計方針

- `SoraMessagingE2ETest.kt` に検証を追加する（新規テストクラスは作らず、既存の messaging テスト基盤を再利用する）。
- 検証項目:
  1. `type: switched` 受信時点では `onDataChannel` がまだ発火していないこと
  2. `switched` 受信後に `onDataChannel` が発火すること
  3. `onDataChannel` 発火時点で `sendDataChannelMessage()` が即座に成功すること（DataChannel が OPEN 済みのため、`NOT_READY` / `LABEL_NOT_FOUND` / `INVALID_STATE` のポーリングが不要であること）
- 既存の messaging テスト（2 チャネル間の送受信）は維持し、タイミング検証を別テストメソッドとして追加する。
- 接続先 Sora が `data_channel_signaling` 非対応の場合はスキップする（既存パターン踏襲）。

## 完了条件

- `switched` 受信時点では `onDataChannel` が発火していないこと、その後に発火すること、発火時点で `sendDataChannelMessage()` が成功することを検証する e2e テストが追加されていること。
- 接続先 Sora が `data_channel_signaling` 非対応の場合はテストがスキップされること。
- Gradle Managed Device (pixelApi35) で完走すること。
- `CHANGES.md` の `develop` セクションに該当エントリを追記すること（存在しない場合）。

## 解決方法
