# kotlin-reflect 依存を外す

- Priority: Low
- Created: 2026-06-03
- Completed:
- Polished: 2026-08-21
- Model: Opus 4.8
- Branch: feature/refactor-remove-kotlin-reflect

## 目的

`kotlin-reflect` への依存が不要であることが調査で確定したため、依存を削除する。不要な依存を取り除くことでライブラリの依存数を削減する。

注: ファイル名 prefix は `investigate` のままとする。調査は完了済みだが、削除後の検証で No-Go 余地（kotlin-reflect が必要と判明する可能性）を残すためである（0061 と同型の特例）。

## 優先度根拠

- 調査は完了済みで、残作業は依存宣言の削除と検証のみ。実装リスクが低く緊急性もないため Low とする。
- 依存削除は後方互換のある変更（`[UPDATE]`）であり、急ぐ必要がない。

## 事前調査

簡易な確認（`./gradlew :sora-android-sdk:assembleDebug` + `./gradlew :sora-android-sdk:test`）では `kotlin-reflect` を外してもビルドとテストが通った（2026-06-03 時点）。

## 現状

`kotlin-reflect` は以下で依存として宣言されている。

- `gradle/libs.versions.toml`: `kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect" }`
- `sora-android-sdk/build.gradle.kts`: `implementation(libs.kotlin.reflect)`

`src` 配下のコードに `kotlin.reflect` API を直接利用している箇所は見当たらない。シグナリングの JSON シリアライズ / デシリアライズには Gson を利用しているが、Gson は Java のリフレクションを使用しており `kotlin-reflect` を必要としない。

`releaseRuntimeClasspath` の依存グラフ上でも、`kotlin-reflect` は明示的な `implementation` 宣言によってのみ入っており、他依存が実質的に必須として持ち込んでいる状況は確認できていない。

## 設計方針

- `kotlin-reflect` の直接利用箇所が見当たらず、依存グラフ上でも明示依存としてのみ入っているため、まずは削除を前提に作業する。
- 削除後に既存 build / test / E2E で問題が出た場合のみ、必要性を再調査する（No-Go 時の運用は「依存関係」セクション参照）。
- 削除する場合は `CHANGES.md` の `develop` セクションに `[UPDATE]` エントリを追記する。
  - `[CHANGE]` ではなく `[UPDATE]` とする理由: `kotlin-reflect` は `implementation` 依存であり、コンシューマーの compile classpath に露出しない。SDK コードに `kotlin.reflect` の利用がなく、公開 API にも影響がないため、後方互換のない変更には該当しない。依存削除を `[UPDATE]` で記載した前例（CHANGES.md の「grgit から git コマンドに移行する」）とも整合する。

## 対応手順

### 実装

1. **依存宣言の削除**: `gradle/libs.versions.toml` から `kotlin-reflect` の宣言を、`sora-android-sdk/build.gradle.kts` から `implementation(libs.kotlin.reflect)` を削除する。

### 検証

2. **依存グラフの確認**: 削除後の `releaseRuntimeClasspath` と `testRuntimeClasspath` を確認し、`kotlin-reflect` が実依存として依存グラフから消えることを確認する。
   - 注: `implementation(platform(libs.kotlin.bom))`（build.gradle.kts:177）により、依存グラフには `kotlin-reflect:2.2.10 (c)` という constraint 行が残る。`(c)` は dependency constraint であり実依存ではない。
   - 確認コマンド:
     - `./gradlew :sora-android-sdk:dependencies --configuration releaseRuntimeClasspath`
     - `./gradlew :sora-android-sdk:dependencies --configuration testRuntimeClasspath`
3. **コード / テスト利用の確認**: `src/main` / `src/test` / `src/androidTest` 配下で `kotlin.reflect` API を使っていないことを再確認する。
4. **ビルド / テストの実行**: 削除後に以下が通ることを確認する。
   - `./gradlew :sora-android-sdk:assembleDebug`
   - `./gradlew :sora-android-sdk:testDebugUnitTest`
   - `./gradlew :sora-android-sdk:assembleRelease`（publish 対象は `singleVariant("release")` のため）
   - E2E: `pixelApi35AndroidE2ETest`（ローカルで実行できない場合は e2e-test.yml の CI 実行結果で代替）

## 完了条件

- `gradle/libs.versions.toml` と `sora-android-sdk/build.gradle.kts` から `kotlin-reflect` の宣言が削除されていること。
<<<<<<< HEAD
- `kotlin-reflect` を削除した状態で `:sora-android-sdk:assembleDebug` / `:sora-android-sdk:testDebugUnitTest` / `:sora-android-sdk:assembleRelease` が通ること。
- 既存の E2E テスト（`pixelApi35AndroidE2ETest`、または e2e-test.yml の CI 実行結果）が通ること。
- `src/main` / `src/test` / `src/androidTest` 配下に `kotlin.reflect` API の利用がないこと。
- `releaseRuntimeClasspath` と `testRuntimeClasspath` の依存グラフから `kotlin-reflect` が実依存として消えていること（`(c)` の constraint 行は実依存ではないため除外して判定する）。
- `CHANGES.md` の `develop` セクションに `[UPDATE]` エントリを追記すること。

## 変更対象ファイル

- `gradle/libs.versions.toml`
- `sora-android-sdk/build.gradle.kts`
- `CHANGES.md`

## 依存関係

- issue 0061（libwebrtc-c リライトの調査）— 0018 を「独立対応候補」として分類済み（0061:522）。
  - 0061 の Phase 0（ビルド構成刷新、`compileOnly` 降格）は `build.gradle.kts` の dependencies ブロック（0018 の変更対象）と `libs.versions.toml` を書き換えるため、**0061 Phase 0 のマージ前に 0018 を完了させる**ことを推奨する。0061 Phase 0 が先行して dependencies ブロックを書き換えた場合、kotlin-reflect の行が 0061 側で消え、0018 が moot になる可能性がある。その場合は本 issue を close し、0061 側で対応済みであることを解決方法に記録する。
  - No-Go 時（kotlin-reflect が必要と判明した場合）の運用: 必要性を再調査した結果を解決方法に追記し、`issues/closed/` へ移動する。ブランチ `feature/refactor-remove-kotlin-reflect` は破棄し、develop には変更を入れない。
=======
- `kotlin-reflect` を削除した状態で `:sora-android-sdk:assembleDebug` と `:sora-android-sdk:testDebugUnitTest` が通ること。
- 既存の E2E テストを実行できる場合は、それが通ること。
- `releaseRuntimeClasspath` の依存グラフから `kotlin-reflect` が消えていること。
- `CHANGES.md` の `develop` セクションに該当エントリを追記すること。
>>>>>>> 8429215 (0018 不要なら kotlin-reflect 依存を外す issue を更新)

## 解決方法
