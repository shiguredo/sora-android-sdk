# USB 接続カメラ（UVC）への対応方法を調査し PoC を実施する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-09-04
- Model: Opus 4.8
- Branch: feature/add-uvc-camera-support

## 目的

組み込み Android 端末などで利用される USB 接続カメラ（UVC デバイス）の映像を Sora に送信する方法を調査し、カスタム `VideoCapturer` 実装による PoC を実施する。PoC の結果を踏まえて、SDK 本体への組み込み可否を判断する。

## 優先度根拠

- 組み込み Android 端末に UVC カメラを接続する利用シーンは存在するが、対応できる端末・カメラ構成が限定的であり、まず PoC で成立性を確認する段階である。
- 既存機能の不具合ではなく新規対応の調査であり、SDK 本体への影響も PoC の結果次第のため Medium とする。

## 事前調査結果

一般的な市販 UVC カメラは Android の CameraService に登録されないため、`Camera2` / `CameraX` では扱えない。`CameraX` は `Camera2` の論理カメラのみを扱う。
一部の産業用端末などでメーカーが USB カメラを `Camera2` デバイスとして実装している場合は、例外的に `CameraX` で扱える。

SDK 本体、`sora-android-sdk-samples`、`sora-android-sdk-quickstart` のいずれにも、UVC / USB カメラ対応の実装は存在しない（2026-09-13 時点で再確認）。

### SDK 側の受け口の状況

`SoraMediaOption.enableVideoUpstream(capturer, eglContext, cameraConfig)` に任意の `org.webrtc.VideoCapturer` を渡せる経路があり、`RTCComponentFactory.createVideoManager` は `mediaOption.userSettingVideoCapturer()` を優先して使う。ソフトウェアバッファを供給するカスタム実装と `eglContext = null` の組み合わせは `issues/closed/0029-add-dummy-video-capturer-sample.md` で動作確認済みである。

つまり SDK 側の受け口はすでにあり、欠けているのは UVC デバイスを扱う `VideoCapturer` 実装そのものである。

### 第三者 UVC ライブラリの状況

2026-09-13 時点で GitHub API と各リポジトリの実体から取得した情報を示す。

| ライブラリ | star | 最終 push | トップレベル | 実際の依存 | 判定 |
|---|---|---|---|---|---|
| `jiangdongguo/AndroidUSBCamera` | 2757 | 2024-09-02 | Apache-2.0 | `libuvc/src/main/jni/libusb/COPYING` が GNU LGPL Version 2.1。`jniLibs/<abi>/libusb100.so` としてプリビルドを同梱 | 採用不可 |
| `saki4510t/UVCCamera` | 3219 | 2021-12-14 | リポジトリにライセンス表記なし | libusb と libuvc を JNI でビルド | 採用不可 |
| `libuvc/libuvc` | 1159 | 2026-09-13 | BSD-3-Clause | libusb (LGPL-2.1) | 採用不可 |
| libusb のみを同梱して UVC 層を自作する案 | — | — | — | libusb (LGPL-2.1) | 採用不可 |

**UVC を扱う既存ライブラリはすべて libusb (LGPL-2.1) に行き着くため、LGPL を避ける方針では採用できない。** `AndroidUSBCamera` が同梱する libusb は `LIBUSB_API_VERSION 0x01000103` (libusb 1.0.22 系) である。

同梱物のうち libjpeg-turbo は BSD-3-Clause、rapidjson は MIT であり、これらは問題にならない。`saki4510t/UVCCamera` はライセンスが明示されておらず、同梱物のライセンスも別途確認が必要である。

### 自作の実現性

Android の公開 USB Host API はアイソクロナス転送をサポートせず、AOSP のネイティブ実装 `libusbhost` も BULK と INTERRUPT 以外のエンドポイントを拒否する。UVC カメラの映像はアイソクロナス転送で送出されるため、公開 API だけでは UVC のフレームを取得できない。

ただし libusb が提供しているのは usbfs の ioctl ラッパーにすぎない。`/dev/bus/usb` を直接操作するネイティブコードを自作すれば、libusb を介さずにアイソクロナス転送を発行できる。これが LGPL を避けられる唯一のルートである。

自作が必要な範囲は、usbfs の ioctl、UVC の記述子パースと形式ネゴシエーション、ISO URB の組み立てとフレーム再構成である。MJPEG デコードは Android の `BitmapFactory` が利用できるため自作は不要である。実現性の判定は 0100 で行う。

### 想定する実装ルート

一般的な UVC カメラに対応するルートは次のとおりである。

