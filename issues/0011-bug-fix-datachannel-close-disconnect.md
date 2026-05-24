# シグナリング用以外の DataChannel close で internalDisconnect が呼ばれる

- Priority: Low
- Created: 2026-05-24
- Model: deepseek-v4-pro
- Branch: feature/fix-datachannel-close-disconnect

## 目的

`onDataChannelClosed` コールバックで `dataChannelSignalingCloseEvent` が null の場合、label を問わず `internalDisconnect(null)` が呼ばれ、Sora 接続全体が切断される。シグナリング用（label: "signaling"）以外の DataChannel close では接続全体を切断すべきではない。

## 優先度根拠

- シグナリング用以外の DataChannel（mesh、ユーザ定義の `#` ラベル等）が閉じられた場合でも接続全体が切断される
- Sora の動作仕様として、Sora 側から切断が発生する際は `type: close` メッセージが先に送られ `dataChannelSignalingCloseEvent` が設定されるため、このパスは異常系のみで発火する
- メッシュ DataChannel の PeerConnection 切断など、接続全体の切断を意図しないケースでも発火しうる
- Sora の仕様上意図した動作の可能性もあるため、実装前に仕様確認が必要

## 現状

```kotlin
// SoraMediaChannel.kt:949-964
override fun onDataChannelClosed(label: String, dataChannel: DataChannel) {
    SoraLogger.d(TAG, "[channel:$role] @peer:onDataChannelClosed label=$label")
    if (label == "rpc") {
        failPendingRpc(SoraRpcErrorReason.DATA_CHANNEL_CLOSED)
    }

    dataChannelSignalingCloseEvent?.let { event ->
        // Sora から type: close メッセージを受信済み → 意図的な切断
        internalDisconnect(SoraDisconnectReason.DATACHANNEL_ONCLOSE, event)
        return
    }

    // dataChannelSignalingCloseEvent が null の場合、label を問わず切断される
    internalDisconnect(null)
}
```

## 設計方針

切断を意図した DataChannel close のみで `internalDisconnect` を呼ぶよう、label によるフィルタリングを追加する。切断をトリガーするのはシグナリング用ラベル "signaling" のみとする。

設計判断の保留: 本件は既存の動作を変更するため、Sora の仕様として「signaling ラベル以外の DataChannel close は接続全体の切断を意味しない」ことが確認できてから修正する。仕様確認が取れない場合は `issues/pending/` に移動する。

## 完了条件

- "signaling" ラベルの DataChannel close のみが `internalDisconnect` をトリガーすること
- Sora からの `type: close` メッセージ受信後の close は引き続き `internalDisconnect` が呼ばれること

## 解決方法

```kotlin
override fun onDataChannelClosed(label: String, dataChannel: DataChannel) {
    SoraLogger.d(TAG, "[channel:$role] @peer:onDataChannelClosed label=$label")
    if (label == "rpc") {
        failPendingRpc(SoraRpcErrorReason.DATA_CHANNEL_CLOSED)
    }

    dataChannelSignalingCloseEvent?.let { event ->
        internalDisconnect(SoraDisconnectReason.DATACHANNEL_ONCLOSE, event)
        return
    }

    // シグナリング用 DataChannel の close のみ切断をトリガーする
    if (label == "signaling") {
        internalDisconnect(null)
    }
}
```
