# androidTest にダミー音声入力を追加し、実機マイクなしで e2e 音声テストを実行できるようにする

- Priority: Medium
- Created: 2026-06-09
- Completed:
- Branch: feature/add-dummy-audio-for-e2e-test
- Model: DeepSeek V4 Pro

## 目的

androidTest の E2E テストで、実機のマイクに依存せずに音声送信の動作を検証できるようにする。
現在、映像については issue 0058 で `DummyVideoCapturer` が実装済みだが、音声については `initialAudioHardMute = true` で回避している状態であり、音声送信のテストは行えていない。

## 優先度根拠

- 映像 E2E テスト (issue 0058) が完了した次のステップとして、音声送信の自動テストも必要
- ただし映像テストと比べて優先度はやや低く、Medium とする

## 現状

- issue 0058 の過程で `kDummyAudio` の利用可否を調査した結果、shiguredo の libwebrtc AAR には Java ラッパーが存在しない。
  - `webrtc-build/patches/` 以下に `kDummyAudio` 関連のパッチは 0 件
  - `org.webrtc.audio` パッケージに独自追加された Java ファイルも存在しない
  - `android_audio_pause_resume.patch` で `JavaAudioDeviceModule` に追加があるのみ
- libwebrtc 本体の `kDummyAudio` は C++ 実装として存在するが、Java からの呼び出し経路がない。
- `SoraMediaOption.audioOption.audioDeviceModule` (`SoraAudioOption.kt:54`) で任意の `AudioDeviceModule` を外部注入可能であり、ダミー `AudioDeviceModule` 実装があれば SDK 本体の変更は不要。

## 設計方針

### アプローチ

webrtc-build に以下のパッチを追加し、`kDummyAudio` の Java ラッパーを新規作成する:

1. **Java ラッパークラスの追加**: `org.webrtc.audio.DummyAudioDeviceModule` を新規作成
   - `AudioDeviceModule` インターフェースを実装する
   - 内部で `kDummyAudio` (C++ 側) のインスタンスを保持し、全 ADM メソッドを委譲する
   - コンストラクタで `nativeCreateDummyAudioDeviceModule()` を呼び出し native ポインタを取得

2. **JNI ブリッジの追加**: `kDummyAudio` の C++ 実装を Java から呼び出すための JNI 関数を追加
   - `nativeCreateDummyAudioDeviceModule()` — `webrtc::kDummyAudio` が提供するダミー ADM を生成
   - その他必要な JNI 関数

3. **AAR への反映**: パッチ適用済みの AAR をビルドし、SDK プロジェクトの依存を更新する

### androidTest での利用

- `SoraMediaOption.audioOption.audioDeviceModule = DummyAudioDeviceModule()` で注入
- `enableAudioUpstream()` と組み合わせて音声送信テストを実現
- `initialAudioHardMute = true` は不要になり、実際の音声送信をテスト可能

### build.gradle.kts の変更

- AAR バージョンの更新（パッチ適用済みの新しい AAR に変更）

## 完了条件

- `DummyAudioDeviceModule` が webrtc-build にパッチとして追加され、shiguredo-webrtc-android AAR に含まれていること
- androidTest から `audioDeviceModule = DummyAudioDeviceModule()` 経由で注入してダミー音声が送信できること
- E2E テストで音声送信確認（`getStats()` の outbound-rtp `kind == "audio"` の `bytesSent > 0`）が通ること
- テストが実機マイク権限を要求しないこと

## 変更対象ファイル

- `webrtc-build` リポジトリ:
  - `patches/` 以下に `DummyAudioDeviceModule` の Java クラスと JNI ブリッジを追加するパッチ
- `sora-android-sdk/`:
  - `src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt` — 音声送信確認テストを追加

## 依存関係

- issue 0058 (androidTest 基盤 + DummyVideoCapturer) の完了

## 設計案1: FakeAudioDeviceModule（C++ SineWaveGenerator 内蔵方式）

### 背景調査

**libwebrtc の `kDummyAudio` (`AudioDeviceDummy`) は完全な no-op 実装であり、音声送信テストには使用できない。**

- `AudioDeviceDummy` の全録音系メソッドは `-1` / `false` を返し、`AudioDeviceBuffer` に音声データが一切供給されない
- `StartRecording()` が `-1` を返すため WebRTC の録音パイプラインが停止し、`bytesSent > 0` を達成できない
- 本来の用途は「オーディオデバイス不要なユニットテスト」であり、音声データを必要とする E2E テストには不適

**JS SDK (`sora-js-sdk`) の E2E テストでは、Web Audio API の `OscillatorNode` で 440Hz 正弦波を生成している。**

- `e2e-tests/src/fake.ts` の `createFakeAudioTrack(frequency=440, volume=0.1, stereo=false)`
- ブラウザが `MediaStreamTrack` → エンコーダ間のパイプラインを内部処理するため、SDK 側で `AudioInput` 相当の実装は不要
- Android ではこのパイプラインを自分で構築する必要がある

