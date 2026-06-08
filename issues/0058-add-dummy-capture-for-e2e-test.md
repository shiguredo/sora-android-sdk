# androidTest にダミー映像・音声キャプチャを追加し、実機カメラ・マイクなしで e2e テストを実行できるようにする

- Priority: Medium
- Created: 2026-06-08
- Completed:
- Branch: feature/add-dummy-capture-for-e2e-test
- Polished: 2026-06-08
- Model: DeepSeek V4 Pro

## 目的

Android SDK の接続・映像送信・音声送信の動作を、実機のカメラやマイクに依存せずにエミュレータや CI 環境で検証できるようにする。
現在、instrumentation test (androidTest) は存在せず、実機での手動検証に依存している。
テスト基盤としてダミーの `VideoCapturer` を用意し、androidTest を整備する。音声については libwebrtc 本体の `kDummyAudio` を活用する。

## 優先度根拠

- 自動テストの基盤がないことは品質保証上の重大なリスクであるため Medium とする。
- テスト専用ユーティリティであり SDK の公開 API には影響しない。

## 現状

- instrumentation test (androidTest) は存在せず、sourceSet も未設定。
  - `build.gradle.kts:60-67` の `sourceSets` には `main` と `test` のみ定義。
  - `defaultConfig` に `testInstrumentationRunner` が指定されていない。
- ユニットテストはごく少数しか存在しない。
- ダミー `VideoCapturer` 実装は存在しない（issue 0029 でサンプルアプリ向けに PoC が行われ、`VideoCapturer` インターフェース実装によるダミー映像送信が可能なことは確認済み）。
- ダミー `AudioDeviceModule` 実装は存在しない。
- SDK には以下の注入ポイントが既にある：
  - `SoraMediaOption.enableVideoUpstream(capturer, eglContext, cameraConfig)` (`SoraMediaOption.kt:134-143`) で任意の `VideoCapturer` を外部から注入可能。`cameraConfig` が null の場合 `isOwnedCapturer` が false になり、`startCapture()` は呼ばれない（テスト側で制御する想定）。
  - `SoraAudioOption.audioDeviceModule` (`SoraAudioOption.kt:54`) で任意の `AudioDeviceModule` を外部から注入可能。
- これらの注入 API を使えば、SDK 本体のソースコードを変更せずに androidTest 側からダミー実装を差し込める。

## 設計方針

### 全体方針

- ダミー実装はあくまでテスト専用であり、SDK の機能として `src/main` に組み込まない。
- ダミー実装は全て `src/androidTest` sourceSet に配置する。
  - SDK の公開 API にテスト用の enum 値やクラスを追加しない。
  - SDK 本体のソースコード（`src/main`）は変更不要。
- AGENTS.md の「モックやスタブは絶対に利用しないこと」に抵触しないよう、`DummyVideoCapturer` は実際に映像フレームを生成する本物の実装とする。
- `DummyVideoCapturer` の可視性は `internal` とし、テストクラスと同じパッケージ `jp.shiguredo.sora.sdk` から利用できるようにする。

### DummyVideoCapturer

- `org.webrtc.VideoCapturer` インターフェースを実装する。
- 配置場所: `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/DummyVideoCapturer.kt`
- 可視性: `internal`（テストクラスと同一パッケージ `jp.shiguredo.sora.sdk`）
- フレーム生成は `SurfaceTextureHelper` が提供する `Handler` に post する方式で実装する（自前スレッドは立てない）。
- 生成するフレーム: フレーム番号に応じて色相を回転させた単色フレーム（単色だとエンコーダーがスキップフレームとみなす可能性があるため、時間変化させる）。`JavaI420Buffer.allocate(width, height)` で I420 バッファを作成し、`CapturerObserver.onFrameCaptured()` に渡す。
- カメラ権限は不要。
- `CameraVideoCapturer` を実装しないため、`switchCamera()` からの呼び出しは `as?` キャストでスキップされる（意図した動作）。
- `dispose()` はフレーム生成タスクの停止と `SurfaceTextureHelper` が確保したリソースの解放を行う。`isOwnedCapturer = false` の場合 `RTCLocalVideoManager.dispose()` は `capturer.dispose()` を呼ばないため、テストコード側で明示的に呼ぶ。ただし `SurfaceTextureHelper.dispose()` は `RTCLocalVideoManager.dispose()` が行う。

