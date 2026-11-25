# E2E 失敗時にクラッシュ調査用の logcat とテストレポートを保存する

- Priority: Medium
- Created: 2026-09-25
- Completed: {YYYY-MM-DD}
- Branch: feature/update-e2e-crash-diagnostics
- Polished: 2026-11-25
- Refreshed: 2026-11-25

## 目的

E2E テストがプロセスクラッシュで失敗したときに、原因を追える情報を artifact として残す。現在はテストレポートと logcat が artifact に保存されず、実行後に参照できない。

## 現状

- `.github/workflows/e2e-test.yml` の `Run E2E tests with logcat` は `"$ADB_PATH" logcat -v time Sora*E2ETest:D SoraSimulcastReconfigureE2ETest:D SoftwareMediaCodecVideoEncoderFactoryForTest:D DummyVideoCapturer:D *:S` で logcat を取得しており、クラッシュ系のタグ (`AndroidRuntime` / `DEBUG` / `libc`) がフィルタで除外される。
- 取得した logcat は `trap` の `cleanup()` が `cat` でジョブログへ出力するだけで、artifact として保存していない。この `cat` は成功時も失敗時も実行される。
- Gradle Managed Device は workflow のフィルタとは別に、テスト単位の logcat を `sora-android-sdk/build/outputs/androidTest-results/` に、テストレポートを `sora-android-sdk/build/reports/androidTests/` に生成するが、workflow はどちらも artifact に保存していない。self-hosted runner のワークスペースは次回の `actions/checkout` で清掃されるため、実行後に参照できない。
- `SoraMessagingE2ETest` のプロセスクラッシュ (issues/closed/0102-bug-fix-e2e-messaging-crash.md) では、workflow の logcat が許可リストで絞られ、GMD の logcat も artifact に保存していなかったため、実行後にスタックトレースを参照できず、原因を特定できなかった。

## 設計方針

- logcat の取得を単発起動からループに変え、テスト開始前からテスト終了後まで取得を継続する。テスト実行中に adb サーバが再起動されると単発の `adb logcat` は終了するため、取得プロセスを再起動できるようにする。
- `adb devices` で `device` 状態の serial を選んで `-s` で対象デバイスを固定し、デバイスとテストプロセスの出現を待ってから取得を開始する。`adb logcat` が終了したら再起動し、テスト終了時にループと子プロセスを停止する。
- `-G` でリングバッファを拡張し、テストプロセス (`jp.shiguredo.sora.sdk.test`) の PID に限定して `-b all -v threadtime` で取得する。システムログに含まれる ICCID や private IP などの PII を artifact に含めない。テストプロセスのログに残る private IP も保存前に `<IP>` に置換する。取得した logcat は `e2e-logs/logcat.txt` に追記し、step の冒頭で `e2e-logs/` を作り直す。
- 現行の `cleanup()` による全量 `cat` を廃止し、テスト step が非 0 で終了したときだけ、クラッシュ関連の行 (`FATAL EXCEPTION` / `AndroidRuntime` / `DEBUG` / `libc` / `tombstone` / `Fatal signal` / `backtrace`) を最大 300 行、および logcat の末尾 100 行をジョブログへ出力する。終了コードは `cleanup()` の先頭で保存する。
- 診断ログに TURN 資格情報、`channel_id`、`access_token`、内部エンドポイント、個人情報 (ICCID、private IP、絶対パス) を含めない。テストの生 signaling メッセージはログに出力しない。
- `actions/upload-artifact` の step を追加し、失敗時 (`if: failure()`) に `e2e-logs/`、`sora-android-sdk/build/reports/androidTests/`、`sora-android-sdk/build/outputs/androidTest-results/**/TEST-*.xml` を artifact 名 `e2e-diagnostics-${{ github.run_attempt }}` として保存する。per-test logcat と `test-result.pb` / `test-result.textproto` は保存しない（資格情報と絶対パスを含むため）。`if-no-files-found: ignore` を指定し、保持期間は 7 日とする。

## 完了条件

- テスト step が非 0 で終了した run で、artifact `e2e-diagnostics-<run_attempt>` から `e2e-logs/logcat.txt`、テストレポート (`reports/androidTests/`)、テスト結果 (`TEST-*.xml`) を取得できること。
- クラッシュを再現した run で、`e2e-logs/logcat.txt` に Java スタック (`FATAL EXCEPTION`) またはネイティブスタック (`Fatal signal` / `backtrace`) が含まれること。
- テスト step が成功した run で、logcat の全量がジョブログへ出力されないこと。
- artifact と診断ログ (`e2e-logs/logcat.txt` と `cleanup` の出力) に TURN 資格情報、`channel_id`、`access_token`、内部エンドポイント、個人情報 (ICCID、private IP、絶対パス) が含まれないこと。

## 変更対象ファイル

- `.github/workflows/e2e-test.yml`
- `.github/dependabot.yml`
- `.gitignore`
- `CHANGES.md`
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETestBase.kt`
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraSignalingE2ETest.kt`
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraCloseTypeE2ETest.kt`
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraRpcE2ETest.kt`

## 解決方法
