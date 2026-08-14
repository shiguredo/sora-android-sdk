# onDataChannel の発火タイミングを Android クライアント側の DataChannel 準備完了時に修正する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/change-on-data-channel-fire-timing

## 目的

コールバック `MediaChannel.Listener.onDataChannel` を「Android クライアント側の DataChannel 準備が完了したタイミング」の通知として定義し直し、発火タイミングをクライアント側で DataChannel が OPEN になったタイミングに修正する。API シグネチャは変更しない。

現状は Sora サーバから `type: switched` を受信したタイミングで発火しているが、これはサーバ側のシグナリング切替完了の通知であり、クライアント側でメッセージング用 DataChannel が送受信可能になったタイミングとは厳密には一致しない。ユーザーが「DataChannel が利用可能になった」と判断する根拠として正確でない。

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

API の後方互換を維持しつつ（`onDataChannel` のシグネチャは不変）、発火タイミングのみをクライアント側の DataChannel 準備完了に合わせる。

- `PeerChannel.kt` の `DataChannel.Observer.onStateChange` で `DataChannel.State.OPEN` への遷移を検知する。現在は CLOSED のみを処理しているため、OPEN 遷移の検知を追加する。
- メッセージング用ラベル（`#` で始まるラベル）の DataChannel が**すべて** OPEN になったタイミングで `MediaChannel.Listener.onDataChannel` を発火する。これは Rust SDK の「全 DataChannel が OPEN になった時点で DataChannel シグナリングを有効化する」（`opened_datachannels.len() == data_channel_configs.len()`）という判断と整合する。
- 発火時に渡すラベルリストは従来どおり `dataChannelsForMessaging`（`type: offer` の `data_channels` から `#` 始まりを抽出したもの）とする。
- ラベルごとの個別通知（Rust の `on_data_channel_open` 相当）は行わず、従来どおり一括通知とする。
- `type: switched` 受信時には `onDataChannel` を発火しない。
- 既に OPEN 済みのメッセージング用ラベルがある場合は、その状態を保持し、最後の 1 つが OPEN になった時点で発火する。
- 後方互換: API シグネチャは不変であり、発火タイミングのみの変更となる。

## 完了条件

- メッセージング用ラベルの DataChannel がすべてクライアント側で OPEN になった時点で `onDataChannel` が発火すること。
- `type: switched` 受信時には `onDataChannel` が発火しないこと。
- 発火タイミングの変更を、`CHANGES.md` の `develop` セクションに `[UPDATE]` エントリとして追記すること（API 互換の挙動変更のため）。
- 単体テストまたは E2E テストで上記の発火タイミングを検証すること。

## 解決方法
