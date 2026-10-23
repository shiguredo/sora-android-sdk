# onDataChannel 発火直後の sendDataChannelMessage が NOT_READY になる問題を修正する

- Created: 2026-09-10
- Completed: 2026-09-10
- Branch: feature/fix-messaging-not-ready-race
- Polished: 2026-09-10

## 目的

`SoraMediaChannel.Listener.onDataChannel` が発火した直後の `SoraMediaChannel.sendDataChannelMessage` が `SoraMessagingError.NOT_READY` を返すことがある race を解消する。

`onDataChannel` は「クライアント側でメッセージング用 DataChannel がすべて OPEN になり、送受信可能になった時点」の通知として定義されている。発火直後の送信が `NOT_READY` になる状態は、この仕様と矛盾している。

## 現状

- `SoraMediaChannel.sendDataChannelMessage` は冒頭で `switchedToDataChannel` を確認し、`false` の場合は `SoraMessagingError.NOT_READY` を返す。
- `switchedToDataChannel` は `SoraMediaChannel.handleSwitched()` で `true` になる。`handleSwitched` はシグナリングの `type: switched` を受信した時に呼ばれる。
- 一方 `onDataChannel` は 0051 の変更で「クライアント側でメッセージング用 DataChannel がすべて OPEN になった時」に発火するようになった。0051 の検証記録にも `onDataChannel` が `@signaling:onSwitched` より先に発火することが記載されている。
- このため利用者が `onDataChannel` コールバック内で `sendDataChannelMessage` を呼ぶと、`switchedToDataChannel` がまだ `false` で `NOT_READY` になることがある。
- E2E テスト (`SoraMessagingE2ETest`) の失敗は、これとは別の順序にも起因する。`SignalingChannel.onMessage` は `notifyReceivedSignalingMessage` を `onSwitchedMessage` より先に呼ぶ。`onSignalingMessage` は `switched` も通知対象に含むため、テストが `onSignalingMessage` で `switched` を観測してから送信しても、`handleSwitched` による `switchedToDataChannel = true` がまだ完了していないことがある。
- 0078 には「`NOT_READY` は `switchedToDataChannel` フラグで決まるため、`switched` 受信を待ってから送信することで発生しない」と記載されているが、上記の順序のため実際には発生し得る。
- CI で実際に `java.lang.AssertionError: onDataChannel 発火後の最初の送信が成功すること expected:<OK> but was:<NOT_READY>` を確認している。同一コードでも実行によって成功・失敗が分かれる flaky な状態である。

## 設計方針

メッセージング用 DataChannel の送信可否を、DataChannel シグナリングの切替 (`switchedToDataChannel`) ではなく、メッセージング用 DataChannel の準備完了で判定する。これにより、利用者が `onDataChannel` コールバック内で送信する場合の race と、`onSignalingMessage` で `switched` を観測した直後に送信する場合の race の両方を解消する。以下の 2 案を比較する。

- 案 A: `sendDataChannelMessage` から `switchedToDataChannel` のゲートを外し、対象ラベルの `DataChannel.state() == OPEN` で判定する。未 OPEN 時は `NOT_READY` ではなく `LABEL_NOT_FOUND` / `INVALID_STATE` を返すようになる。
- 案 B: `onDataChannel` の発火条件と同じ「メッセージング用 DataChannel がすべて OPEN」を表す状態で判定する。`NOT_READY` の意味を「メッセージング用 DataChannel がまだ準備できていない」に統一でき、既存の `SoraMessagingError` の意味を保てる。

`onDataChannel` の仕様と `NOT_READY` の意味を維持できるため、案 B を推奨する。

## 完了条件

- `onDataChannel` 発火直後の `sendDataChannelMessage` が安定して `SoraMessagingError.OK` を返すこと。
- `SoraMessagingE2ETest` の該当テストがタイミングに依存せず成功すること。
- メッセージング用 DataChannel が未準備の場合に `NOT_READY` を返す意味が変わらないこと (案 B を採用した場合)。
- `CHANGES.md` の `develop` セクションにエントリを追記すること。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/SoraMediaChannel.kt` の `sendDataChannelMessage`
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/SoraMediaChannel.kt` の `maybeNotifyDataChannelAvailable` (案 B を採用する場合)

## 解決方法

### 実装

- `SoraMediaChannel.sendDataChannelMessage` の送信可否判定を `switchedToDataChannel` から `onDataChannelNotified` に変更した
  - `onDataChannelNotified` は `maybeNotifyDataChannelAvailable()` で全メッセージング用 DataChannel が OPEN になった時点で `true` になる
  - `listener?.onDataChannel(...)` の直前に設定されるため、`onDataChannel` コールバック内からの送信でも `NOT_READY` にならない
  - `onDataChannelNotified` に `@Volatile` を付与し、libwebrtc のシグナリングスレッドでの更新をアプリ側スレッドから参照できるようにした
- `switchedToDataChannel` は `sendDisconnectIfNeeded` などの用途で引き続き利用する

### テスト

- 既存の `SoraMessagingE2ETest` の「onDataChannel 発火後の最初の送信が成功すること」検証が、タイミングに依存せず成功するようになる
- ローカル環境に Android SDK が無いため E2E テストは CI で確認する

### 変更履歴

- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追加した

### ドキュメント

- スキルドキュメントの送信条件表記を実装に合わせて修正した
