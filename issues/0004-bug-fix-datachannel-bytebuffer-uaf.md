# unzipBufferIfNeeded で compress=false 時にネイティブ ByteBuffer をそのまま返す

- Priority: High
- Created: 2026-05-24
- Model: deepseek-v4-pro
- Branch: feature/fix-datachannel-bytebuffer-uaf

## 目的

`PeerChannel.unzipBufferIfNeeded()` で `compress=false` の場合、WebRTC ネイティブの direct ByteBuffer をそのまま `listener.onDataChannelMessage()` に渡している。リスナーがこのバッファを非同期で保持した場合、`onDataChannelMessage` コールバックを抜けた後に WebRTC 内部でバッファが再利用・解放され、解放済みメモリへのアクセス（Use-After-Free）でネイティブクラッシュ（SIGSEGV）が発生する。

`compress=true` の場合は `ZipHelper.unzip()` が `ByteBuffer.wrap()` で新たなバッファを生成するため問題ない。

## 優先度根拠

- WebRTC の DataChannel メッセージで受信した `ByteBuffer` はネイティブの direct buffer であり、コールバックを抜けた後に再利用・解放される可能性がある
- アプリケーション側でバッファを別スレッドに渡したり非同期処理に利用した場合、解放済みメモリへのアクセスでネイティブクラッシュが発生する
- 修正は `false` 分岐にバッファコピーを追加するのみで完了する

## 現状

```kotlin
// PeerChannel.kt:730-739
override fun unzipBufferIfNeeded(
    label: String,
    buffer: ByteBuffer,
): ByteBuffer {
    val compress = compressLabels.contains(label)
    return when (compress) {
        true -> ZipHelper.unzip(buffer)  // ZipHelper.unzip が新しい ByteBuffer を返す
        false -> buffer                  // 元のネイティブ ByteBuffer をそのまま返す
    }
}

// SoraMediaChannel.kt:909
val buffer = peer!!.unzipBufferIfNeeded(label, dataChannelBuffer.data)
listener?.onDataChannelMessage(this@SoraMediaChannel, label, buffer)
```

## 設計方針

`unzipBufferIfNeeded()` の `false` 分岐で、元の `ByteBuffer` の内容をヒープにコピーした新しい `ByteBuffer` を生成して返す。ヒープバッファは JVM の GC で管理されるため、リスナーが非同期で保持しても安全。

## テスト戦略

`ReusableCompositeDisposable` 同様、`PeerChannel` の実オブジェクトのみに依存しており、モック不要で単体テストが可能。

追加するテストケース (`sora-android-sdk/src/test/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannelTest.kt` に新規作成。`rtc` ディレクトリも新規作成が必要):

- `compress=false` のラベルに対して呼び出した場合、返却される `ByteBuffer` が元のバッファと異なるインスタンスであること
- 返却されたバッファが元のバッファと同じ内容を持つこと
- 返却されたバッファがヒープバッファであること (`ByteBuffer.isDirect()` が false)
- `compress=true` の場合の既存動作（`ZipHelper.unzip` 経由で新しいバッファを返す）が変更されないこと

## 完了条件

- `compress=false` の場合、`listener.onDataChannelMessage()` に渡される `ByteBuffer` がヒープにコピーされた独立したバッファであること
- `compress=true` の場合の既存動作が変更されないこと
- `PeerChannelTest.kt` の全テストが通過すること
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も忘れずに追記する）

## 解決方法

`PeerChannel.unzipBufferIfNeeded()` の `false` 分岐にバッファコピー処理を追加する:

```kotlin
override fun unzipBufferIfNeeded(
    label: String,
    buffer: ByteBuffer,
): ByteBuffer {
    val compress = compressLabels.contains(label)
    return when (compress) {
        true -> ZipHelper.unzip(buffer)
        false -> {
            val copy = ByteBuffer.allocate(buffer.remaining())
            copy.put(buffer.duplicate())
            copy.flip()
            copy
        }
    }
}
```
