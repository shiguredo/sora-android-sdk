# Android E2E テストを self-hosted Linux runner + USB 接続 arm64 実機で CI 実行できるようにする

- Priority: Low
- Created: 2026-06-09
- Completed:
- Polished: 2026-06-09
- Branch: feature/add-android-e2e-ci
- Model: GPT-5

## 目的

issue 0058 で追加した `androidTest` の E2E テストを、既存の macOS arm64 self-hosted runner (Gradle Managed Device) に加えて、Linux self-hosted runner + USB 接続 arm64 実機でも継続的に実行できるようにする。
Linux 実機 CI を併設することで、エミュレータでは検出しづらい実ハードウェア依存の問題（HW エンコーダー、実機特有のパフォーマンス特性）を CI で捕捉できるようになる。

## 優先度根拠

- 既存の macOS + Managed Device による E2E CI は稼働しており、最低限の品質保証は機能しているため優先度は Low とする

## 現状

- `sora-android-sdk` には `androidTest` ベースの E2E テスト (映像送信確認 1 件) が存在する (issue 0058)
- 既存の `.github/workflows/ci.yml` には macOS arm64 self-hosted runner + Gradle Managed Device (pixelApi35) による E2E ジョブが存在し、稼働中
- テストは `arm64-v8a` の `libjingle_peerconnection_so` を前提としており、以下が成立する:
  - arm64-v8a 実機: 動作可能
  - macOS arm64 ホスト上の arm64 エミュレータ: 動作可能 (既存 ci.yml で実績あり)
  - Linux x86_64 ホスト上の x86_64 エミュレータ: AAR に x86_64 の `.so` が含まれていないため動作不可
- `ci.yml` の `slack_notify` ジョブは `needs: [build, e2e]` と宣言しているが、`ci.yml` 内に `build` ジョブが存在しないため常にスキップされる既存の不具合がある

## 設計方針

### 全体方針

- 既存の macOS + Managed Device 構成は **維持する**。本 issue では新たに Linux USB 実機構成を **追加** する
- `ci.yml` に Linux 実機用の E2E ジョブを追加し、既存 macOS ジョブと共存させる
- ジョブ名を `e2e-macos` (既存) / `e2e-linux` (新規) にリネームして区別を明確にする
- `slack_notify` の `needs` を `[e2e-macos, e2e-linux]` に修正し、既存のデッドロックを解消する

### runner 構成

- `runs-on` は `[self-hosted, linux, x64]` を利用する
- テスト対象の arm64 実機を USB 常時接続し、`adb devices` で `device` 状態であることを前提とする
- runner ホストには以下を事前インストールする:
  - Android SDK Platform Tools (`adb`)
  - Android SDK（`sdkmanager` によるライセンス承諾を含む）
- 実機のシリアル番号を `ANDROID_SERIAL` 環境変数として runner ホストまたは job の `env` に設定する

### workflow 設計

`ci.yml` に以下の変更を加える:

1. 既存 `e2e` ジョブを `e2e-macos` にリネームする
2. 新規 `e2e-linux` ジョブを追加する。以下の設定を含める:
   - `timeout-minutes: 45`（macOS ジョブと同様）
   - `runs-on: [self-hosted, linux, x64]`
   - `env.SORA_SIGNALING_URL: ${{ secrets.SORA_SIGNALING_URL }}`
   - `env.ANDROID_SERIAL` に対象実機のシリアル番号を設定する
   - テスト step を `continue-on-error: true` で実行し、後続 step で失敗時に最大 1 回の再実行を行う
   - step の内容:
     1. checkout
     2. JDK 17 セットアップ (`actions/setup-java`、distribution: temurin、`cache: 'gradle'`)
     3. `ANDROID_SDK_ROOT` の存在確認と `sdkmanager --licenses` 承諾（macOS ジョブと同等のバリデーション）
     4. `gradle.properties` 配置 (`cp gradle.properties.example gradle.properties`)
     5. `adb devices` による実機接続確認。`device` 状態の端末が 0 台の場合はエラーメッセージを出力して fail
     6. `./gradlew :sora-android-sdk:connectedDebugAndroidTest --stacktrace` を `continue-on-error: true` で実行
     7. 前 step 失敗時: `adb logcat -d -b main,system,crash -v time` を実行して job log に出力し、`./gradlew :sora-android-sdk:connectedDebugAndroidTest --stacktrace` を 1 回リトライ
     8. 前 step でも失敗時: `adb logcat -d -b main,system,crash -v time` を再度実行
3. `slack_notify` の `needs` を `[e2e-macos, e2e-linux]` に修正する。なお runner がオフラインでジョブがスキップされた場合、`slack_notify` もスキップされ通知が飛ばないが、これは運用上の許容範囲とする

### 実行対象タスク

- Linux 実機用には `connectedDebugAndroidTest` をそのまま使用する
- 既存の `pixelApi35` managed device 定義と `pixelApi35AndroidE2ETest` タスクは macOS ジョブが引き続き使用するため維持する

### 依存関係

- issue 0058 (androidTest 基盤 + DummyVideoCapturer): 完了済み
- issue 0059 (ダミー音声入力): 未完了。0059 完了後も `e2e-linux` ジョブの構成に変更は不要（テストコード側にテストケースが追加されるのみ）

## 完了条件

- `ci.yml` に Linux USB 実機用の E2E ジョブ (`e2e-linux`) が追加されていること
- 既存の macOS E2E ジョブが `e2e-macos` にリネームされ、そのまま稼働すること
- `slack_notify` の `needs` が `[e2e-macos, e2e-linux]` に修正されていること
- `adb devices` の確認で実機未接続時に原因が分かるエラーメッセージが出ること
- テスト失敗時に `adb logcat` が job log に出力されること
- `SORA_SIGNALING_URL` を secret から注入して E2E テストが通ること

## 変更対象ファイル

- `.github/workflows/ci.yml` — 既存 `e2e` → `e2e-macos` リネーム、新規 `e2e-linux` 追加、`slack_notify.needs` 修正
- `sora-android-sdk/build.gradle.kts` — 変更不要（managed device 設定は macOS ジョブ用に維持）

## 解決方法
