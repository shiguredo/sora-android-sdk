# dataChannels と localStream が internalDisconnect でクリアされない

- Priority: Low
- Created: 2026-05-24
- Model: deepseek-v4-pro
- Branch: feature/fix-clear-on-disconnect

## 目的

`internalDisconnect()` で `peer` や `signaling` は null クリアされるが、`dataChannels` マップと `localStream` はクリアされず、切断後も dispose 済みネイティブオブジェクトへの Java 参照が残留する。

`localStream = null` については #0005 と重複するが、#0005 が `setAudioSoftMute` の修正に焦点を当てているのに対し、本 issue は切断時の包括的なクリーンアップとして扱う。

## 優先度根拠

- `peer?.disconnect()` によりネイティブリソースは解放されるため、Java からのアクセスで直ちにクラッシュする可能性は低い
- しかし、参照が残っていることで誤ってアクセスされる可能性があり、防御的な観点からクリアすべき
- Low 優先度だが、`internalDisconnect` のパターン（null クリア）を他のフィールドにも適用する一貫性のための修正

## 現状

```kotlin
// SoraMediaChannel.kt:227
private var dataChannels: MutableMap<String, DataChannel> = mutableMapOf()

// SoraMediaChannel.kt:512
private var localStream: MediaStream? = null

// SoraMediaChannel.kt:1549-1592 — internalDisconnect()
peer?.disconnect(null)
peer = null
// dataChannels も localStream もクリアされない
listener?.onClose(this)
listener = null
```

## 設計方針

`internalDisconnect()` 内で `dataChannels.clear()` と `localStream = null` を実行する。

## テスト戦略

コードレビューで検証する。

## 完了条件

- `internalDisconnect()` 完了後に `dataChannels` が空、`localStream` が `null` であること
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も忘れずに追記する）

## 解決方法

`internalDisconnect()` 内の `peer = null` の直後に追加する:

```kotlin
peer?.disconnect(null)
peer = null
dataChannels.clear()
localStream = null
```
