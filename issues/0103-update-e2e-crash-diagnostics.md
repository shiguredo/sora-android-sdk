# E2E 失敗時にクラッシュ調査用の logcat とテストレポートを保存する

- Priority: Medium
- Created: 2026-09-25
- Completed: {YYYY-MM-DD}
- Branch: feature/update-e2e-crash-diagnostics
- Polished: 2026-09-25

## 目的

E2E テストがプロセスクラッシュで失敗したときに、原因を追える情報を残す。現在はクラッシュのスタックトレースがどこにも保存されず、調査ができない。

## 現状

- `.github/workflows/e2e-test.yml` の `Run E2E tests with logcat` は `"$ADB_PATH" logcat -v time Sora*E2ETest:D *:S` で logcat を取得しており、クラッシュ系のタグ (`AndroidRuntime` / `DEBUG` / `libc`) がフィルタで除外される。
- `SoraMessagingE2ETest` のプロセスクラッシュ (issues/0102) では、このフィルタのためにクラッシュのスタックトレースが残らず、原因を特定できなかった。
- 取得した logcat はジョブログへ出力するだけで、artifact として保存していない。Gradle Managed Device のテストレポート (`sora-android-sdk/build/reports/androidTests/` と `sora-android-sdk/build/outputs/androidTest-results/`) も保存していない。

## 設計方針

- logcat のフィルタを外して全量を取得する。
- 失敗時はクラッシュ関連の行 (`FATAL EXCEPTION` / `AndroidRuntime` / `DEBUG` / `libc` / `tombstone` / `Fatal signal` / `backtrace`) をジョブログへ出力する。
- `actions/upload-artifact` で logcat とテストレポートを失敗時に保存する。

## 完了条件

- E2E が失敗したときに、logcat とテストレポートが artifact から取得できること。
- クラッシュが発生した場合に、ネイティブスタックまたは Java スタックをジョブログまたは artifact から確認できること。

## 変更対象ファイル

- `.github/workflows/e2e-test.yml`
- `CHANGES.md`

## 解決方法
