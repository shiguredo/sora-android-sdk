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

#### 既存パッチとの関連

- `android_audio_pause_resume.patch`: `JavaAudioDeviceModule.java` と `WebRtcAudioRecord.java` を修正する。本パッチは新規ファイル作成のみでこれらのファイルに干渉しない。依存関係はなく共存可能。
- `BUILD.gn` 修正: 本パッチが修正する行 (~459, ~928, ~1368) は、BUILD.gn を触る既存パッチ4件 (`android_webrtc_version`, `android_proxy`, `android_simulcast`, `android_audio_track_sink`) の修正行 (~52, ~121, ~154, ~290, ~320, ~588, ~788, ~1055, ~1535) と重複しない。適用順序に依存せず単独で機能する。

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

## 設計案2: ExternalAudioDeviceModule（Java 側から PCM 注入方式）

### 設計案1 との対比

| | 設計案1 (Fake) | 設計案2 (External) |
|---|---|---|
| 音声生成場所 | C++ 内蔵(SineWaveGenerator) | Java/Kotlin 側(任意) |
| 供給タイミング | C++ 専用スレッド 10ms 自動 | Java 側が `write()` を呼ぶ |
| 音源種別 | 正弦波 440Hz のみ | 任意(WAV/合成音/処理後音声) |
| 内部バッファ | 不要(C++ で直接生成) | 要(Java-C++ 間のリングバッファ) |
| 複雑さ | 低 | 中 |
| iOS 転用 | 不可(`jni::AudioInput` 依存) | 不可(同様) |

### アーキテクチャ

```
[androidTest / アプリ]
  val adm = ExternalAudioDeviceModule(sampleRate=48000, channels=1)
  mediaOption.audioOption.audioDeviceModule = adm
  connect()
  // 別スレッドで音声供給
  launch(Dispatchers.IO) {
      while (recording) {
          val pcm = generateAudioFrame()  // 任意の音声生成
          adm.write(pcm, frames)
          delay(10)
      }
  }
       │
       ▼
[ExternalAudioDeviceModule.java]          (新規: webrtc-build パッチ)
  implements AudioDeviceModule
  write(ByteBuffer pcm, int numFrames)     // PCM データを注入（スレッドセーフ）
  getNative() → nativeCreate(envRef, sampleRate, channels)
       │
       ▼  JNI
[external_audio_device_module_jni.cc]     (新規: webrtc-build パッチ)
  ExternalAudioInput + ExternalAudioOutput を生成
  CreateAudioDeviceModuleFromInputAndOutput() に渡す
       │
       ├─► [ExternalAudioInput]           (新規: C++, implements jni::AudioInput)
       │     内部スレッド (10ms 周期)
       │     PaUtilRingBuffer (lock-free SPSC, modules/third_party/portaudio/)
       │     Java write() ──JNI──→ ring_buffer.Write()
       │     専用スレッド ──→ ring_buffer.Read() ──→ AudioDeviceBuffer::DeliverRecordedData()
       │     アンダーラン時は無音を自動供給
       │
       └─► [ExternalAudioOutput]          (新規: C++, implements jni::AudioOutput)
             全メソッド no-op（再生不要）
       │
       ▼
[AndroidAudioDeviceModule]                (既存: libwebrtc)
  AudioInput/AudioOutput を AudioDeviceModule インターフェースに合成
       │
       ▼
[WebRTC エンコーダ] → [RTP] → [Sora Server]
```

### データ供給方式: リングバッファ介在

`AudioRecordJni` は Java 側が `AudioRecord.read()` の一定周期でデータを供給する前提だが、外部注入では Java 側の書き込みタイミングが不規則になる可能性がある。このため libwebrtc に既存の `PaUtilRingBuffer`（`modules/third_party/portaudio/pa_ringbuffer.h`、ロックフリー SPSC）で Java 書き込みスレッドと C++ 読み取りスレッドを分離する。

- Java `write()`: リングバッファに書き込み。満杯時は古いデータを上書き（ノンブロッキング）
- C++ 専用スレッド: 10ms 周期でリングバッファを読み取り。空時は無音を供給
- `PaUtilRingBuffer` は `AudioDeviceMac` で macOS Core Audio コールバックとの同期に実績あり

| リングバッファパラメータ | デフォルト | 説明 |
|------------------------|-----------|------|
| バッファ長 | 200ms | = sampleRate * channels * 0.2 要素 |
| 要素サイズ | channels * 2 bytes | int16 PCM |
| 型 | lock-free SPSC | `PaUtilRingBuffer` |
| 書き込みブロック | なし | 満杯時は上書き |
| 読み取りブロック | なし | 空時は無音(ゼロ)出力 |

### Java API

```java
public class ExternalAudioDeviceModule implements AudioDeviceModule {
    /**
     * @param sampleRate    サンプルレート (e.g. 48000)
     * @param channels      チャンネル数 (1 or 2)
     * @param bufferSizeMs  リングバッファサイズ (default: 200ms)
     */
    public ExternalAudioDeviceModule(int sampleRate, int channels);
    public ExternalAudioDeviceModule(int sampleRate, int channels, int bufferSizeMs);

    /**
     * 音声 PCM データを注入する。スレッドセーフ。
     * @param pcm       direct ByteBuffer, int16 PCM, interleaved
     * @param numFrames フレーム数 (sampleRate/100 = 10ms 推奨)
     * @return 実際に書き込まれたフレーム数（0 の場合はリングバッファ満杯）
     */
    public int write(ByteBuffer pcm, int numFrames);

    @Override public long getNative(long webrtcEnvRef);
    @Override public void release();
    @Override public void setSpeakerMute(boolean mute);    // no-op
    @Override public void setMicrophoneMute(boolean mute); // no-op
}
```

