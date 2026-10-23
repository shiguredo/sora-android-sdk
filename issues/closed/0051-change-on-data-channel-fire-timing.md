# onDataChannel の発火タイミングを Android クライアント側の DataChannel 準備完了時に修正する

- Priority: Medium
- Created: 2026-06-03
- Completed: 2026-08-17
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/change-on-data-channel-fire-timing

## 目的

コールバック `MediaChannel.Listener.onDataChannel` を「Android クライアント側の DataChannel 準備が完了したタイミング」の通知として定義し直し、発火タイミングをクライアント側で DataChannel が OPEN になったタイミングに修正する。API シグネチャは変更しない。

現状は Sora サーバから `type: switched` を受信したタイミングで発火しているが、これはサーバ側のシグナリング切替完了の通知であり、クライアント側でメッセージング用 DataChannel が送受信可能になったタイミングとは厳密には一致しない。ユーザーが「DataChannel が利用可能になった」と判断する根拠として正確でない。

### reopen 理由

2026-08-14 に一旦 close したが、以下の観点で再度 open する。

- 一括通知（`onDataChannel`）のタイミング変更は実装・実機検証済みであるが、ラベル個別の OPEN 通知コールバックの**対象ラベル範囲**を C++ SDK の実装に合わせて全ラベルに変更する。
- C++ SDK の `SoraSignaling::OnStateChange`（sora_signaling.cpp）は、offer の `data_channels` に含まれる**全ラベル**（`signaling` / `stats` / `notify` / `#` 始まりを含む）を対象に、ラベル個別・OPEN 時に `OnDataChannel(label)` を発火する。`#` フィルタはない。
- 当初は Rust SDK の `on_data_channel_open` に合わせて `#` ラベルのみ対象としたが、C++ / Python SDK が全ラベル対象であることを確認したため、Android も全ラベル対象に変更する。

## 優先度根拠

- 発火タイミングをクライアント側の準備完了に合わせることで、コールバックの意味論が明確になり、ユーザーが DataChannel を安全に利用できるようになる。
- API シグネチャは不変で後方互換があるが、発火タイミングの変更でユーザーの挙動が変わる可能性がある。緊急のバグではないため Medium とする。

## 現状

`onDataChannel` はシグナリング `type: switched` を受信したタイミングで発火している。

```kotlin
// SoraMediaChannel.kt handleSwitched()
private fun handleSwitched(switchedMessage: SwitchedMessage) {
    switchedToDataChannel = true
    // ...
    listener?.onDataChannel(this, dataChannelsForMessaging)
}
```

`onDataChannel` に渡すラベルリスト `dataChannelsForMessaging` は、`type: offer` の `data_channels` のうちラベルが `#` で始まるものを抽出して作成している。

```kotlin
// SoraMediaChannel.kt
dataChannelsForMessaging =
    offerMessage.dataChannels.filter {
        it.containsKey("label") && (it["label"] as? String)?.startsWith("#") ?: false
    }
```

一方、個々の DataChannel がクライアント側で OPEN になったことは `PeerChannel.kt` の `DataChannel.Observer.onStateChange` で検知できるが、現状では CLOSED のみを処理しており、OPEN 遷移の検知も `onDataChannel` の発火も行っていない。

```kotlin
// PeerChannel.kt
override fun onStateChange() {
    if (dataChannel.state() == DataChannel.State.CLOSED) {
        listener?.onDataChannelClosed(dataChannel.label(), dataChannel)
    }
}
```

つまり、`type: switched` を受信した時点で `onDataChannel` が発火しており、個々の DataChannel がクライアント側で OPEN になったタイミングとは一致していない。

なお、`PeerChannel.Listener.onDataChannelOpen` は PeerConnection の `onDataChannel`（DataChannel オブジェクト作成時）の直後に呼ばれる通知であり、OPEN 遷移を表すものではない点に注意する。

## 他 SDK との比較

| SDK | 発火タイミング | 備考 |
|---|---|---|
| Android（現状） | `switched` 受信時 | サーバ側のシグナリング切替完了 |
| iOS | `switched` 受信時 | サーバ仕様に依存 |
| JS | `switched` 受信時に全 datachannels 分 | サーバ仕様に依存 |
| Rust | `on_data_channel` = 作成時、`on_data_channel_open` = OPEN 遷移時 | クライアント側で DataChannel の state を監視して検知（connection.rs の `handle_datachannel_state`） |
| Python | Rust バックエンドの作成時に対応 | open 系コールバックなし |