### DummyVideoCapturer 使用時の制約

- `enableVideoUpstream(capturer, eglContext)` に `cameraConfig = null` で渡すと `soraCameraConfig` が null になる。その場合:
  - `setVideoHardMute(false)` を呼んでも `canVideoCapturerControllable`（`SoraMediaOption.kt:49-50`）が false になり、早期 return する。例外は発生しないが、ハードミュート解除のテストはできない。
  - テストでハードミュート解除操作を含む場合は、`cameraConfig` に `SoraCameraConfig()` を渡す必要がある。`captureType` はデフォルトで `DEVICE_CAMERA` になるが、SDK コード内で `captureType` の値に基づく分岐は発生しないため動作に影響しない。
- `createPeerConnectionFactory()` はメインスレッドでの呼び出しが必須（`RTCComponentFactory.kt:61-62`）。androidTest では `@UiThreadTest` または `InstrumentationRegistry.getInstrumentation().runOnMainSync {}` で UI スレッド実行する。
- `EglBase.Context` はテスト開始時に `EglBase.create()` で共有コンテキストを生成し、テスト終了時に `EglBase.release()` で解放する。エミュレータの GPU 設定（Host GPU / SwiftShader）を確認すること。
  - SW エンコードで十分な場合、`eglContext = null` でも動作する。HW エンコードが必要な場合は UI スレッドでの `EglBase.create()` が必須。エミュレータでは SW エンコードを推奨する。

### テストのライフサイクル管理

- `PeerConnectionFactory.initialize()` は static な初期化のため、全テストケースで 1 回だけ呼ばれる。
  - テストの `@Before` で `factory = componentFactory.createPeerConnectionFactory()` を生成し、`@After` で `factory.dispose()` する。`@After` の順序は `@After` アノテーションの逆順（後入れ先出し）で実行される。
- `SoraMediaChannel.connect()` は非同期チェーン（WebSocket → SDP 交換 → ICE 接続）のため、接続成功は `Listener.onConnect()` で待つ:
  ```kotlin
  val connected = CompletableDeferred<Unit>()
  val channel = SoraMediaChannel(..., listener = object : SoraMediaChannel.Listener {
      override fun onConnect(ch: SoraMediaChannel) { connected.complete(Unit) }
      override fun onClose(ch: SoraMediaChannel, e: SoraCloseEvent) {
          connected.completeExceptionally(RuntimeException("closed: ${e.statusCode}"))
      }
      override fun onError(ch: SoraMediaChannel, r: SoraErrorReason, m: String) {
          connected.completeExceptionally(RuntimeException("error: $r $m"))
      }
  })
  channel.connect()
  withTimeout(10_000) { connected.await() }
  ```
- テスト終了時の解放順序（`capturer.dispose()` は `surfaceTextureHelper.dispose()` より先に行う必要があるため）:
  1. `capturer.stopCapture()`
  2. `capturer.dispose()`
  3. `soraMediaChannel.disconnect()`（内部で `surfaceTextureHelper.dispose()` と `source.dispose()` が呼ばれる）

### ダミー音声入力（kDummyAudio）

#### 調査結果（libwebrtc-build パッチ確認済み）

- libwebrtc 本体には `kDummyAudio` が存在するが、shiguredo の AAR には Java ラッパーがない。
  - `webrtc-build/patches/` 以下に `kDummyAudio` 関連のパッチは 0 件。
  - `org.webrtc.audio` パッケージに独自追加された Java ファイルも存在しない。
  - `android_audio_pause_resume.patch` で `JavaAudioDeviceModule` に追加があるのみ。
- 音声ダミー入力を実現するには `webrtc-build` への新規パッチ追加と AAR 再ビルドが必要であり、本 issue のスコープを超える。

#### 本 issue での対応

- 音声ダミー入力は別 issue に分割する。
  - 別 issue では webrtc-build に `kDummyAudio` の Java ラッパーを追加するパッチを作成し、AAR に反映する。
- 本 issue では音声に `initialAudioHardMute = true` を設定し、マイク権限なしで接続する。
  - 音声トラックは生成されるが、マイク入力は行われないため権限不要で動作する。

### build.gradle.kts の変更

- `defaultConfig` に以下を追加する:
  ```kotlin
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  ```
- `sourceSets` に以下を追加する:
  ```kotlin
  getByName("androidTest") {
      java.srcDirs("src/androidTest/kotlin")
  }
  ```
