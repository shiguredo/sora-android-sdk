# ZipHelper と stringToDataChannelBuffer で DeflaterInputStream/InflaterInputStream が close されない

- Priority: Medium
- Created: 2026-05-24
- Model: deepseek-v4-pro
- Branch: feature/fix-ziphelper-stream-close

## 目的

`ZipHelper.zip()` / `ZipHelper.unzip()` と `PeerChannelImpl.stringToDataChannelBuffer()` で生成される `DeflaterInputStream` および `InflaterInputStream` が `close()` されず、ネイティブの zlib リソースが GC 依存の遅延解放になっている。

## 優先度根拠

- zlib のネイティブリソース（圧縮・展開用の内部バッファ等）が GC 任せで解放されるため、メモリ使用量が不必要に高止まりする
- DataChannel メッセージの送受信は高頻度で行われるため、リークの蓄積が顕著になる可能性がある
- `DeflaterInputStream` と `InflaterInputStream` は `close()` によりネイティブリソースを即時解放する設計
- Kotlin の `.use {}` で簡潔に修正可能

## 現状

```kotlin
// ZipHelper.kt:10-12
fun zip(buffer: ByteBuffer): ByteBuffer =
    ByteBuffer.wrap(DeflaterInputStream(ByteBufferBackedInputStream(buffer)).readBytes())
    // DeflaterInputStream が close されていない

fun unzip(buffer: ByteBuffer): ByteBuffer =
    ByteBuffer.wrap(InflaterInputStream(ByteBufferBackedInputStream(buffer)).readBytes())
    // InflaterInputStream が close されていない

// PeerChannel.kt — stringToDataChannelBuffer
private fun stringToDataChannelBuffer(label: String, data: String): DataChannel.Buffer {
    val inStream = when (compressLabels.contains(label)) {
        true -> DeflaterInputStream(ByteArrayInputStream(data.toByteArray()))
        false -> ByteArrayInputStream(data.toByteArray())
    }
    val byteBuffer = ByteBuffer.wrap(inStream.readBytes())
    return DataChannel.Buffer(byteBuffer, true)
    // inStream が close されていない
}
```

## 設計方針

Kotlin の `.use {}` 拡張関数を利用し、ストリームの読み取り完了後に確実に `close()` を呼ぶ。

## テスト戦略

コードレビューで検証する。既存の DataChannel 圧縮・展開動作が継続して正常に動作することは既存の結合テストで確認する。

## 完了条件

- `ZipHelper.zip()` / `ZipHelper.unzip()` で `DeflaterInputStream` / `InflaterInputStream` が `close()` されること
- `stringToDataChannelBuffer()` で `DeflaterInputStream` が `close()` されること
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も忘れずに追記する）

## 解決方法

### ZipHelper.kt: .use {} で close を保証

```kotlin
fun zip(buffer: ByteBuffer): ByteBuffer =
    DeflaterInputStream(ByteBufferBackedInputStream(buffer)).use { stream ->
        ByteBuffer.wrap(stream.readBytes())
    }

fun unzip(buffer: ByteBuffer): ByteBuffer =
    InflaterInputStream(ByteBufferBackedInputStream(buffer)).use { stream ->
        ByteBuffer.wrap(stream.readBytes())
    }
```

### PeerChannel.kt: .use {} で close を保証

```kotlin
private fun stringToDataChannelBuffer(label: String, data: String): DataChannel.Buffer {
    val inStream = when (compressLabels.contains(label)) {
        true -> DeflaterInputStream(ByteArrayInputStream(data.toByteArray()))
        false -> ByteArrayInputStream(data.toByteArray())
    }
    val byteBuffer = inStream.use { stream -> ByteBuffer.wrap(stream.readBytes()) }
    return DataChannel.Buffer(byteBuffer, true)
}
```
