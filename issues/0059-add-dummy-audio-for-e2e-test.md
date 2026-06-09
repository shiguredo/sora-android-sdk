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

## 解決方法
