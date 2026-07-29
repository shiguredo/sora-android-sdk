# デフォルトの AudioDeviceModule を JavaAudioDeviceModule から AAudio へ移行するか検討する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## pending 理由

minSdk の引き上げ（21 から 26 へ）が必要になること、および AAudio の `AudioDeviceModule` を Java から生成する API が libwebrtc 側に用意されていないことという重いブロッカーがあるため pending とする。これらのブロッカーが解消されない限り着手できない。

## 目的

デフォルトで生成される `AudioDeviceModule` を `JavaAudioDeviceModule` から AAudio ベースのものへ移行するかどうかを検討する。

AAudio はより低レイテンシーで新しい音声 API であり、音声の遅延低減が期待できるため、移行の是非と必要な前提条件を整理する。

## 優先度根拠

- 音声遅延の低減は利点だが、現状の `JavaAudioDeviceModule` でも配信は問題なく動作しており、緊急性はない。
- minSdk の引き上げや libwebrtc 側 API の不在という重いブロッカーがあり、すぐに着手できないため Low とする。

## 現状

- デフォルトの `AudioDeviceModule` は `RTCComponentFactory.kt` の `createJavaAudioDevice()` で `org.webrtc.audio.JavaAudioDeviceModule` として生成される。
  - `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/RTCComponentFactory.kt`
  - 外部から `SoraAudioOption.audioDeviceModule` が指定されている場合はそちらが利用され、指定がない場合のみデフォルトの `JavaAudioDeviceModule` が生成される。
- 本 SDK の `minSdk` は 21 である（`compileSdk` / `targetSdk` は 36）。
  - `gradle/libs.versions.toml`（`minSdk = "21"`）
  - `sora-android-sdk/build.gradle.kts`
- AAudio に関する制約は以下のとおり。
  - AAudio は Android SDK 26 以降で利用可能であり、AAudio へ移行する場合は `minSdk` を 26 へ引き上げる必要がある。
  - libwebrtc 側で AAudio サポートをビルドに含めるには GN フラグ `rtc_enable_android_aaudio` を有効化する必要があり、有効化すると `WEBRTC_AUDIO_DEVICE_INCLUDE_ANDROID_AAUDIO` が定義されて AAudio 向け実装がリンクされる。
  - libwebrtc 側のデフォルトは `JavaAudioDeviceModule` である。AAudio を使う場合は JNI 経由で `CreateAAudioAudioDeviceModule()` を呼ぶ必要があり、`JavaAudioDeviceModule` のように Java から直接生成するメソッドが libwebrtc 側に用意されていない。対応時にも用意されていない場合は、Java 側のラッパーを別途作成する必要がある。

## 設計方針

- AAudio へ移行する利点（低レイテンシー）と、移行に伴うコスト（`minSdk` 引き上げによる対応端末の縮小、libwebrtc 側のビルド設定変更、Java ラッパーの作成）を比較し、移行の是非を判断する。
- 移行する場合の前提条件を整理する。
  - libwebrtc のビルドで `rtc_enable_android_aaudio` を有効化する。
  - `minSdk` を 26 へ引き上げる（後方互換のない変更となる）。
  - AAudio の `AudioDeviceModule` を Java から生成するためのラッパーを用意する。
- デフォルトを切り替えるのか、オプションで AAudio を選択可能にするに留めるのかも検討する。

## 完了条件

- AAudio へ移行するかどうかの方針が決定されていること。
- 移行する場合は、必要な前提条件（`minSdk` 引き上げ、libwebrtc のビルド設定、Java ラッパーの要否）が整理されていること。

## 解決方法
