# 不要なら kotlin-reflect 依存を外す

- Priority: Low
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/refactor-remove-kotlin-reflect

## 目的

`kotlin-reflect` への依存が本当に必要かを調査し、不要であれば依存を削除する。不要な依存を取り除くことでライブラリの依存数と配布物のサイズを削減する。

## 事前調査

簡易な確認（`./gradlew :sora-android-sdk:assembleDebug` + `./gradlew :sora-android-sdk:test`）では `kotlin-reflect` を外してもビルドとテストが通った。

## 現状

`kotlin-reflect` は以下で依存として宣言されている。

- `gradle/libs.versions.toml`: `kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect" }`
- `sora-android-sdk/build.gradle.kts`: `implementation(libs.kotlin.reflect)`

`src` 配下のコードに `kotlin.reflect` API を直接利用している箇所は見当たらない。シグナリングの JSON シリアライズ / デシリアライズには Gson を利用しているが、Gson は Java のリフレクションを使用しており `kotlin-reflect` を必要としない。

`releaseRuntimeClasspath` の依存グラフ上でも、`kotlin-reflect` は明示的な `implementation` 宣言によって入っており、他依存が実質的に必須として持ち込んでいる状況は確認できていない。

## 調査項目

1. **依存宣言の削除**: `gradle/libs.versions.toml` と `sora-android-sdk/build.gradle.kts` から `kotlin-reflect` の宣言を削除する。
2. **依存グラフの再確認**: 削除後の `releaseRuntimeClasspath` を確認し、`kotlin-reflect` が依存グラフから消えることを確認する。
3. **コード / テスト利用の再確認**: `src/main` / `src/test` / `src/androidTest` 配下で `kotlin.reflect` API を使っていないことを再確認する。
4. **既存検証の実行**: 削除後に既存の build / test / E2E が成立することを確認する。
   - `./gradlew :sora-android-sdk:assembleDebug`
   - `./gradlew :sora-android-sdk:testDebugUnitTest`
   - 実行可能な E2E テストがあれば、その結果を確認する

## 設計方針

- 現時点では `kotlin-reflect` の直接利用箇所が見当たらず、依存グラフ上でも明示依存としてのみ入っているため、まずは削除を前提に作業する。
- 削除後に既存 build / test / E2E で問題が出た場合のみ、必要性を再調査する。
- 削除する場合は `CHANGES.md` の `develop` セクションに `[CHANGE]` エントリを追記する。

## 完了条件

- `gradle/libs.versions.toml` と `sora-android-sdk/build.gradle.kts` から `kotlin-reflect` の宣言が削除されていること。
- `kotlin-reflect` を削除した状態で `:sora-android-sdk:assembleDebug` と `:sora-android-sdk:testDebugUnitTest` が通ること。
- 既存の E2E テストを実行できる場合は、それが通ること。
- `releaseRuntimeClasspath` の依存グラフから `kotlin-reflect` が消えていること。
- `CHANGES.md` の `develop` セクションに該当エントリを追記すること。

## 解決方法