**libwebrtc には `SineWaveGenerator` (`modules/audio_mixer/sine_wave_generator.h`) が既存し、AAR に含まれている。**

```cpp
class SineWaveGenerator {
 public:
  SineWaveGenerator(float wave_frequency_hz, int16_t amplitude);
  void GenerateNextFrame(AudioFrame* frame);
};
```

これを Java から呼び出す JNI ブリッジは存在しないため、新規追加が必要。

### アーキテクチャ

```
[androidTest]
  mediaOption.audioOption.audioDeviceModule = FakeAudioDeviceModule()
       │
       ▼
[FakeAudioDeviceModule.java]            (新規: webrtc-build パッチ)
  implements AudioDeviceModule
  getNative() → nativeCreateFakeAudioDeviceModule(envRef, freq, vol)
       │
       ▼  JNI
[fake_audio_device_module_jni.cc]       (新規: webrtc-build パッチ)
  FakeAudioInput + FakeAudioOutput を生成
  CreateAudioDeviceModuleFromInputAndOutput() に渡す
       │
       ├─► [FakeAudioInput]             (新規: C++, implements jni::AudioInput)
       │     10ms 周期の専用スレッド
       │     SineWaveGenerator(freq, amp) で正弦波を生成
       │     AudioDeviceBuffer::SetRecordedBuffer() + DeliverRecordedData()
       │
       └─► [FakeAudioOutput]            (新規: C++, implements jni::AudioOutput)
             全メソッド no-op（再生不要）
       │
       ▼
[AndroidAudioDeviceModule]              (既存: libwebrtc)
  AudioInput/AudioOutput を AudioDeviceModule インターフェースに合成
       │
       ▼
[WebRTC エンコーダ] → [RTP] → [Sora Server]
```

- **注入ポイント**: `RTCComponentFactory.kt:121-133` — `audioOption.audioDeviceModule` が非 null ならカスタム ADM が使用される。SDK 本体の変更不要。
- **命名**: libwebrtc の `AudioDeviceDummy` (no-op) との混同を避けるため `Dummy` ではなく `Fake` を使用。JS SDK の `getFakeMedia()` / `createFakeAudioTrack()` とも統一。

### 生成音声パラメータ

| パラメータ | デフォルト値 | JS SDK との一致 |
|-----------|------------|----------------|
| 波形 | 正弦波 (SineWaveGenerator) | ○ OscillatorNode type="sine" |
| 周波数 | 440Hz | ○ |
| 振幅 | 0.1 (full scale 比) | ○ |
| チャンネル | 1 (モノラル) | 0.1 mono |
| サンプルレート | 48kHz | ○ |

Java コンストラクタでパラメータ指定可能：

```kotlin
FakeAudioDeviceModule()                          // デフォルト: 440Hz, 0.1
FakeAudioDeviceModule(frequencyHz = 880)          // 周波数のみ指定
FakeAudioDeviceModule(volume = 0.05)              // 音量のみ指定
FakeAudioDeviceModule(frequencyHz = 880, volume = 0.05)  // 両方指定
```

パラメータは JNI 経由で C++ の `SineWaveGenerator` に渡され、`FakeAudioInput` のコンストラクタで設定される。テストコード側で自由に調整可能。

**振幅値の計算**: `volume` は 0.0〜1.0 の範囲。int16 PCM の最大値 32767 との積で振幅を算出: `amplitude = (int16_t)(32767.0 * volume)`。デフォルト 0.1 では約 3276。

### スレッドモデル

```
FakeAudioInput::StartRecording()
  → 専用スレッド起動 (detach, priority=URGENT_AUDIO)
    → 10ms ループ:
        1. SineWaveGenerator::GenerateNextFrame(audio_frame)  // 10ms 分の正弦波生成
        2. AudioDeviceBuffer::SetRecordedBuffer(pcm, 480, timestamp_ns)
        3. AudioDeviceBuffer::DeliverRecordedData()           // WebRTC パイプラインへ
        4. sleep_until(next_wake_time)

FakeAudioInput::StopRecording()
  → keep_alive_ = false
  → スレッド join
```

`AudioRecordJni` と同様に `AudioDeviceBuffer` を介して WebRTC に音声データを供給する。`AudioRecordJni` が Java 側の `AudioRecord.read()` からデータを受け取るのに対し、`FakeAudioInput` は内部の `SineWaveGenerator` からデータを生成する。

### webrtc-build 変更内容

#### 新規ファイル (6件、パッチで作成)

