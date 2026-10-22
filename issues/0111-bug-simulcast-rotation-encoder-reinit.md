# simulcast 回転時に StreamEncoderWrapper の initEncode が SIGABRT する

- Created: 2026-10-22
- Completed: {YYYY-MM-DD}
- Branch: feature/fix-simulcast-rotation-encoder-reinit
- Polished: {YYYY-MM-DD}

## 目的

simulcast 有効時に画面を回転させると、`SimulcastVideoEncoderFactoryWrapper.StreamEncoderWrapper` の `release()` が Executor を shutdown した後、libwebrtc の `SimulcastEncoderAdapter` が同じエンコーダーへ `initEncode()` を呼ぶ。その結果 `RejectedExecutionException` が発生し、Java 例外が JNI を越えて SIGABRT になる問題を修正する。

## 現状

- `StreamEncoderWrapper` は単一スレッドの `ExecutorService` を持ち、`release()` の `finally` で `executor.shutdown()` を呼ぶ。この shutdown は issue 0008 で Executor のスレッドリーク対策として追加されたもの。
- libwebrtc の `SimulcastEncoderAdapter::InitEncode` は、再構成時に `Release()` を呼んだ後、同じエンコーダーへ `InitEncode()` を呼ぶ。`VideoEncoder.release()` は終端ではなく、再初期化される前提になっている。
- 画面回転で simulcast のレイヤー数が 3 から 1 に変わると、`release()` → `initEncode()` の順に呼ばれ、shutdown 済み Executor への `submit()` が `RejectedExecutionException` を投げる。
- その Java 例外が JNI を越え、libwebrtc の `HandleException`（`sdk/android/src/jni/jvm.cc`）が `RTC_CHECK(false)` で abort する。クラッシュスレッドは `VideoEncoderQue`。
- 2026.4.0-canary.6 の CHANGES にある回転時クラッシュ修正は、受信映像の texture → I420 変換失敗による SIGABRT であり、本件は送信側 simulcast エンコーダーの別経路。受信トラックがない状態でも再現する。
- 初回接続時も 3 → 1 の縮小は発生するが、`release()` 前なので成功する。

### 再現ログ

```text
11:25:10.816  simulcast.cc: Reducing simulcast layer count from 3 to 1
11:25:10.899  video_encoder_wrapper.cc: release: 0
11:25:10.900  simulcast_encoder_adapter.cc: [SEA] InitEncode: Single-encoder mode
11:25:10.900  System.err: java.util.concurrent.RejectedExecutionException:
              Task ... rejected from ThreadPoolExecutor[... Shutting down,
              pool size = 1, active threads = 0, queued tasks = 0,
              completed tasks = 967]
11:25:10.900  at ...SimulcastVideoEncoderFactoryWrapper$StreamEncoderWrapper
              .initEncode(...)
11:25:10.901  rtc: # Fatal error in: ../../sdk/android/src/jni/jvm.cc, line 81
11:25:10.901  rtc: # Check failed: false
11:25:10.904  libc: Fatal signal 6 (SIGABRT) ... tid 26507 (VideoEncoderQue)
```

## 再現手順

1. quickstart の `MainActivity` の `SoraMediaOption` に `enableSimulcast()` を追加し、映像送信ありで Sora に接続する。
2. 端末を縦向きから横向きに回転させる。
3. 回転直後に `VideoEncoderQue` スレッドで SIGABRT することを確認する。

## 関連 issue

- `issues/closed/0008-bug-fix-simulcast-encoder-leak.md`
  - `StreamEncoderWrapper.release()` に `executor.shutdown()` を追加した修正。
  - 本 issue は、その修正が release 後の `initEncode()` を考慮していないために発生する regression。
  - 単純に revert すると Executor のスレッドリークが再発するため、release 後の再初期化とリーク防止を両立させる必要がある。

## 設計方針

- `StreamEncoderWrapper` の Executor を `initEncode()` 時に再生成可能にする。`release()` 後に `initEncode()` が呼ばれたら、新しい Executor を作る。
- `release()` は現在の Executor を shutdown してよいが、`StreamEncoderWrapper` 自体は再利用可能にする。`release()` は冪等にする。
- `initEncode()` / `encode()` / `setRateAllocation()` / `getScalingSettings()` / `getImplementationName()` で Executor の submit/get の例外を catch し、`VideoCodecStatus.ERROR` などのエラーを返す。Java 例外を JNI に漏らさない。
- issue 0008 の Executor shutdown 修正（スレッドリーク対策）を維持しつつ、release 後の再初期化を許容する。
- `release()` → `initEncode()` の回帰テストと、simulcast + 画面回転の E2E を追加する。

## 完了条件

- simulcast 有効時に画面を回転させても SIGABRT しない。
- `release()` 後の `initEncode()` が成功する、またはエラーを返してプロセスを落とさない。
- `RejectedExecutionException` が JNI を越えない。
- 回帰テストが追加されている。
- issue 0008 の Executor スレッドリーク対策が維持されていること。
- `CHANGES.md` の `## develop` に `[FIX]` エントリが追加されている。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/codec/SimulcastVideoEncoderFactoryWrapper.kt`
- `CHANGES.md`

## 解決方法

未着手
