# PeerConnectionFactory.initialize が 2 回目以降の useTracer 変更を無視する

- Priority: Low
- Created: 2026-05-24
- Completed: 2026-05-27
- Model: deepseek-v4-pro
- Branch: feature/fix-peerconnection-factory-use-tracer

## 目的

`PeerChannelImpl.initializeIfNeeded()` は static な `isInitialized` フラグで `PeerConnectionFactory.initialize()` の呼び出しを 1 回に制限している。`PeerConnectionFactory.initialize()` はプロセス全体で 1 回しか呼べない制約があるが、2 回目以降の `initializeIfNeeded()` 呼び出しで異なる `useTracer` 値が指定されても無視され、開発者に通知されない。

## 優先度根拠

- `PeerConnectionFactory.initialize()` はグローバルな初期化であり複数回呼び出せない制約がある
- 最初の接続で `useTracer = false`、2 回目の接続で `useTracer = true` を指定しても tracer は有効化されず、開発者が気付かない
- 実際の利用シーンでは `useTracer` の値はアプリケーション全体で一貫していることが多く、実害は限定的

## 現状

```kotlin
// PeerChannel.kt:160-178
private var isInitialized = false

fun initializeIfNeeded(context: Context, useTracer: Boolean) {
    if (!isInitialized) {
        val options = PeerConnectionFactory.InitializationOptions
            .builder(context)
            .setEnableInternalTracer(useTracer)
            .setFieldTrials("")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        isInitialized = true
    }
    // 2 回目以降: useTracer の値に関わらず何もせず、通知もない
}
```

## 設計方針

初回の `useTracer` 値を保持する変数を追加し、2 回目以降の呼び出しで初回と異なる値が指定された場合に警告ログで通知する。`PeerConnectionFactory.initialize()` の 1 回制約は変更不可のため、挙動自体は変更しない。

## テスト戦略

コードレビューで検証する。

## 完了条件

- 2 回目以降の `initializeIfNeeded()` 呼び出しで `useTracer` の値が初回と異なる場合、警告ログが出力されること
- 初回と同じ値の場合は既存の動作が維持されること
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記すること（`- @<担当者名>` の行も忘れずに追記する）

## 解決方法

初回の `useTracer` 値を保持する変数を追加し、2 回目以降に比較する:

```kotlin
private var isInitialized = false
private var initialUseTracer: Boolean? = null

fun initializeIfNeeded(context: Context, useTracer: Boolean) {
    if (!isInitialized) {
        val options = PeerConnectionFactory.InitializationOptions
            .builder(context)
            .setEnableInternalTracer(useTracer)
            .setFieldTrials("")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        if (SoraLogger.libjingleEnabled) {
            Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO)
        }
        isInitialized = true
        initialUseTracer = useTracer
    } else if (useTracer != initialUseTracer) {
        SoraLogger.w(TAG, "PeerConnectionFactory.initialize() already called with useTracer=$initialUseTracer. useTracer=$useTracer is ignored.")
    }
}
```
