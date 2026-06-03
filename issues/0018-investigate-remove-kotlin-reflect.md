# 不要なら kotlin-reflect 依存を外す

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## 目的

`kotlin-reflect` への依存が本当に必要かを調査し、不要であれば依存を削除する。不要な依存を取り除くことでライブラリの依存数と配布物のサイズを削減する。

## 優先度根拠

- 依存を外しても動作することが簡易な確認では判明しているが、本番動作に影響しないことを確実に検証する必要がある。
- 機能追加やバグ修正ではなく依存の整理であり、緊急性は低いため Low とする。

## 現状

`kotlin-reflect` は以下で依存として宣言されている。

- `gradle/libs.versions.toml`: `kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect" }`
- `sora-android-sdk/build.gradle.kts`: `implementation(libs.kotlin.reflect)`

`src` 配下のコードに `kotlin.reflect` API（`KClass` のメンバー走査など）を直接利用している箇所は見当たらない。シグナリングの JSON シリアライズ / デシリアライズには Gson を利用しているが、Gson は Java のリフレクションを使用しており `kotlin-reflect` を必要としない。簡易な確認では依存を外しても動作した。

## 設計方針

- `kotlin-reflect` が実行時に必要となる箇所が本当に存在しないかを確認する。Gson によるデータクラスのシリアライズ / デシリアライズ、ProGuard / R8 による難読化後の挙動を含めて検証する。
- 不要と確認できたら、`gradle/libs.versions.toml` と `sora-android-sdk/build.gradle.kts` から `kotlin-reflect` の宣言を削除する。

## 完了条件

- `kotlin-reflect` を削除した状態でビルドと全テストが通ること。
- シグナリングの JSON シリアライズ / デシリアライズを含む既存機能が、難読化後も含めて正しく動作すること。
- 削除する場合は `CHANGES.md` の `develop` セクションに該当エントリを追記すること。

## 解決方法