1. USB カメラが `Camera2` の外部カメラとして登録されている場合。既存の `CameraCapturerFactory` がそのまま使えるが、そのような端末に限られる。
2. `/dev/bus/usb` を直接操作するネイティブコードを自作し、カスタム `VideoCapturer` 実装で映像を供給する場合。LGPL を避けつつ一般的な UVC カメラに対応できる唯一のルートである。

## 関連 issue

- 0100: LGPL に依存せずに UVC カメラのフレームを取得できるかの実現性を実機で検証する。本 issue は方式を決めて PoC を実施するところまでを扱い、実現性判定は 0100 に分離する。
- 0052: カメラ以外の入力ソース全般（画面共有等）の検討。本 issue は UVC カメラに限定する。

## SDK の既存制約

調査および PoC にあたり、以下の SDK 制約を考慮する必要がある。
なお、`issues/closed/0029-add-dummy-video-capturer-sample.md` の対応で、カスタム `VideoCapturer`（ソフトウェアバッファ、`eglContext = null`、`cameraConfig = null`）による Sora への映像送信はすでに動作確認済みである。以下はこの結果と合わせて前提にできる。

### カスタム VideoCapturer のキャプチャ開始

`enableVideoUpstream(capturer, eglContext, cameraConfig = null)` でカスタム `VideoCapturer` を渡した場合、SDK は `capturer.startCapture()` を自動で呼ばない。`RTCLocalVideoManager.startOwnedCapture()` 内の `isOwnedCapturer == false` の分岐でスキップされる（既定値は `false`）ため、SDK 利用者が自前で接続前または `onAddLocalStream` コールバック後に `startCapture()` を呼ぶ必要がある。また `capturer.dispose()` の解放も SDK 利用者の責任である。

### SurfaceTextureHelper の必須性

`RTCLocalVideoManager.initTrack()` は `SurfaceTextureHelper.create(...)` を呼び出し、そのインスタンスを `capturer.initialize()` に渡している。カスタム実装は `SurfaceTextureHelper` の `handler` をスケジューリングに利用できる（issue 0029 の `DummyVideoCapturer` もこの `handler` を利用している）。UVC カメラの出力はソフトウェアバッファ（MJPEG / YUYV / YUV）であり `SurfaceTexture` を介さないため、`SurfaceTexture` に依存しない実装で足りるか、あるいは `initialize()` に `null` の `SurfaceTextureHelper` を渡すための SDK 側の変更が必要かを検証する。

### CameraVideoCapturer 専用 API の制限

以下の SDK API は `CameraVideoCapturer` または `cameraConfig` を前提としており、UVC のカスタム `VideoCapturer` では制約が生じる。PoC のスコープからは除外する。

| API | 制約 |
|---|---|
| `SoraMediaChannel.switchCamera()` | `RTCLocalVideoManager.switchCamera()` の `capturer as? CameraVideoCapturer` のキャストに失敗するため、何も起きない |
| `SoraMediaChannel.setVideoHardMute()` | `cameraConfig` が `null` の場合 `SoraMediaOption.canVideoCapturerControllable` のガードで弾かれ `false` を返す |
| `SoraMediaChannel.changeCaptureFormat()` | `cameraConfig` が `null` の場合も例外にはならず、SDK 側で管理するカメラ設定の更新がスキップされるだけである（カスタム実装側の `VideoCapturer.changeCaptureFormat()` の対応に依存する） |
| `SoraMediaChannel.startVideoCapture()` | この名前の公開 API は存在しない。`IllegalStateException` を投げるのは `internal` な `RTCLocalVideoManager.startVideoCapture()` のみで、公開経路では `SoraMediaChannel.setVideoHardMute()` からしか呼ばれない |

### EGL コンテキストの要否

`enableVideoUpstream(capturer, eglContext, cameraConfig)` の `eglContext` は、`PeerChannel` の `initTrack()` 経由で `RTCLocalVideoManager.initTrack()` の `SurfaceTextureHelper.create()` に、また `RTCComponentFactory` のエンコーダーファクトリ生成（`SoraDefaultVideoEncoderFactory` → `HardwareVideoEncoderFactory`）に渡される。`eglContext` が `null` の場合、`RTCComponentFactory.determineVideoEncoderFactoryType()` は UPSTREAM ではなく NULL の分岐を選択する。UVC フレームがソフトウェアバッファ経由の場合、`eglContext` を `null` にしてこの両経路が成立するか（特にハードウェアエンコーダーが使えるかどうか）を検証する。

### MJPEG / YUYV → I420 変換

多くの UVC カメラは USB レベルでは MJPEG / YUYV 等を出力する。WebRTC の `VideoCapturer` が期待する I420 への変換が必要になり、この変換はソフトウェア処理となるため、性能面での影響を評価する必要がある。MJPEG のデコードには Android の `BitmapFactory` が利用できる。

## 設計方針

