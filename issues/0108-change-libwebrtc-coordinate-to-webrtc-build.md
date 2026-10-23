# libwebrtc の依存座標を webrtc-build の JitPack 配布に移行する

- Priority: Medium
- Created: 2026-09-28
- Completed: {YYYY-MM-DD}
- Branch: feature/change-libwebrtc-coordinate
- Polished: 2026-09-28

## 目的

libwebrtc の AAR の配布元を `shiguredo/shiguredo-webrtc-android` の JitPack 座標 (`com.github.shiguredo:shiguredo-webrtc-android`) から、 `shiguredo-webrtc-build/webrtc-build` の JitPack 座標 (`com.github.shiguredo-webrtc-build:webrtc-build`) に移行する。

webrtc-build の `m155.8059.1.1` (2026-09-28 公開) で Release の成果物に `libwebrtc.aar` が直接追加され、 JitPack でも AAR が公開されるようになった (`jitpack.yml` と `scripts/prepare_aar.sh` は 2026-09-25 に追加)。これにより libwebrtc の更新ごとに AAR を `shiguredo-webrtc-android` へ転載する作業が不要になり、 更新の作業を webrtc-build に一本化できる。`shiguredo/shiguredo-webrtc-android` は移行完了後に archived にする予定である。

webrtc-build リポジトリ側にも同じ移行を追跡する [issues/0032-change-sora-android-sdk-libwebrtc-coordinate.md](https://github.com/shiguredo-webrtc-build/webrtc-build/blob/m155.8059.1.1/issues/0032-change-sora-android-sdk-libwebrtc-coordinate.md) が存在する (同 issue の対象も Sora Android SDK 側の変更であり、 実装は本 issue が担当する)。

## 優先度根拠

- 現行の依存先 `shiguredo-webrtc-android` は archived になる予定であり、 今後の libwebrtc 更新を受け取るための経路を確保する必要がある。
- issues/0101 で扱う UAF 修正の取り込みは、 本移行の完了後に webrtc-build の新しいリリースを参照する形になるため、 本移行が前提になる。
- 差し迫った不具合修正ではなく、 SDK の利用者への影響もビルド環境の更新が中心のため Medium とする。

## 現状

- `gradle/libs.versions.toml` の `[versions].libwebrtc` は `151.7922.0.0`、 `[libraries].shiguredo-webrtc-android` は `com.github.shiguredo:shiguredo-webrtc-android` を参照している。
- `sora-android-sdk/build.gradle.kts` は `api(libs.shiguredo.webrtc.android)` で AAR を取り込み、 `BuildConfig.LIBWEBRTC_VERSION` に `libs.versions.libwebrtc` の値をそのまま埋め込んでいる。`BuildConfig.LIBWEBRTC_VERSION` は `SDKInfo.libwebrtcInfo()` で connect メッセージの `libwebrtc` フィールドに使われる。
- JitPack の新しい座標では `m155.8059.1.1` の AAR が取得できることを確認済みである (<https://jitpack.io/#shiguredo-webrtc-build/webrtc-build>)。
- webrtc-build の JitPack 公開は `jitpack.yml` を含む `m155.8059.1.1` 以降のタグが対象であり、 それ以前のタグ (m150/m151 など) は JitPack でビルドされていない。このため座標の切り替えには libwebrtc のバージョン更新 (151.7922.0.0 → m155.8059.1.1) が伴う。
  - `m155.8059.1.1` の `WEBRTC_COMMIT` は `c4f21b1f91386bae6d710540976dff5aae67b3c5` (`M155.8059@{#1}`) である。
- `m155.8059.1.1` の AAR と `shiguredo-webrtc-android` 151.7922.0.0 の AAR を比較した結果は次のとおり。
  - `AndroidManifest.xml` (minSdk 21) と JNI (`jni/arm64-v8a/libjingle_peerconnection_so.so`) の構成は同じ。
  - `META-INF/NOTICE` (WebRTC のライセンス) が追加されている。
  - `classes.jar` にクラスの削除はなく、 `org.jni_zero.*` などが追加されている。
  - `classes.jar` の class file は version 69 (Java 25) で作られている (旧 AAR は version 65 (Java 21))。
- class file version 69 のため、 JDK 21 で `./gradlew build` を実行すると `:sora-android-sdk:compileDebugUnitTestJavaWithJavac` が `class file version 69.0 ... should be 65.0` で失敗する。JDK 25 では `./gradlew build` が成功する。`assembleDebugAndroidTest` と `dokkaGenerateHtml` は JDK 21 でも成功する (いずれも `m155.8059.1.1` を一時的に指定して確認)。Android SDK Build-Tools 37.0.0 の D8 (R8 9.2.4-dev) では `classes.jar` を dex 変換できることも確認済みである。
- `.github/workflows/build.yml` と `.github/workflows/deploy-api-docs.yml` の JDK 21 は、 `shiguredo-webrtc-android` の `classes.jar` が Java 21 の class file を含むことを理由に固定しており、 コメントもその前提になっている。
- `SDKInfo.libwebrtcInfo()` は `"Shiguredo-build " + WebrtcBuildVersion.webrtc_branch + " (" + BuildConfig.LIBWEBRTC_VERSION + " " + revision + ")"` の形式で、 現行は `Shiguredo-build M151 (151.7922.0.0 f20ebb8)` を送信する。`m` 付きの値をそのまま使うと `Shiguredo-build M155 (m155.8059.1.1 c4f21b1)` となり、 sora-ios-sdk が送信する `Shiguredo-build M155 (155.8059.1.1 c4f21b1)` とずれる。

## 設計方針

- `gradle/libs.versions.toml` のライブラリ定義を `webrtc-build = { module = "com.github.shiguredo-webrtc-build:webrtc-build", version.ref = "libwebrtc" }` に変更し、 `[versions].libwebrtc` を `m155.8059.1.1` にする。エイリアス名の変更に伴い `sora-android-sdk/build.gradle.kts` の依存指定を `api(libs.webrtc.build)` に更新する。
- `BuildConfig.LIBWEBRTC_VERSION` には先頭の `m` を除いた `155.8059.1.1` を埋め込み、 `SDKInfo.libwebrtcInfo()` の文字列形式を維持する (例: `libs.versions.libwebrtc.get().removePrefix("m")`)。
- ビルド JDK を 25 に更新する。
  - `.github/workflows/build.yml` は単体テストの Java コンパイルがあるため必須である。
  - `.github/workflows/e2e-test.yml` と `.github/workflows/deploy-api-docs.yml` は JDK 21 でもタスクは成功するが、 ワークフロー間で JDK を揃えるため 25 に更新する。
  - `jitpack.yml` も `openjdk25` に更新する。JitPack が `openjdk25` を解決できない場合は SDKMAN (`sdk install java 25-open`) での導入に切り替える。
- 利用側アプリの R8 を有効にしたビルドを確認し、 新しい AAR の class file version による影響が利用者に及ぶ場合は `CHANGES.md` に記載する。
- `README.md` の libwebrtc バッジ (`151.7922` / `branch-heads/7922`) と `skills/sora-android-sdk/SKILL.md` の依存ライブラリの記載を新しい座標とバージョンに合わせる。`skills/sora-android-sdk/SKILL.md` のビルド・テストに関する記述には、 `./gradlew build` に JDK 25 が必要な旨を追記する。
- `CHANGES.md` の `develop` に依存座標の変更、 libwebrtc の更新、 ビルドに JDK 25 が必要になる点を記載する。
- issues/0101 が扱う libwebrtc の更新は、 本 issue の完了後に webrtc-build の座標でバージョンを上げる形にし、 `shiguredo-webrtc-android` への AAR の登録は行わない。

## 完了条件

- `com.github.shiguredo-webrtc-build:webrtc-build:m155.8059.1.1` を参照して JDK 25 で `./gradlew build` が成功すること。
- GitHub Actions の build が JDK 25 で成功すること。
- E2E テスト (pixelApi35 の Gradle Managed Device) が成功すること。
- connect メッセージの `libwebrtc` フィールドが先頭の `m` を含まない `Shiguredo-build M155 (155.8059.1.1 c4f21b1)` の形式で送信されること。
- `./gradlew publishToMavenLocal` で生成される POM が新しい座標を参照し、 リリース時に JitPack で Sora Android SDK の AAR が公開できること。
- 利用側アプリの R8 を有効にしたビルドを確認し、 新しい AAR の class file version による影響の有無と確認結果を記録していること (影響が利用者に及ぶ場合は `CHANGES.md` に記載すること)。
- `README.md` と `skills/sora-android-sdk/SKILL.md` の記載が新しい座標とバージョンになっていること。
- `CHANGES.md` に変更内容が記載されていること。

## 変更対象ファイル

- `gradle/libs.versions.toml`
- `sora-android-sdk/build.gradle.kts`
- `.github/workflows/build.yml`
- `.github/workflows/e2e-test.yml`
- `.github/workflows/deploy-api-docs.yml`
- `jitpack.yml`
- `README.md`
- `skills/sora-android-sdk/SKILL.md`
- `CHANGES.md`

## 解決方法
