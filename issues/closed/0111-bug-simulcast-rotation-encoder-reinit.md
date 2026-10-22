# simulcast 回転時に StreamEncoderWrapper の initEncode が SIGABRT する

- Created: 2026-10-22
- Completed: 2026-10-22
- Branch: feature/fix-simulcast-rotation-encoder-reinit
- Polished: 2026-10-22

## 目的

simulcast 有効時に画面を回転させると、`SimulcastVideoEncoderFactoryWrapper.StreamEncoderWrapper` の `release()` が Executor を shutdown した後、libwebrtc の `SimulcastEncoderAdapter` が同じエンコーダーへ `initEncode()` を呼ぶ。その結果 `RejectedExecutionException` が発生し、Java 例外が JNI を越えて SIGABRT になる問題を修正する。

## 現状

- 再現環境: Sora Android SDK 2026.4.0-canary.6（libwebrtc 156.8078.5.1）、Pixel 7 / Android 17 (API 37)、Sora 2026.1.3、VP9 simulcast (r0/r1/r2)、HW encoder `c2.exynos.vp9.encoder`、`softwareVideoEncoderOnly=false`。
- 再現アプリ: quickstart `feature/2026.4` (2418f17) の `MainActivity` に `enableSimulcast()` を追加したもの。
- `StreamEncoderWrapper` は単一スレッドの `ExecutorService` を持ち、`release()` の `finally` で `executor.shutdown()` を呼ぶ。この shutdown は issue 0008 で Executor のスレッドリーク対策として追加されたもの。
- libwebrtc の `SimulcastEncoderAdapter::InitEncode` は、呼び出しの先頭で `Release()` を呼んだ後、同じエンコーダーへ `InitEncode()` を呼ぶ。そのため `StreamEncoderWrapper.release()` は終端として扱えず、再初期化に耐える必要がある。
- 画面回転でフレームサイズが変わると `VideoStreamEncoder` が再構成し、`SimulcastEncoderAdapter::InitEncode` が呼ばれる。`InitEncode` は先頭で `Release()` を呼んで既存のエンコーダーを解放した後、同じ `StreamEncoderWrapper` に `InitEncode()` を呼ぶ。そのため shutdown 済み Executor への `submit()` が `RejectedExecutionException` を投げる。3 → 1 のレイヤー数縮小は再構成に付随する現象で、release の原因ではない。
- その Java 例外が JNI を越え、libwebrtc の `HandleException`（`sdk/android/src/jni/jvm.cc`）が `RTC_CHECK(false)` で abort する。クラッシュスレッドは `VideoEncoderQue`。
- 2026.4.0-canary.6 の CHANGES にある回転時クラッシュ修正は、受信映像の texture → I420 変換失敗による SIGABRT であり、本件は送信側 simulcast エンコーダーの別経路。受信トラックがない状態でも再現する。
- 初回接続時も 3 → 1 の縮小は発生するが、その時点では `SimulcastEncoderAdapter` の `stream_contexts_` が空のため `Release()` は no-op で、成功する。

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
- `initEncode()` / `encode()` / `setRateAllocation()` / `release()` は Executor の submit/get の `Exception` を catch して `VideoCodecStatus.ERROR` を返す。`release()` は `executor` が null または停止済みなら submit せず `VideoCodecStatus.OK` を返して冪等にし、submit も try の内側に置く。`getScalingSettings()` は例外時に `VideoEncoder.ScalingSettings.OFF`、`getImplementationName()` はコンストラクタで取得した文字列を返す。Java 例外を JNI に漏らさない。`OutOfMemoryError` などの `Error` は対象外とする。
- issue 0008 の Executor shutdown 修正（スレッドリーク対策）を維持しつつ、release 後の再初期化を許容する。
- 回帰テストの方針は `## テスト戦略` に従う。

## テスト戦略

- `SoraE2ETestBase` を継承した instrumentation test を `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraSimulcastReconfigureE2ETest.kt` に追加する。
- `DummyVideoCapturer` と `enableVideoUpstream(capturer, null)`、`enableSimulcast()` を使い、`softwareVideoEncoderOnly=false` で Sora に接続する。
- SDK はユーザー提供 capturer の `startCapture()` を呼ばないため、テスト側で `DummyVideoCapturer.startCapture(1280, 720, 15)` を呼ぶ。
- `softwareVideoEncoderOnly=false` だけでは HW エンコーダーが使われる保証がない。`HardwareVideoEncoderFactory.createEncoder()` が非 null を返す HW VP9 実機（Pixel 7 など）で実行し、SW エンコーダーのエミュレータでは `assumeTrue` でスキップする。
- `softwareVideoEncoderOnly=true` だと `SIMULCAST_SOFTWARE` 経路になり、本件の `SimulcastVideoEncoderFactoryWrapper` を通らない。新テストでは `false` にする。
- 接続後に `DummyVideoCapturer.changeCaptureFormat()` で解像度を変更し、`VideoStreamEncoder` の再構成を発生させる。
- 解像度は 1280x720 から 960x540、960x540 から 1280x720 のように、3 レイヤーを維持したまま明確に異なる値へ変更し、`RequiresEncoderReset` が true になるようにする。レイヤー数が減る解像度は HW エンコーダーの最小サイズを下回る可能性があるため使わない。
- `DummyVideoCapturer` の `width` / `height` / `fps` を 1 つの不変オブジェクトにまとめ、`@Volatile` な参照として保持し、`changeCaptureFormat()` の変更が生成スレッドに反映されるようにする。
- 再構成後に `outbound-rtp` の `bytesSent` / `framesEncoded` が増加し、`frameWidth` / `frameHeight` が解像度変更の方向に変化していることを確認する。
- `changeCaptureFormat()` を複数回実行し、繰り返しの再構成でも SIGABRT しないことを確認する。
- `changeCaptureFormat()` 後の再構成で `release()` → `initEncode()` が発生しても SIGABRT せず、接続が維持されることを確認する。
- 実機の画面回転は手動確認とし、E2E では回転時に発生するエンコーダー再構成を解像度変更で再現する。