クライアント側の DataChannel の OPEN 遷移を検知してコールバックを発火しているのは Rust が唯一であり、本 issue の観点（クライアント側の準備完了通知）の参考実装となる。

## 設計方針

API の後方互換を維持しつつ（`onDataChannel` のシグネチャは不変）、発火タイミングをクライアント側の DataChannel 準備完了に合わせる。さらに Rust SDK 踏襲として、ラベル個別の OPEN 通知コールバックを追加する。

### 1. 一括通知（`onDataChannel`）の発火タイミング修正

- `PeerChannel.kt` の `DataChannel.Observer.onStateChange` で `DataChannel.State.OPEN` への遷移を検知する。現在は CLOSED のみを処理しているため、OPEN 遷移の検知を追加する。
- メッセージング用ラベル（`#` で始まるラベル）の DataChannel が**すべて** OPEN になったタイミングで `MediaChannel.Listener.onDataChannel` を発火する。これは Rust SDK の「全 DataChannel が OPEN になった時点で DataChannel シグナリングを有効化する」（`opened_datachannels.len() == data_channel_configs.len()`）という判断と整合する。
- 発火時に渡すラベルリストは従来どおり `dataChannelsForMessaging`（`type: offer` の `data_channels` から `#` 始まりを抽出したもの）とする。
- `type: switched` 受信時には `onDataChannel` を発火しない。
- 既に OPEN 済みのメッセージング用ラベルがある場合は、その状態を保持し、最後の 1 つが OPEN になった時点で発火する。
- 後方互換: API シグネチャは不変であり、発火タイミングのみの変更となる。

### 2. ラベル個別の OPEN 通知コールバック追加（C++ / Python 踏襲）

- `MediaChannel.Listener` に `onDataChannelOpened(mediaChannel, label)` を追加する。これは C++ SDK の `OnDataChannel`（OPEN 遷移時・ラベル個別・全ラベル対象・引数はラベル文字列）とタイミング・粒度を一致させる。
- 発火条件: offer の `data_channels` に含まれる**全ラベル**の DataChannel がクライアント側で OPEN になった時点で、ラベルごとに 1 回発火する。`#` 始まりのラベルに限定しない。
- `onDataChannelOpen`（内部コールバック）でラベル個別に通知する。重複通知は C++ SDK の `notified` フラグ相当の集合（`openedDataChannelLabels`）で防止する。
- 既存の `onDataChannel`（一括通知・`#` ラベル限定）は維持し、後方互換を保つ。

### 3. 完了条件・テスト

- 一括通知のタイミング修正に加え、ラベル個別通知コールバックの追加を完了条件に含める。
- 単体テストまたは E2E テストで検証する（e2e 検証は別 issue 0078 で実施）。

## 完了条件

- メッセージング用ラベルの DataChannel がすべてクライアント側で OPEN になった時点で `onDataChannel` が発火すること。
- `type: switched` 受信時には `onDataChannel` が発火しないこと。
- offer の `data_channels` に含まれる全ラベルの DataChannel が OPEN になった時点で `onDataChannelOpened` がラベルごとに一度だけ発火すること。
- 発火タイミングの変更と `onDataChannelOpened` の追加を、`CHANGES.md` の `develop` セクションにエントリとして追記すること（`onDataChannel` のタイミング変更は `[UPDATE]`、`onDataChannelOpened` の追加は `[ADD]`）。
- 単体テストまたは E2E テストで上記の発火タイミングを検証すること。

## 解決方法

### 実装済み（reopen 前に完了）

#### PeerChannel.kt

- `DataChannel.Observer.onStateChange` で `DataChannel.State.OPEN` への遷移を検知し、`PeerChannel.Listener.onDataChannelOpen` を通知するようにした。これにより `onDataChannelOpen` は「DataChannel オブジェクトが作成された時」ではなく「クライアント側で DataChannel が OPEN になった時」の通知となる。
- 従来は `PeerConnection.onDataChannel`（DataChannel オブジェクト作成時）の直後に `onDataChannelOpen` を呼んでいたが、その直接呼び出しを削除した。
- libwebrtc の `DataChannel.RegisterObserver` は登録時に現在の state を `onStateChange` で即時通知しない実装であるため、登録時点で既に OPEN の場合の防御的チェックを追加した（`onStateChange` による通知と重複する可能性はあるが、`SoraMediaChannel` 側の `onDataChannelNotified` フラグで二重発火を防止している）。

#### SoraMediaChannel.kt

