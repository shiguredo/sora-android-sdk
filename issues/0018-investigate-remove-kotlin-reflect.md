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

## 調査項目

1. **推移的依存の有無**: `kotlinx-coroutines-android` や他の依存が `kotlin-reflect` を推移的に取り込んでいないか。`./gradlew :sora-android-sdk:dependencies --configuration releaseRuntimeClasspath` で確認する。
   - 推移的依存がある場合、明示的な `implementation` 宣言を削除しても依存グラフから消えない。その場合は `exclude` の要否を判断する。
2. **テストコードの調査**: `src/test` 配下で `kotlin.reflect` が `testImplementation` として利用されていないかも確認する。
3. **Gson 使用箇所の精査**: コードベース内の全 Gson 使用箇所（`GsonBuilder` 設定を含む）で `kotlin-reflect` への暗黙的依存がないことを確認する。
4. **R8 難読化後の検証**: release ビルドで R8 を適用した状態で、以下のシナリオが正しく動作することを実機または Robolectric テストで確認する。
   - `Sora.connect()` → `Sora.disconnect()` の基本接続サイクル
   - シグナリング `type: connect` / `type: offer` / `type: answer` の全メッセージラウンドトリップ
   - DataChannel の送受信

## 設計方針

- 上記調査で `kotlin-reflect` が不要と確認できたら、`gradle/libs.versions.toml` と `sora-android-sdk/build.gradle.kts` から宣言を削除する。
- 削除する場合は `CHANGES.md` の `develop` セクションに `[CHANGE]` エントリを追記する。

## 完了条件

- `kotlin-reflect` を削除した状態でビルドと全テストが通ること。
- シグナリングの JSON シリアライズ / デシリアライズを含む既存機能が R8 難読化後も正しく動作すること。
- 削除前後で AAR に含まれるクラス数または AAR サイズの差分を計測し、削除に意味があることを確認すること。
- `CHANGES.md` の `develop` セクションに該当エントリを追記すること。

## 解決方法