- `dependencies` に以下を追加する。`testBase` バンドルには `kotlin-test-junit` が含まれており androidTest では不要なため、個別に指定する:
  ```kotlin
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
  ```
- テスト接続先用のシグナリング URL を `buildConfigField` で埋め込む:
  ```kotlin
  buildConfigField("String", "TEST_SIGNALING_URL", "\"${System.getenv("SORA_SIGNALING_URL") ?: ""}\"")
  ```
  テストコードは `BuildConfig.TEST_SIGNALING_URL` を参照する。未設定時はテストをスキップする。

### libs.versions.toml の変更

- `androidx.test:runner` と `androidx.test.ext:junit` を Version Catalog に追加する。
  - `androidx-test-runner = "1.6.1"`（既存の `androidx-test-core = "1.6.1"` とバージョンを揃える）
  - `androidx-test-ext-junit = "1.2.1"`

### issue 0052 との関係

- issue 0052 はカメラ以外の入力ソースを SDK 公開 API としてサポートすることを検討する issue であり、本 issue とは方向性が異なる。
- 本 issue の `DummyVideoCapturer` は androidTest 専用の内部実装であり、公開 API ではない。

### AndroidManifest.xml

- `src/androidTest/AndroidManifest.xml` を新規作成する。最小構成は `manifest` 要素のみで、カメラ・マイク権限の宣言は不要。

### テスト戦略

- テストクラス配置場所: `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/`
- `DummyVideoCapturer` + `initialAudioHardMute` で `SoraMediaChannel` に接続し、接続成功と切断正常性を確認する。
- テストクラス名: `SoraE2ETest`。`@RunWith(AndroidJUnit4::class)` を付与する。
- テストは最低限、接続成功を確認する 1 件を含む。
- `SoraMediaChannel` 構築に最低限必要なパラメータ: `channelId`、`role = sendrecv`、`SoraMediaOption`（`enableVideoUpstream(dummyCapturer, eglContext)` と `enableAudioUpstream()` を設定）。
- 映像・音声の送信確認は `PeerConnection.getStats()` で `outbound-rtp` の `bytesSent` または `packetsSent` の増加を観測する。非同期結果のため、最大 10 秒のポーリング（1 秒間隔、最大 10 回）で確認する。
- テスト接続先 Sora サーバーは `BuildConfig.TEST_SIGNALING_URL` で指定する。値は環境変数 `SORA_SIGNALING_URL` から Gradle ビルド時に埋め込む。未設定時は `Assume.assumeTrue(BuildConfig.TEST_SIGNALING_URL.isNotEmpty())` でテストをスキップする。
- テストはエミュレータ（API 35 以降、Google APIs イメージ、Host GPU または SwiftShader）での動作を想定する。

## 完了条件

- `DummyVideoCapturer` が実装され、androidTest から `enableVideoUpstream(capturer, eglContext)` 経由で注入して、指定した解像度・フレームレートでダミー映像が送信できること。
- `kDummyAudio` の利用可否が確認され、shiguredo AAR に Java ラッパーがないことを確認した上で、音声ダミー入力は別 issue に分割すること。
- 本 issue のテストでは `initialAudioHardMute = true` でマイク権限なしの接続を実現すること。
- androidTest がエミュレータ上で実行可能になり、接続テストが通ること。
- テストが実機カメラ・マイク権限を要求しないこと。
- `CHANGES.md` の `### misc` にエントリが追記されていること。

## 変更対象ファイル

- `sora-android-sdk/build.gradle.kts` — `testInstrumentationRunner`、`androidTest` sourceSet、`androidTestImplementation`、`BuildConfig.TEST_SIGNALING_URL` を追加
- `gradle/libs.versions.toml` — `androidx.test:runner` と `androidx.test.ext:junit` を追加
- `sora-android-sdk/src/androidTest/AndroidManifest.xml` — 新規
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/DummyVideoCapturer.kt` — 新規
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt` — 新規
- `CHANGES.md` — `### misc` にエントリを追記

### 別 issue に分割する項目

- **音声ダミー入力の実現** — shiguredo の libwebrtc AAR に `kDummyAudio` の Java ラッパーがないため、`webrtc-build` へのパッチ追加と AAR 再ビルドが必要。別途 issue を立てる。

## 解決方法