- `openedMessagingLabels`（`MutableSet<String>`）を追加し、`onDataChannelOpen` でメッセージング用ラベル（`#` で始まるラベル）の OPEN を追跡するようにした。
- `maybeNotifyDataChannelAvailable()` を追加し、`dataChannelsForMessaging` に含まれる全メッセージング用ラベルが OPEN になった時点で `MediaChannel.Listener.onDataChannel` を一度だけ発火するようにした。
- `handleSwitched()` 内の `onDataChannel` 発火を削除し、`switched` 受信時には発火しないようにした。
- `handleInitialOffer`（リダイレクト等で offer が再送された場合）と切断時に `openedMessagingLabels` / `onDataChannelNotified` をリセットするようにした。
- メッセージング用ラベルが存在しない場合は発火しない。

#### テスト・変更履歴

- `SoraMessagingE2ETest.kt` のコメントを新しい発火タイミングに合わせて更新した。
- 発火タイミングの e2e 検証は別 issue（0078）で実施する。
- `CHANGES.md` の `develop` セクションに `[UPDATE]` エントリを追記した。

#### 検証

- `./gradlew :sora-android-sdk:compileDebugKotlin` が成功すること。
- `./gradlew :sora-android-sdk:testDebugUnitTest` が成功すること（既存テストへの影響なし）。
- `./gradlew :sora-android-sdk:ktlintCheck` が成功すること。
- 実機（sora-android-sdk-samples の MessagingActivity）で `onDataChannel` の発火タイミングを確認した。ログ上 `onDataChannel: data_channels=[...]` が `@signaling:onSwitched` より先に発火し、その直後に `@peer:onDataChannelMessage label=stats` が届くことを確認した。つまり `onDataChannel` 発火時点で DataChannel はクライアント側で OPEN 済みであり、メッセージの送受信が可能な状態であることが確認できた。

### 追加実装（reopen 後）

#### MediaChannel.Listener への `onDataChannelOpened` 追加

- `SoraMediaChannel.Listener` に `onDataChannelOpened(mediaChannel, label)` を追加した。C++ SDK の `OnDataChannel` と同様のタイミング（OPEN 遷移時）・粒度（ラベル個別・引数はラベル文字列）で通知する。
- デフォルト実装は空（`{}`）とし、既存の `Listener` 実装に影響を与えない。

#### SoraMediaChannel.kt の変更

- `onDataChannelOpen`（内部コールバック）で、PeerConnection 経由で受け取った**すべての DataChannel** が OPEN になった時点で `onDataChannelOpened` をラベル個別に発火する。ラベル名によるフィルタ（`#` 始まりに限定する等）は行わない。
  - 対象の決定方法について: C++ SDK は offer の `data_channels` から構築した `dc_labels_` を基準に発火するが、Android は offer との突合を行わず受信ベースで発火する。Sora サーバは offer に含めた DataChannel しか作成しないため実用上は一致するが、厳密には「offer 基準」と「受信基準」の差異がある。あえて offer 基準のフィルタは入れず、予期しないラベルが届いた場合もユーザーに通知される受信基準を採用した。
- 重複通知は `openedDataChannelLabels`（全ラベル用）で防止する（C++ SDK の `notified` フラグ相当）。メッセージング用ラベルの追跡（`openedMessagingLabels`）は `onDataChannel`（一括通知）の判定専用に分離した。
- リセット箇所（offer 再送・切断）に `openedDataChannelLabels.clear()` を追加した。
- KDoc と実装コメントに「受け取ったすべての DataChannel を対象とする」旨と C++ SDK との対応を明記した。

#### CHANGES.md

- `[ADD]` エントリで `onDataChannelOpened` の追加を追記した。対象は「受け取ったすべての DataChannel」とし、C++ SDK の `OnDataChannel` との対応は「同様のタイミング（OPEN 遷移時）・粒度（ラベル個別）」に限定して記載した。

### 追加実装の検証

- `./gradlew :sora-android-sdk:compileDebugKotlin` が成功すること。
- `./gradlew :sora-android-sdk:testDebugUnitTest` が成功すること（既存テストへの影響なし）。
- `./gradlew :sora-android-sdk:ktlintCheck` が成功すること。
- 実機（sora-android-sdk-samples の MessagingActivity）で `onDataChannelOpened` のラベル個別発火を確認した。ログ上 `onDataChannelOpened: label=signaling` → `notify` → `push` → `stats` → `rpc` → `#spam` → `#egg` の順で発火し、**全ラベル対象**（`#` 始まりに限定しない）でラベルごとに 1 回ずつ通知されること、全ラベル OPEN 後に一括通知（`onDataChannel`）が発火することを確認した。RPC ラベル（`rpc`）も発火すること、重複発火がないことも確認した。