## 完了条件

- simulcast 有効時に画面を回転させても SIGABRT しない。
- `release()` 後の `initEncode()` が成功し、回転後も simulcast 映像の送信が継続すること。
- Executor の再生成に失敗した場合でも、`RejectedExecutionException` などの `Exception` を JNI に漏らさず `VideoCodecStatus.ERROR` などのエラーとして扱い、プロセスを落とさないこと。`OutOfMemoryError` などの `Error` は対象外とする。
- `getScalingSettings()` / `getImplementationName()` が Executor 停止後も例外を投げないこと。
- `RejectedExecutionException` が JNI を越えない。
- 回帰テストが追加され、HW VP9 エンコーダーを生成できる実機で実行可能であること（CI の SW エミュレータでは skip されるため、実機での実行確認は別途必要）。
- issue 0008 の Executor スレッドリーク対策が維持されていること。
- `CHANGES.md` の `## develop` に `[FIX]` エントリと `- @<担当者名>` 行が追加されている。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/codec/SimulcastVideoEncoderFactoryWrapper.kt`
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/DummyVideoCapturer.kt`
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraSimulcastReconfigureE2ETest.kt`（新規）
- `CHANGES.md`

## 解決方法

- `SimulcastVideoEncoderFactoryWrapper.StreamEncoderWrapper` の Executor を nullable にし、`release()` で shutdown した後に `initEncode()` が呼ばれた場合は新しい Executor を生成するようにした。
- `release()` は Executor が null または停止済みなら `VideoCodecStatus.OK` を返して冪等にした。
- `encode()` のスケール判定で width に加えて height も比較するようにした（width が同じで height だけ異なるフレームが未スケールのまま内部エンコーダーへ渡るのを防ぐ）。
- submit/get の `RejectedExecutionException` などの `Exception` を catch し、`VideoCodecStatus.ERROR` などのエラーとして返すようにした。`InterruptedException` はタスクの完了を待ってから interrupt を復元するようにした。
- `getScalingSettings()` は Executor が無い場合や例外時に `VideoEncoder.ScalingSettings.OFF` を返し、`getImplementationName()` は生成時に取得した文字列を返すようにした。
- `SoraSimulcastReconfigureE2ETest` を追加し、`HardwareVideoEncoderFactory.createEncoder()` が非 null を返す HW VP9 環境でのみ実行するようにした。Sora の offer の simulcast を確認し、encodings の rid を記録した上で、交渉コーデックが VP9 であることを stats から確認する。解像度変更を複数回行い、下位 2 レイヤー（r0 / r1）の `bytesSent` / `framesEncoded` の増加と `frameWidth` / `frameHeight` の変化（増減方向を含む）を確認する。Sora が simulcast / VP9 を提供しない環境では `assumeTrue` でスキップする。
- `DummyVideoCapturer` の width / height / fps を 1 つの不変オブジェクトにまとめ、`@Volatile` で可視性を保証するようにした。
- `CHANGES.md` の `## develop` に `[FIX]` エントリと、e2e テスト追加の `### misc` エントリを追加した。
- 実機での手動確認は Pixel 7a で実施済みである（`## 検証結果` を参照）。`SoraSimulcastReconfigureE2ETest` の実機実行は未実施であり、CI の SW エミュレータでは `assumeTrue` でスキップされるため、実機で実行して stats を確認する必要がある。

## 検証結果

- Pixel 7a（HW VP9 エンコーダー搭載）で simulcast 有効の映像配信を行い、配信中に端末を回転させても SIGABRT などのエラーが発生しないことを確認した（2026-10-22）。
- GitHub Actions の E2E テストが成功した（<https://github.com/shiguredo/sora-android-sdk/actions/runs/39768034954>、2026-10-22）。CI の `pixelApi35` は SW エミュレータのため `SoraSimulcastReconfigureE2ETest` は `assumeTrue` でスキップされ、10 テスト中 1 件 skip・失敗 0 件であった。
- `SoraSimulcastReconfigureE2ETest` の実機実行は未実施である。回転後の `bytesSent` / `framesEncoded` と `frameWidth` / `frameHeight` の変化は、実機で E2E テストを実行して確認する必要がある。
