# SimulcastVideoEncoderFactoryWrapper: ExecutorService 未 shutdown と scaledBuffer 例外時未 release

- Priority: Medium
- Created: 2026-05-24
- Completed: 2026-05-27
- Model: deepseek-v4-pro
- Branch: feature/fix-simulcast-encoder-leak

## 目的

`SimulcastVideoEncoderFactoryWrapper.StreamEncoderWrapper` の 2 つのリソースリークを修正する:

1. `ExecutorService` が `release()` 後も `shutdown()` されず、非デーモンスレッドがプロセス終了まで生き残る
2. `encode()` 内で `encoder.encode()` が例外を投げた場合に `scaledBuffer.release()` が実行されず、ネイティブバッファがリークする

## 優先度根拠

### ExecutorService 未 shutdown

- `Executors.newSingleThreadExecutor()` で生成されたスレッドは非デーモンスレッドのため、明示的に `shutdown()` しない限りプロセス終了まで生き続ける
- `StreamEncoderWrapper` はエンコーダー生成のたびに new されるため、`release()` のたびにスレッドが蓄積する
- 長時間稼働するアプリケーションでスレッド数が増加し、リソース枯渇の原因になる

### scaledBuffer 例外時未 release

- `VideoFrame.buffer.cropAndScale()` で確保されたネイティブバッファが例外パスで解放されない
- 映像エンコード中のスケーリングは高頻度で行われるため、リークの蓄積速度が速い

## 現状

```kotlin
// SimulcastVideoEncoderFactoryWrapper.kt:45
val executor: ExecutorService = Executors.newSingleThreadExecutor()

// SimulcastVideoEncoderFactoryWrapper.kt:77-80 — release()
override fun release(): VideoCodecStatus {
    val future = executor.submit(Callable { return@Callable encoder.release() })
    return future.get()
    // executor.shutdown() が呼ばれていない
}

// SimulcastVideoEncoderFactoryWrapper.kt:82-119 — encode()（抜粋）
val scaledBuffer = frame.buffer.cropAndScale(...)
val scaledFrame = VideoFrame(scaledBuffer, ...)
val result = encoder.encode(scaledFrame, encodeInfo)  // ここで例外が発生する可能性
scaledBuffer.release()  // 例外時はここに到達しない
result
```

## 設計方針

1. `release()` 内で `executor.shutdown()` を呼ぶ
2. `encode()` 内の `scaledBuffer` 確保から解放までを `try/finally` で囲む

## テスト戦略

コードレビューで検証する。単体テストの追加は不要（リソースリークの自動検証は困難なため）。

## 完了条件

- `release()` 呼び出し後に `executor` のスレッドが終了すること
- `encoder.encode()` が例外を投げた場合でも `scaledBuffer.release()` が実行されること
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も忘れずに追記する）

## 解決方法

### 1. release(): executor.shutdown() を追加

`encoder.release()` が失敗した場合でも `executor` が確実に shutdown されるよう、`try/finally` で保護する。

```kotlin
override fun release(): VideoCodecStatus {
    val future = executor.submit(Callable { return@Callable encoder.release() })
    return try {
        future.get()
    } finally {
        executor.shutdown()
    }
}
```

### 2. encode(): try/finally で scaledBuffer を保護

```kotlin
val scaledBuffer = frame.buffer.cropAndScale(...)
try {
    val scaledFrame = VideoFrame(scaledBuffer, ...)
    encoder.encode(scaledFrame, encodeInfo)
} finally {
    scaledBuffer.release()
}
```
