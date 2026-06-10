# Android E2E テストを GitHub Actions の self-hosted macOS runner で CI 実行できるようにする

- Priority: Medium
- Created: 2026-06-10
- Completed:
- Polished: 2026-06-10
- Branch: feature/add-android-e2e-ci
- Model: GPT-5

## 目的

issue 0058 で追加した `androidTest` の E2E テストを、GitHub Actions の self-hosted macOS runner から継続的に実行できるようにする。
現在、macOS マシン自体は GitHub organization に self-hosted runner として登録済みだが、workflow の整備と運用前提の明文化が未完了である。

## 優先度根拠

- self-hosted runner は既に存在しており、残作業は workflow 整備と運用前提の明文化が中心である
- ただし E2E テストが CI の品質ゲートにまだ完全には組み込まれていないため、優先度は Low ではなく Medium とする

## 現状

- `sora-android-sdk` には `androidTest` ベースの E2E テストが存在する
  - `recvonly` の接続・切断確認
  - `DummyVideoCapturer` による映像送信確認
- テストは `arm64-v8a` の `libjingle_peerconnection_so` を前提としており、Apple Silicon 上の arm64 エミュレーターで実行可能である
- GitHub organization には macOS マシンが self-hosted runner として登録済みである
- `.github/workflows/ci.yml` は macOS arm64 self-hosted runner + Gradle Managed Device (`pixelApi35`) を使う草案になっている
- ただし現状の `ci.yml` には以下の不整合がある
  - `slack_notify` が `needs: [build, e2e]` を参照しているが、同一 workflow に `build` ジョブが存在しない
  - self-hosted runner の前提条件と運用手順が issue として整理されていない

## 設計方針

### 全体方針

- self-hosted macOS runner + Gradle Managed Device 構成を正式な CI 実行形態として採用する
- 既存の `ci.yml` を整備し、organization runner を利用して安定実行できる状態にする
- Linux 実機や別 runner への展開は別 issue として切り出し、本 issue には含めない

### runner 構成

- `runs-on` は `[self-hosted, macOS, ARM64]` を利用する
- runner ホストは Apple Silicon を前提とする
- runner ホストには以下が事前に整備されていることを前提とする
  - JDK 17
  - Android SDK
  - `cmdline-tools/latest/bin/sdkmanager`
  - arm64-v8a の emulator / system image を取得可能な構成

### workflow 設計

`ci.yml` に以下の変更を加える:

1. `e2e` ジョブを self-hosted macOS runner 用の正式ジョブとして整備する
2. `timeout-minutes: 45`、`runs-on: [self-hosted, macOS, ARM64]`、`env.SORA_SIGNALING_URL` を維持する
3. step の内容を以下で固定する
   1. checkout
   2. JDK 17 セットアップ (`actions/setup-java`、distribution: temurin、`cache: 'gradle'`)
   3. `ANDROID_SDK_ROOT` と `sdkmanager` の存在確認
   4. `gradle.properties` 配置 (`cp gradle.properties.example gradle.properties`)
   5. `sdkmanager --licenses` 承諾
   6. `platform-tools`, `platforms;android-35`, `emulator`, `system-images;android-35;google_apis;arm64-v8a` の導入確認
   7. `./gradlew :sora-android-sdk:pixelApi35AndroidE2ETest --stacktrace` 実行
4. 失敗時のデバッグ性向上のため、必要なら emulator のログや `adb logcat` を収集する
5. `slack_notify` の `needs` は実在するジョブだけを参照するよう修正する

### 実行対象タスク

- `pixelApi35` managed device 定義を利用する
- workflow からは `pixelApi35AndroidE2ETest` を呼び出す

### 依存関係

- issue 0058 (androidTest 基盤 + DummyVideoCapturer): 完了済み
- issue 0059 (ダミー音声入力): 未完了。完了後は同じ macOS workflow 上で音声 E2E も実行する

## 完了条件

- self-hosted macOS runner で `ci.yml` の E2E ジョブが実行できること
- `pixelApi35AndroidE2ETest` が GitHub Actions から起動できること
- `SORA_SIGNALING_URL` を secret から注入して E2E テストが通ること
- `slack_notify` の `needs` が実在するジョブのみを参照していること
- runner 前提条件と workflow の実行形態が issue に整理されていること

## 変更対象ファイル

- `.github/workflows/ci.yml` — self-hosted macOS runner 用 E2E workflow の整備、`slack_notify.needs` 修正
- `sora-android-sdk/build.gradle.kts` — `pixelApi35AndroidE2ETest` task を維持

## 解決方法

- self-hosted macOS runner を使う E2E workflow を整備する
- `SORA_SIGNALING_URL` を secret から注入する
- `pixelApi35AndroidE2ETest` を GitHub Actions から呼び出す
- `slack_notify` の依存関係を修正する
