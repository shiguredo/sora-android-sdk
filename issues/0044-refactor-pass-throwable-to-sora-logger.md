# 例外時に SoraLogger の第三引数へ例外を渡してスタックトレースを出力する

- Priority: Low
- Created: 2026-06-03
- Polished: 2026-06-03
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/refactor-pass-throwable-to-sora-logger

## 目的

例外を捕捉してログ出力している箇所のうち、`SoraLogger` のメソッドに `Throwable` を渡していない箇所を、第三引数へ例外を渡すように改善する。これにより例外発生時にスタックトレースがログへ出力され、原因調査がしやすくなる。

## 優先度根拠

- 既存のログ出力でも動作には影響がなく、出力される情報が増えるだけの軽量なリファクタリングである。
- 不急であり、気付いた箇所から少しずつ直していけばよいため Low とする。

## 現状

`SoraLogger` の各メソッド（`v` / `d` / `i` / `w` / `e`）は第三引数として `Throwable? = null` を受け取り、内部で `android.util.Log` の対応メソッドへそのまま渡す。第三引数に例外を渡すとスタックトレースが出力されるが、例外を捕捉しているのに第三引数へ渡していない箇所が複数存在し、例外メッセージ（`${e.message}`）だけがログに出る状態になっている。

`SoraLogger.kt` の定義（抜粋）。

```kotlin
fun w(
    tag: String?,
    msg: String,
    tr: Throwable? = null,
) {
    if (enabled) {
        Log.w(tag, msg, tr)
    }
}
```

例外を捕捉しているが第三引数へ渡していない箇所の例。

- `SoraMediaChannel.kt`: `SoraLogger.w(TAG, "Failed to setAudioSoftMute: ${e.message}")` ほか、`setVideoHardMute` / `setVideoSoftMute` の失敗ログ。
- `PeerChannel.kt`: `SoraLogger.w(TAG, "Failed to set DegradationPreference: ${e.message}")` ほか、`[audio_recording_pause]` 関連の失敗ログ。
- `RTCComponentFactory.kt`: `SoraLogger.w(TAG, "dispose controllable ADM failed: ${e.message}")` / `SoraLogger.w(TAG, "release ADM failed: ${e.message}")`。

一方で `SoraLogger.w(TAG, msg, it)` のように既に例外を渡している箇所も存在しており、渡している箇所と渡していない箇所が混在している。

## 設計方針

- 例外を捕捉している `catch` ブロック内で `SoraLogger.w` / `SoraLogger.e` を呼び出している箇所を洗い出し、捕捉した例外を第三引数へ渡すように統一する。
- ログメッセージ本文は既存のものを維持し、第三引数の追加のみにとどめてログ表記の変更を最小化する。
- 例外を保持していない箇所（例外オブジェクトが手元にないログ）は対象外とする。

## 完了条件

- 例外を捕捉している箇所の `SoraLogger` 呼び出しで、捕捉した例外が第三引数へ渡されていること。
- 例外発生時のログにスタックトレースが出力されること。
- 既存のログ出力の挙動（メッセージ本文）が変わらないこと。

## 解決方法
