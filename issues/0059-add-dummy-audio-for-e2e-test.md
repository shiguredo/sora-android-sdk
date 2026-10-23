# androidTest にダミー音声入力を追加し、実機マイクなしで e2e 音声テストを実行できるようにする

- Priority: Medium
- Created: 2026-06-09
- Completed:
- Branch: feature/add-dummy-audio-for-e2e-test
- Polished: 2026-09-17
- Model: DeepSeek V4 Pro

## 目的

androidTest の E2E テストで、実機のマイクに依存せずに音声送信の動作を検証できるようにする。
現在、映像については issue 0058 の `DummyVideoCapturer` と映像送信確認（現行の `SoraStatsE2ETest` に集約）まで実装済みだが、音声については音声 upstream を使うテストが存在せず、音声送信の自動テストは行えていない。

## 優先度根拠

- 映像 E2E テスト (issue 0058) が完了した次のステップとして、音声送信の自動テストも必要
- ただし映像テストと比べて優先度はやや低く、Medium とする

## 現状

- issue 0058 の実装過程で、`enableAudioUpstream()` と `initialAudioHardMute = true` を併用した構成でも `AUDIO_RECORD_INIT_ERROR` が発生したため、現行の E2E テスト（`SoraStatsE2ETest` 等）は音声 upstream を一切使っていない（issue 0058 の「実装過程で判明した知見」の「音声 upstream なしで映像テスト」参照）。
- shiguredo-webrtc-android AAR（`gradle/libs.versions.toml` の `libwebrtc` 相当、現行 `150.7871.3.0`）を照合した結果、`org.webrtc.audio` パッケージにダミー ADM の Java ラッパーは存在しない。存在するのは `AudioDeviceModule` インターフェースと `JavaAudioDeviceModule`（およびその補助クラス）のみ。
  - shiguredo-webrtc-build/webrtc-build の `patches/` にもダミー ADM 関連のパッチは存在しない。音声関連の既存パッチは `android_audio_pause_resume.patch` と `android_audio_track_sink.patch` のみ。
- **`kDummyAudio` はダミー音声入力には使えない。** libwebrtc の `webrtc::AudioDeviceModule::kDummyAudio`（`AudioDeviceDummy`）は音声デバイスを無効化するためのスタブであり、`InitRecording()` / `StartRecording()` が -1 を返し、録音データを一切供給しない。sora-cpp-sdk の `use_audio_device = false`（momo の `--no-audio-device`）と同じ「音声なし」用途のため、これを Java から呼べるようにしても音声 RTP は生成されない。
- 音声入力のダミー化には、libwebrtc 側にオーディオデータ（無音またはトーン）を生成して `AudioDeviceBuffer` へ供給する ADM 実装を新規追加する必要がある。参考実装として、momo の `FakeAudioCapturer`（`momo/src/rtc/fake_audio_capturer.cpp`。スレッドで 10 ms ごとに PCM を生成し、`AudioDeviceBuffer.SetRecordedBuffer()` + `DeliverRecordedData()` で供給する）がある。
- `SoraAudioOption.audioDeviceModule`（`SoraAudioOption.kt` の `audioDeviceModule` プロパティ、`SoraMediaOption.audioOption.audioDeviceModule` 経由）で任意の `AudioDeviceModule` を外部注入可能であり、SDK 本体（`src/main`）の変更なしにカスタム ADM を差し込める。`RTCComponentFactory.createPeerConnectionFactory()` が `PeerConnectionFactory.Builder.setAudioDeviceModule()` に渡す。
  - `audioDeviceModule` が非 null の場合、`initialAudioHardMute` と `useHardwareAcousticEchoCanceler` / `useHardwareNoiseSuppressor` の設定は無視される（`SoraAudioOption.kt` の KDoc 記載どおり）。

## 設計方針

### アプローチ

webrtc-build に以下のパッチを追加し、音声入力を生成するダミー入力 ADM を新規作成する（`kDummyAudio` は利用しない）。AGENTS.md の「モックやスタブは絶対に利用しないこと」に照らしても、無音またはトーンの PCM を実生成して AudioDeviceBuffer へ供給する実動する ADM 実装とする。

1. **C++ 側のダミー入力デバイス**: `AudioDeviceGeneric` を実装するクラスを新規追加する
   - 専用スレッドで 10 ms ごとに PCM（無音またはトーン）を生成し、`AudioDeviceBuffer.SetRecordedBuffer()` + `DeliverRecordedData()` で供給する
   - `InitRecording()` / `StartRecording()` は成功を返し、`Recording()` は生成中に true を返す
   - 参考: momo の `FakeAudioCapturer`
