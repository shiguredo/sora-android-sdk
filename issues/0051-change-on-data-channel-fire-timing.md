# onDataChannel の発火タイミングを DataChannel 接続確立時に修正する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/change-on-data-channel-fire-timing

## 目的

DataChannel が確立されたことをユーザーへ通知するコールバック `MediaChannel.Listener.onDataChannel` の発火タイミングを見直し、DataChannel の接続が実際に確立されたタイミングで発火するように修正する。

## 優先度根拠

- 現状でも DataChannel 自体は利用できるが、発火タイミングが実際の接続確立より早く、ユーザーが「DataChannel が利用可能になった」と判断する根拠として正確でない。
- API の挙動として誤解を招きやすく、後方互換のない変更を伴うため計画的に対応したいが、緊急のバグではないため Medium とする。

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

一方、個々の DataChannel が実際にオープンしたことは `PeerChannel.Listener.onDataChannelOpen` で通知されるが、現状ではここで `onDataChannel` を発火していない。

```kotlin
// SoraMediaChannel.kt peerListener
override fun onDataChannelOpen(
    label: String,
    dataChannel: DataChannel,
) {
    this@SoraMediaChannel.dataChannels[label] = dataChannel
}
```

つまり、`type: switched` を受信した時点で `onDataChannel` が発火しており、個々の DataChannel が実際に確立されたタイミングとは一致していない。

なお、過去の検討では `type: switched` 受信時にメッセージング機能利用可能の通知を行う設計とされ、すべての DataChannel がオープンしたことを確認する実装は一旦見送られた経緯がある。

## 設計方針

- 最初の `onDataChannelOpen` が発火したタイミングで `MediaChannel.Listener.onDataChannel` を発火する方針を検討する。
- 現状は `type: offer` の `data_channels` から作成したラベルリストを `type: switched` 受信時にまとめて渡しているが、これは他の Sora SDK の同種コールバックとは渡すデータも発火タイミングも異なる。仕様の整合性を踏まえつつ、Android SDK としてどのタイミング・どのデータで通知するかを決める。
- 個別のラベルごとに通知する方針も選択肢として検討する。
- 後方互換のない変更となるため、移行方針を整理する。

## 完了条件

- `onDataChannel` が DataChannel の接続確立タイミングで発火すること。
- 発火タイミング変更に伴う後方互換のない変更を、`CHANGES.md` の `develop` セクションに `[CHANGE]` エントリとして追記すること。

## 解決方法