SDK 本体への組み込み可否は PoC の結果を踏まえて別途判断する。まず `sora-android-sdk-samples` / `sora-android-sdk-quickstart` 側でカスタム `VideoCapturer` 実装として PoC を進め、再現可能な手順を確立する。成果物は `sora-android-sdk-samples` に残す。

UVC ライブラリの採用については、LGPL を避ける方針から第三者ライブラリを採用しない。このため方式は「`/dev/bus/usb` を直接操作するネイティブコードの自作」に限定され、0100 で実現性を判定する。

- 0100 で成立が確認できた場合は、検証用のネイティブコードを土台に実装を進める。
- 0100 で成立しなかった場合は、一般的な UVC カメラへの対応を断念し、`Camera2` の外部カメラとして見える端末のみを対象とする形へ方針を切り替える。
- SDK 本体に UVC 用の第三者ライブラリを同梱することは行わない。

この結果、`switchCamera` や `setVideoHardMute` などのカメラ制御 API は UVC では利用できない制約が残る。この制約をどう扱うかは PoC の結果を踏まえて判断する。

## 調査フェーズ

1. **実現性の判定**: 0100 で、LGPL に依存せずに UVC フレームを取得できるかを実機で判定する。
2. **USB デバイス検出**: `UsbManager` で UVC デバイスを列挙し、権限取得フローを実装する。Android 14 以降は `PendingIntent` の可変性指定が必要になる点に注意する。
3. **フレーム取得**: usbfs を直接操作して UVC の MJPEG / YUYV フレームを取得し、I420 へ変換するパイプラインを構築する。
4. **VideoCapturer 実装**: `VideoCapturer` インターフェースを実装し、`SurfaceTexture` に依存しない経路で動作することを検証する。
5. **SDK 連携**: `enableVideoUpstream` でカスタム `VideoCapturer` を渡し、Sora サーバーへ映像が送信されることを確認する。
6. **評価**: フレームレート・遅延・CPU 使用率を計測し、実用性を評価する。

## 却下した候補

LGPL を避ける方針から、次の候補は採用しない。判断の根拠を残すために記録する。

| 候補 | 却下理由 |
|---|---|
| `jiangdongguo/AndroidUSBCamera` | libusb (LGPL-2.1) を `jniLibs/<abi>/libusb100.so` として同梱している。トップレベルが Apache-2.0 でも LGPL を持ち込むため採用できない |
| `saki4510t/UVCCamera` | libusb と libuvc を JNI でビルドしており LGPL を持ち込む。加えてリポジトリにライセンス表記がなく、JNI の対象 ABI が現在の NDK では非対応の `armeabi` / `mips` のままである |
| `libuvc/libuvc` | 本体は BSD-3-Clause だが libusb (LGPL-2.1) に依存する |
| libusb の同梱 | それ自体が LGPL-2.1 である |

## 検証環境

- 検証端末: Android 10 以上の実機
- 推奨カメラ: Logicool C920 / C922（代表的な UVC カメラ）、加えて最低 1 機種以上の別メーカー品
- 注意点: 端末・カメラの組み合わせや給電条件によって UVC の認識可否が変わる。PoC では代表的な組み合わせに限定する。

## PoC 成功基準

以下のすべてを満たすこと。

- USB （UVC） カメラの映像が Sora サーバーに送信され、ブラウザ等で視聴できること。
- 640x480 で 15fps 以上、遅延 500ms 以内の映像送信が安定して行えること。遅延は受信側で映像が表示されるまでのエンドツーエンドとし、計測方法を README に明記すること。
- MJPEG / YUYV から I420 への変換を含めたエンドツーエンドのパイプラインが動作すること。
- `SurfaceTexture` 経由でないソフトウェアバッファの映像を `CapturerObserver.onFrameCaptured()` に供給する `VideoCapturer` 実装が `RTCLocalVideoManager` 上で動作すること。

## 完了条件

- `sora-android-sdk-samples` リポジトリの `samples` モジュール（`samples/src/main/kotlin/jp/shiguredo/sora/sample/` 配下。既存の `camera/DummyVideoCapturer.kt` と同様の配置）に動作可能なサンプルコードと、実機での検証・計測手順を記載した README を残すこと。
- 動作確認済みの端末・カメラ機種一覧と既知の制約事項を本 issue の `## 解決方法` セクションに追記すること。
- SDK 本体で対応すべきか、サンプル側のカスタム映像ソースとして提供すべきかの方針を結論づけること。
- 0100 の判定結果を踏まえ、完全自作で進めるか UVC 対応を断念するかを結論づけること。
- 自作したネイティブコードのライセンス上の問題がないこと（LGPL その他のコピーレフトライセンスに依存していないこと）を確認すること。
