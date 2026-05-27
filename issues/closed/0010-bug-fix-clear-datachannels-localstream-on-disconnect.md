# dataChannels が internalDisconnect でクリアされない

- Priority: Low
- Created: 2026-05-24
- Completed: 2026-05-27
- Model: deepseek-v4-pro
- Branch: feature/fix-clear-on-disconnect

## 目的

`internalDisconnect()` で `peer`、`signaling`、`clientOfferPeer`、`localStream` は null クリアされるが、`dataChannels` マップはクリアされず、切断後も dispose 済みネイティブオブジェクトへの Java 参照が残留する。

## 優先度根拠

- `peer?.disconnect()` によりネイティブリソースは解放されるため、Java からのアクセスで直ちにクラッシュする可能性は低い
- しかし、参照が残っていることで誤ってアクセスされる可能性があり、防御的な観点からクリアすべき
- `internalDisconnect()` のパターン（切断時に null クリア / clear）を他のフィールドにも適用する一貫性のための修正

## 現状

```kotlin
// SoraMediaChannel.kt:227
private var dataChannels: MutableMap<String, DataChannel> = mutableMapOf()

// SoraMediaChannel.kt:1595-1613 — internalDisconnect()
clientOfferPeer?.disconnect(null)
clientOfferPeer = null
peer?.disconnect(null)
peer = null
localStream = null
// dataChannels はクリアされない
listener?.onClose(this)
listener = null
```

## 設計方針

`internalDisconnect()` 内で `dataChannels.clear()` を実行する。

## テスト戦略

コードレビューで検証する。

## 完了条件

- `internalDisconnect()` 完了後に `dataChannels` が空であること
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も忘れずに追記する）

## 解決方法

`internalDisconnect()` 内の `peer = null` の直後に追加する:

```kotlin
peer?.disconnect(null)
peer = null
dataChannels.clear()
```