| # | ファイルパス（`src/` からの相対） | 内容 |
|---|--------------------------------|------|
| 1 | `sdk/android/api/org/webrtc/audio/FakeAudioDeviceModule.java` | `AudioDeviceModule` 実装。`nativeCreateFakeAudioDeviceModule()` JNI 呼び出し |
| 2 | `sdk/android/src/jni/audio_device/fake_audio_input.h` | `jni::AudioInput` 実装、`SineWaveGenerator` 内包、専用スレッド |
| 3 | `sdk/android/src/jni/audio_device/fake_audio_input.cc` | 同上実装 |
| 4 | `sdk/android/src/jni/audio_device/fake_audio_output.h` | `jni::AudioOutput` 実装、全 no-op |
| 5 | `sdk/android/src/jni/audio_device/fake_audio_output.cc` | 同上実装（空） |
| 6 | `sdk/android/src/jni/audio_device/fake_audio_device_module_jni.cc` | JNI 関数: `FakeAudioInput`/`FakeAudioOutput` を生成し `CreateAudioDeviceModuleFromInputAndOutput()` で合成 |

#### BUILD.gn 修正箇所

| ターゲット | 変更 |
|-----------|------|
| `:java_audio_device_module` (line 1368) | `sources` に `fake_audio_input.cc/h`, `fake_audio_output.cc/h` を追加 |
| 新規 `:generated_fake_audio_device_module_jni` | `FakeAudioDeviceModule.java` から JNI ヘッダ自動生成 |
| `:java_audio_device_module_jni` (line 928) | `sources` に `fake_audio_device_module_jni.cc` を追加, `deps` に `:generated_fake_audio_device_module_jni` を追加 |
| `:java_audio_device_module_java` (line 459) | `sources` に `FakeAudioDeviceModule.java` を追加 |

#### run.py 修正

```python
# PATCHES dict の android_sdk に追加:
'android_fake_audio_device.patch',
```

#### 既存の android_audio_pause_resume.patch との関連

`android_audio_pause_resume.patch` は `JavaAudioDeviceModule.java` と `WebRtcAudioRecord.java` を修正する。`FakeAudioDeviceModule` は `JavaAudioDeviceModule` を継承せず、独立した `AudioDeviceModule` 実装として動作する。両パッチに依存関係はなく、共存可能。

### android-sdk 側の変更

| ファイル | 内容 |
|---------|------|
| `SoraE2ETest.kt` | テスト `音声が送信されること` を追加 |
| `gradle/libs.versions.toml` | `libwebrtc` バージョンをパッチ適用後の AAR に更新 |

```kotlin
@Test
fun `音声が送信されること`(): Unit = runBlocking {
    val mediaOption = SoraMediaOption().apply {
        audioOption.audioDeviceModule = FakeAudioDeviceModule()
        enableAudioUpstream()
    }

    val connected = CompletableDeferred<Unit>()
    channel = createChannel(
        mediaOption = mediaOption,
        onConnect = { connected.complete(Unit) },
        onClose = { _, closeEvent ->
            connected.completeExceptionally(RuntimeException("closed: ${closeEvent.code}"))
        },
        onError = { _, reason, message ->
            connected.completeExceptionally(RuntimeException("$reason: $message"))
        },
    )

    channel?.connect()
    withTimeout(10_000) { connected.await() }

    // stats ポーリング: kind=="audio" の outbound-rtp で bytesSent > 0 を確認
    var audioSent = false
    for (i in 1..10) {
        val report = channel?.getStats() ?: continue
        for (stats in report.statsMap.values) {
            if (stats.type != "outbound-rtp") continue
            val kind = (stats.members["kind"] ?: stats.members["mediaType"]) as? String
            if (kind != "audio") continue
            val bytesSent = (stats.members["bytesSent"] as? Number)?.toLong() ?: 0L
            if (bytesSent > 0) {
                audioSent = true
                break
            }
        }
        if (audioSent) break
        delay(1_000)
    }
    assertTrue("音声の outbound-rtp で bytesSent > 0 になること", audioSent)
}
```

### iOS との使いまわし

`FakeAudioInput` は Android の `jni::AudioInput` 抽象クラスに依存しており、iOS のモノリシックな `AudioDeviceIOS` (`VoiceProcessingAudioUnit`) とはアーキテクチャが異なるため、そのままでは使いまわせない。iOS で同等機能を実現する場合は、`RTCAudioDevice` プロトコルを実装して `ObjCAudioDeviceModule` 経由で組み込む別設計が必要。

一方、`SineWaveGenerator` 自体はプラットフォーム非依存であり、10ms PCM 生成ロジック（位相累積による正弦波計算）は iOS 側でも再利用可能。

### 変更対象ファイル

- `webrtc-build` リポジトリ:
  - `patches/android_fake_audio_device.patch` — 上記 6 ファイル + BUILD.gn 修正
  - `run.py` — PATCHES 辞書にパッチ追加
- `sora-android-sdk/`:
  - `src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETest.kt` — 音声送信確認テストを追加
  - `gradle/libs.versions.toml` — AAR バージョン更新

## 解決方法
