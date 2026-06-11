# Android E2E テストを GitHub Actions の self-hosted macOS runner で CI 実行できるようにする

- Priority: Medium
- Created: 2026-06-10
- Completed: 2026-06-11
- Polished: 2026-06-10
- Branch: feature/add-android-e2e-ci
- Model: GPT-5

## 目的

issue 0058 で追加した `androidTest` の E2E テストを、GitHub Actions の self-hosted macOS runner から継続的に実行できるようにする。
ローカルでのみ通る状態では品質ゲートにならないため、workflow と実行前提を CI 向けに固める必要があった。

## 優先度根拠

- E2E テストが CI に組み込まれていない状態では、接続・切断・映像送信の回帰を継続的に検出できない
- self-hosted runner 自体は既に存在しており、残作業は workflow と環境注入の整理が中心だったため Medium とする

## 解決方法

### workflow の整備

`.github/workflows/ci.yml` を self-hosted macOS runner 前提で整備した。

- `runs-on` を `[self-hosted, macOS, ARM64, Apple-M1]` とした
- `timeout-minutes: 45` を設定した
- `actions/setup-java` で JDK 17 を毎回セットアップするようにした
- `ANDROID_SDK_ROOT`、`sdkmanager`、`adb` の存在確認を追加した
- `gradle.properties.example` を `gradle.properties` として配置する step を追加した
- `pixelApi35AndroidE2ETest` を GitHub Actions から直接実行するようにした
- `slack_notify` は `needs: [e2e]` のみを参照し、通知結果は `needs.e2e.result` を使うようにした

### Gradle Managed Device と実行タスク

`sora-android-sdk/build.gradle.kts` に定義済みの `pixelApi35` managed device を CI の正式な実行対象とし、workflow から呼びやすいように alias task を維持した。

- managed device: `pixelApi35`
- 実行タスク: `pixelApi35AndroidE2ETest`

### E2E 用の環境変数注入

CI から `BuildConfig` に必要な値を注入できるようにした。

- `TEST_SIGNALING_URL`
- `TEST_SECRET_KEY`
- `TEST_CHANNEL_ID_PREFIX`
- `TEST_CHANNEL_ID_SUFFIX`

これにより、workflow 側では以下を制御できるようにした。

- シグナリング接続先 URL
- `metadata.access_token`
- E2E 用 `channelId` の prefix / suffix

`channelId` は固定文字列ではなく、`{prefix}e2e-test{suffix}` の形式で組み立てるようにした。
suffix には `github.run_id` を付与し、CI 実行ごとの衝突を避ける構成にした。

### シークレット露出対策

CI で secret を使う構成にしたため、ログ出力も見直した。

- `SoraE2ETest` では `channelId` の実値を出さず、prefix / suffix の設定有無のみをログ出力するようにした
- `TEST_SIGNALING_URL` の実値ログは出さないようにした
- `SoraMediaChannel` の設定ダンプでは、`token`、`secret`、`password`、`authorization`、`credential` を含む key の値を `***` にマスクするようにした

### Actions ログでのデバッグ性確保

GitHub Actions から `SoraE2ETest` の `Log.d` を確認できるようにした。

- `adb logcat` を Gradle 実行と同じ step 内でバックグラウンド起動する
- 収集対象は `SoraE2ETest:D` に絞る
- step 終了時に `trap` で `logcat` を停止し、収集ログを Actions の job log に出力する

別 step で `logcat` を張る構成だと `waiting for device` で停止しやすかったため、Gradle 実行と同じ step にまとめた。

## 実装中に判明した知見

| 知見 | 詳細 |
|---|---|
| runner のシェル初期化ファイルは当てにできない | self-hosted runner では `.zshenv` や `.bashrc` の環境変数がそのまま見えない。`ANDROID_SDK_ROOT` などは workflow の `env` で明示する必要がある |
| `ACCESS_NETWORK_STATE` が不足すると WebRTC JNI abort になる | `androidTest` 側 manifest に権限がないと `jvm.cc` 経由で abort する。issue 0058 側で修正済みだが、CI 化にあたり前提条件として重要だった |
| `slack_notify` の `job.status` は downstream job では不適切 | E2E ジョブの結果を通知したいので `needs.e2e.result` を使う必要がある |
| `adb logcat` は別 step より同一 step の方が安定する | デバイス待ちや PID 管理の問題を避けやすい |

## 完了条件の達成状況

- self-hosted macOS runner で `ci.yml` の E2E ジョブを実行できるようにした
- `pixelApi35AndroidE2ETest` を GitHub Actions から起動できるようにした
- `TEST_SIGNALING_URL` を secret 経由で注入し、E2E テストが通ることを確認した
- `slack_notify` は実在するジョブのみを参照するように修正した
- runner 前提条件と workflow の実行形態を issue に整理した

## 変更対象ファイル

- `.github/workflows/ci.yml` — self-hosted macOS runner 用 E2E workflow の整備、ログ収集、Slack 通知修正
- `sora-android-sdk/build.gradle.kts` — `BuildConfig` 注入、`pixelApi35AndroidE2ETest` task
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt` — secret を出さないログ、`channelId` / `metadata` 注入
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/SoraMediaChannel.kt` — `signalingMetadata` のマスクログ
