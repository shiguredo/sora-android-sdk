# setAudioSoftMute が切断後も dispose 済み AudioTrack にアクセスする

- Priority: High
- Created: 2026-05-24
- Completed: 2026-05-27
- Model: deepseek-v4-pro
- Branch: feature/fix-set-audio-soft-mute-after-disconnect

## 目的

`internalDisconnect()` で `peer` や `signaling` は `null` にクリアされるが、`localStream` はクリアされていない。切断後に `setAudioSoftMute()` が呼ばれると、切断済みまたは dispose 済みの `AudioTrack.setEnabled()` に到達する可能性があり、安全でない API 状態になる。

なお `setVideoSoftMute()` は `peer?.localVideoManager?.setTrackEnabled()` と `peer` 経由の safe call で保護されているため、同様の問題はない。

## 優先度根拠

- `localStream` は `internalDisconnect()` 内で `null` に設定されておらず、切断後も参照が残り続ける
- `AudioTrack.setEnabled()` はネイティブ層を跨ぐ処理であり、dispose 済みまたは切断済みトラックへの呼び出しは未定義挙動やネイティブ層での異常につながるリスクがある
- `try/catch` で Java 例外は捕捉できても、ネイティブ層起因の異常までは十分に防げない可能性がある
- 修正は `internalDisconnect()` に 1 行追加するのみで完了する

## 現状

```kotlin
// SoraMediaChannel.kt:512
private var localStream: MediaStream? = null

// SoraMediaChannel.kt:539-557
fun setAudioSoftMute(muted: Boolean): Boolean {
    val localStream = localStream ?: run {
        SoraLogger.w(TAG, "Cannot call setAudioSoftMute: Local MediaStream not initialized")
        return false
    }
    val audioTrack = localStream.audioTracks.firstOrNull() ?: run {
        SoraLogger.w(TAG, "Cannot call setAudioSoftMute: Local MediaStream has no AudioTrack")
        return false
    }
    return try {
        audioTrack.setEnabled(!muted)   // ← dispose 済み AudioTrack の可能性
        true
    } catch (e: Exception) {
        SoraLogger.w(TAG, "Failed to setAudioSoftMute: ${e.message}")
        false
    }
}

// SoraMediaChannel.kt:1549-1592 — internalDisconnect()
// peer = null (1579 行) はあるが、localStream = null はない
```

## 設計方針

`internalDisconnect()` 内の `peer = null` の直後に `localStream = null` を追加する。これにより `setAudioSoftMute()` は `localStream ?: run { ... }` の早期リターンで安全に `false` を返し、切断後のトラックアクセスを回避できる。

## テスト戦略

`localStream` は `private` であり直接テストできないが、公開 API である `setAudioSoftMute()` の戻り値で検証可能。

テストケース（`sora-android-sdk/src/test/kotlin/jp/shiguredo/sora/sdk/channel/SoraMediaChannelTest.kt` に新規作成）:

- `SoraMediaChannel.disconnect()` 呼び出し後に `setAudioSoftMute(true)` を呼ぶと `false` が返ること
- 接続確立後の通常状態で `setAudioSoftMute(true)` を呼ぶと `true` が返ること（正常系の確認）
- `localStream` が null の場合に `setAudioSoftMute()` が `false` を返し、クラッシュしないこと

## 完了条件

- `internalDisconnect()` 呼び出し後に `setAudioSoftMute()` が実行されても、dispose 済みの `AudioTrack` にアクセスせず `false` を返すこと
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も忘れずに追記する）

## 解決方法

`SoraMediaChannel.internalDisconnect()` の `peer = null`（1597 行）の直後に 1 行追加する:

```kotlin
peer?.disconnect(null)
peer = null
localStream = null   // 追加
```