2. **JNI ブリッジ**: `webrtc::Environment` を受け取り、上記デバイスを持つ `AudioDeviceModule` を生成して Java へ返す JNI 関数（`nativeCreateDummyAudioDeviceModule(long webrtcEnvRef)` 相当）を追加する
   - Java 側の `AudioDeviceModule.getNative(long webrtcEnvRef)` は `webrtc::Environment` のポインタを渡す仕様のため、ADM の生成は Environment を取得した後（`getNative()` 呼び出し時）に行う
   - `AudioDeviceModuleImpl` には `Environment` を受け取るコンストラクタと、`AudioDeviceGeneric` + `create_detached` を受け取るテスト用コンストラクタが用意されており、この経路でダミー入力デバイスを組み込む
3. **Java ラッパークラスの追加**: `org.webrtc.audio.DummyAudioDeviceModule` を新規作成する
   - `AudioDeviceModule` インターフェースを実装する
   - `getNative(long webrtcEnvRef)` で native ポインタを取得（初回のみ生成し、以後キャッシュ。`JavaAudioDeviceModule` と同じパターン）
   - `release()` は `JniCommon.nativeReleaseRef` で解放する
   - `setSpeakerMute()` / `setMicrophoneMute()` 等は no-op とする（実デバイスがないため）
4. **ビルド登録**: 追加したソースを `sdk/android` および `modules/audio_device` のビルド定義へ登録する
5. **AAR への反映**: パッチ適用済みの AAR をビルドして shiguredo-webrtc-android リポジトリへリリースし、SDK プロジェクトの依存を更新する

### androidTest での利用

- `SoraMediaOption.audioOption.audioDeviceModule = DummyAudioDeviceModule()` で注入
- `enableAudioUpstream()` と組み合わせて音声送信テストを実現
- `initialAudioHardMute = true` は不要になる（カスタム ADM 設定時は無視される）+ 実際の音声送信をテスト可能

### 依存の更新

- `gradle/libs.versions.toml` の `libwebrtc`（現行 `150.7871.3.0`）を、パッチ適用済みの新しい shiguredo-webrtc-android AAR のバージョンへ更新する
  - なお webrtc-build には M154 系の master（VERSION `154.8037.1.0`）と M150 系の `feature/m150.7871` ブランチが存在するため、次回の AAR 更新で libwebrtc が M154 系へ上がるかは webrtc-build 側のリリースに依存する

## 完了条件

- ダミー入力 ADM（C++ 実装 + JNI + `org.webrtc.audio.DummyAudioDeviceModule`）が webrtc-build のパッチとして追加され、shiguredo-webrtc-android AAR に含まれていること
- `gradle/libs.versions.toml` の `libwebrtc` をパッチ適用済みの AAR へ更新し、SDK 本体（`src/main`）の変更なしに `SoraMediaOption.audioOption.audioDeviceModule` へ `DummyAudioDeviceModule` を設定できること
- 注入後の動作検証（`AUDIO_RECORD_INIT_ERROR` がないこと・マイク権限を要求しないこと・音声送信確認）は issue 0074 の E2E テスト（`getStats()` の outbound-rtp `kind == "audio"` の `bytesSent > 0` 確認）で行う

## 変更対象ファイル

- `shiguredo-webrtc-build/webrtc-build` リポジトリ:
  - `patches/` 以下にダミー入力 ADM のパッチ（C++ 実装 + JNI + `org.webrtc.audio.DummyAudioDeviceModule` + ビルド登録）
- `sora-android-sdk/`:
  - `gradle/libs.versions.toml` — `libwebrtc` のバージョン更新
- 注意: 現行の `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/` は `SoraE2ETestBase.kt` + シナリオ別テストクラス（`SoraStatsE2ETest.kt` 等）の構成であり、`SoraE2ETest.kt` という単一ファイルは存在しない。音声送信確認テストの追加は issue 0074 で行う

## 依存関係

- issue 0058 (androidTest 基盤 + DummyVideoCapturer) の完了
- 関連: issue 0074 (音声のみ送信の E2E テスト追加。本 issue のダミー音声を利用する前提)
- 関連: issue 0076 (ステレオ音声送受信の E2E。本 issue のダミー音声が必要で、0059 完了後に着手)

## 解決方法