### webrtc-build 変更内容

#### 新規ファイル (6件、パッチで作成)

| # | ファイルパス（`src/` からの相対） | 内容 |
|---|--------------------------------|------|
| 1 | `sdk/android/api/org/webrtc/audio/ExternalAudioDeviceModule.java` | `AudioDeviceModule` 実装。`write()` で PCM 注入 |
| 2 | `sdk/android/src/jni/audio_device/external_audio_input.h` | `jni::AudioInput` 実装、`PaUtilRingBuffer` 内包、専用読み取りスレッド |
| 3 | `sdk/android/src/jni/audio_device/external_audio_input.cc` | 同上実装 |
| 4 | `sdk/android/src/jni/audio_device/external_audio_output.h` | `jni::AudioOutput` 実装、全 no-op |
| 5 | `sdk/android/src/jni/audio_device/external_audio_output.cc` | 同上実装（空） |
| 6 | `sdk/android/src/jni/audio_device/external_audio_device_module_jni.cc` | JNI 関数: `ExternalAudioInput`/`ExternalAudioOutput` を生成し `CreateAudioDeviceModuleFromInputAndOutput()` で合成 |

#### BUILD.gn 修正箇所

| ターゲット | 変更 |
|-----------|------|
| `:java_audio_device_module` (line 1368) | `sources` に `external_audio_input.cc/h`, `external_audio_output.cc/h` を追加。`deps` に `../../modules/third_party/portaudio:portaudio` を追加 |
| 新規 `:generated_external_audio_device_module_jni` | `ExternalAudioDeviceModule.java` から JNI ヘッダ自動生成 |
| `:java_audio_device_module_jni` (line 928) | `sources` に `external_audio_device_module_jni.cc` を追加, `deps` に `:generated_external_audio_device_module_jni` を追加 |
| `:java_audio_device_module_java` (line 459) | `sources` に `ExternalAudioDeviceModule.java` を追加 |

#### 既存パッチとの関連

設計案1 と同様、新規ファイル作成のみのため既存パッチ (`android_audio_pause_resume` 他) と干渉しない。BUILD.gn 修正行も既存パッチの修正行と重複なし。単独で機能する。

### E2E テストでの使用例

```kotlin
@Test
fun `音声が送信されること`(): Unit = runBlocking {
    val adm = ExternalAudioDeviceModule(sampleRate = 48000, channels = 1)

    val mediaOption = SoraMediaOption().apply {
        audioOption.audioDeviceModule = adm
        enableAudioUpstream()
    }

    channel = createChannel(mediaOption = mediaOption, ...)
    channel?.connect()
    withTimeout(10_000) { connected.await() }

    // 440Hz 正弦波を 10ms ごとに供給（設計案2 ではユーザー側で生成）
    val framesPer10ms = 480  // 48000 / 100
    val buffer = ByteBuffer.allocateDirect(framesPer10ms * 2)
    val phaseStep = 2.0 * Math.PI * 440.0 / 48000.0
    var phase = 0.0

    launch(Dispatchers.Default) {
        while (isActive) {
            val shortBuf = buffer.asShortBuffer()
            for (i in 0 until framesPer10ms) {
                shortBuf.put(i, (32767.0 * 0.1 * sin(phase)).toInt().toShort())
                phase += phaseStep
            }
            adm.write(buffer, framesPer10ms)
            delay(10)
        }
    }

    // stats ポーリングで bytesSent > 0 確認（設計案1 と同一）
    // ...
}
```

### 両案の使い分け

| | 設計案1 (Fake) | 設計案2 (External) |
|---|---|---|
| 音声生成 | C++ 内蔵 `SineWaveGenerator` | Java/Kotlin 側で任意生成 |
| API | コンストラクタのみ | コンストラクタ + `write(ByteBuffer, frames)` |
| スレッド | C++ 専用スレッド（自動） | Java 書き込み + C++ 読み取り（二要） |
| 内部バッファ | 不要（都度生成） | `PaUtilRingBuffer`（200ms デフォルト） |
| 音源 | 正弦波 440Hz のみ | 任意（WAV / 合成音 / 処理後音声） |
| 複雑さ | 低 | 中 |
| webrtc-build 変更 | 6 ファイル + BUILD.gn | 6 ファイル + BUILD.gn |
| テストコード量 | 1 行で完結 | 正弦波生成ループが必要 |
| iOS 転用 | 不可 | 不可 |
| 想定用途 | 基本の音声送信確認 | WAV 再生 / 特定パターン検証 |

設計案1 と設計案2 は競合しない。両方を実装することも可能であり、設計案1 は設計案2 の `write()` ループを `SineWaveGenerator` で自動化した特殊ケースとみなすこともできる。

## 解決方法
