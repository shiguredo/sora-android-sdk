# SignalingChannel: ping 非同期 getStats 競合と wsCandidates クリーンアップ不足

- Priority: High
- Created: 2026-05-24
- Completed: 2026-05-27
- Model: deepseek-v4-pro
- Branch: feature/fix-signaling-channel-race

## 目的

`SignalingChannelImpl` における 2 つの競合問題を修正する:

1. `onPingMessage` で `listener.getStats()` の非同期コールバックが `disconnect()` による `listener = null` と競合して NPE を起こす。また `sendPongMessage` が切断済み `ws` に send する可能性がある
2. `disconnect()` が `ws` のみをクローズし、`wsCandidates` 内の未解決候補 WebSocket をクローズしないためリソースリークが発生する

## 優先度根拠

### 問題 1: ping getStats 競合

- `listener != null` チェックと `listener!!.getStats()` の間に `disconnect()` が `listener = null` を実行すると NPE
- `getStats` の非同期コールバック内で `sendPongMessage` → `ws?.let { ... }.send()` が呼ばれるが、`ws` の null クリアは行われておらず、切断後に不要な send が発生する可能性がある。ただし `ws?.let` で保護されているため NPE には至らない

### 問題 2: wsCandidates クリーンアップ不足

- `disconnect()` は `ws?.close(1000, null)` のみで、`wsCandidates` 内の未解決候補をクローズしない
- `onOpen` の `closing` チェックは早期 return するが WebSocket をクローズしない
- 複数シグナリング URL 指定時、未解決の候補 WebSocket がリソースを消費し続ける

## 現状

```kotlin
// SignalingChannel.kt:556-567 — onPingMessage
private fun onPingMessage(text: String) {
    val ping = MessageConverter.parsePingMessage(text)
    if (ping.stats == true && listener != null) {   // TOCTOU: null チェックと listener!! の間に競合
        listener!!.getStats { report ->
            sendPongMessage(report)                  // 非同期コールバック内で ws に send
        }
    } else {
        sendPongMessage(null)
    }
}

// SignalingChannel.kt:569-575 — sendPongMessage
private fun sendPongMessage(report: RTCStatsReport?) {
    ws?.let { ws ->
        val msg = MessageConverter.buildPongMessage(report)
        ws.send(msg)                                // ws は切断後に null クリアされない
    }
}

// SignalingChannel.kt:447-461 — disconnect
override fun disconnect(disconnectReason: SoraDisconnectReason?) {
    if (closing.get()) return
    closing.set(true)
    client.dispatcher.executorService.shutdown()
    ws?.close(1000, null)                           // ws のみクローズ、wsCandidates は未処理
    listener?.onDisconnect(disconnectReason, ...)
    listener = null                                 // このタイミングで onPingMessage と競合
}

// SignalingChannel.kt:625-628 — onOpen 内の closing チェック
if (closing.get()) {
    SoraLogger.i(TAG, "signaling is closing")
    return                                          // WebSocket をクローズせずに return
}
```

## 設計方針

### ping 競合対策

`onPingMessage` で `listener` のローカルスナップショットを取得してから使用する。`listener?.let { ... }` で安全に扱い、TOCTOU を回避する。`sendPongMessage` の `ws` アクセスは既存の `ws?.let` で保護されているが、切断後に不要な send が行われないよう、`sendPongMessage` 自体を `closing` チェックでガードする。

### wsCandidates クリーンアップ

`disconnect()` 内で `wsCandidates` を走査し、未解決の候補 WebSocket をすべてキャンセルしてクリアする。`ws` は一貫性のために null クリアするが、本修正の主眼は `wsCandidates` の解放と、`onOpen` 内での late callback 抑止である。これらの操作は既存のコードコメントに従い `synchronized(this)` で保護する（`ws` と `wsCandidates` は両方を同時に更新する必要があるため）。

`onOpen` の `closing` チェック時にも `webSocket.close()` を呼んでリソースを解放する。

## スレッド安全性に関する判断

`listener` フィールドには `@Volatile` を追加しない。ローカルスナップショットパターン（`val currentListener = listener`）で TOCTOU を回避する。

`ws` と `wsCandidates` は `synchronized(this)` で保護する。これは既存のコードコメント（SignalingChannel.kt:316）にも記載されている方針に従っている。

## テスト戦略

`SignalingChannelImpl` の内部状態を変更する修正であり、`private` メンバーへの変更のみ。以下の方針で検証する:

- コードレビュー: ローカルスナップショットパターン、`synchronized` ブロック、`wsCandidates` クリーンアップが正しく行われていることを確認
- 既存テスト: `sora-android-sdk/src/test/` 以下の既存テストがすべて通過すること
- 結合テスト: 複数シグナリング URL 指定時の接続・切断で `wsCandidates` が正しくクリーンアップされることを手動確認

## 完了条件

- `onPingMessage` と `disconnect()` が並行して実行されても NPE が発生しないこと
- `disconnect()` 後に ping の非同期コールバックが完了しても、切断済み WebSocket に不要な send が発生しないこと
- `disconnect()` 呼び出し時に `wsCandidates` 内の未解決候補 WebSocket がすべてキャンセルされ、解放されること
- 切断確定後の late `onOpen` で WebSocket がクローズされること
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も忘れずに追記する）

## 解決方法

4 箇所を修正する:

### 1. onPingMessage: ローカルスナップショット化

```kotlin
private fun onPingMessage(text: String) {
    val ping = MessageConverter.parsePingMessage(text)
    val currentListener = listener
    if (ping.stats == true && currentListener != null) {
        currentListener.getStats { report ->
            sendPongMessage(report)
        }
    } else {
        sendPongMessage(null)
    }
}
```

### 2. disconnect: wsCandidates クリーンアップ + ws null クリア

```kotlin
override fun disconnect(disconnectReason: SoraDisconnectReason?) {
    if (closing.get()) return
    closing.set(true)
    client.dispatcher.executorService.shutdown()
    synchronized(this) {
        ws?.close(1000, null)
        wsCandidates.forEach { it.cancel() }
        wsCandidates.clear()
        ws = null
    }
    listener?.onDisconnect(disconnectReason, ...)
    listener = null
}
```

### 3. onOpen: closing 時に WebSocket をクローズ

```kotlin
if (closing.get()) {
    SoraLogger.i(TAG, "signaling is closing")
    webSocket.close(1000, null)
    return
}
```

### 4. sendPongMessage: closing ガードを追加

`disconnect()` が先に走った後、遅れて完了する `getStats` 非同期コールバックからの send を抑制する。

```kotlin
private fun sendPongMessage(report: RTCStatsReport?) {
    if (closing.get()) {
        return
    }
    ws?.let { ws ->
        val msg = MessageConverter.buildPongMessage(report)
        ws.send(msg)
    }
}
```
