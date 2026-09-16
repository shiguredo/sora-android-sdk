# USB 接続カメラ（UVC）対応を独立モジュールとして実装する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-09-16
- Model: Opus 4.8
- Branch: feature/add-uvc-camera-module

## 目的

組み込み Android 端末などで利用される USB 接続カメラ（UVC デバイス）の映像を Sora に送信できるようにする。

UVC 対応はネイティブコードと端末依存の処理を必要とし、対象となる利用者も限られる。このため SDK 本体には組み込まず、本リポジトリ内の独立モジュールとして実装する。利用したいアプリだけが明示的に依存を追加する形にする。

## 優先度根拠

- 組み込み Android 端末に UVC カメラを接続する利用シーンは存在するが、対応できる端末・カメラ構成が限定的である。
- 既存機能の不具合ではなく新規対応であり、独立モジュールに閉じるため SDK 本体への影響がない。このため Medium とする。

## 事前調査結果

### 第三者ライブラリは採用できない

検討した UVC カメラ向けライブラリは、いずれも libusb (LGPL-2.1) に行き着くため採用できない。

| 候補 | トップレベル | 実際の依存 | 判定 |
|---|---|---|---|
| `jiangdongguo/AndroidUSBCamera` | Apache-2.0 | `libuvc/src/main/jni/libusb/COPYING` が GNU LGPL Version 2.1。`jniLibs/<abi>/libusb100.so` としてプリビルドを同梱 | 採用不可 |
| `saki4510t/UVCCamera` | LICENSE ファイルは無いが README に Apache-2.0 の表記あり。JNI 配下は別ライセンス | `libuvccamera/src/main/jni/libusb` を同梱しており LGPL-2.1 | 採用不可 |
| `libuvc/libuvc` | BSD-3-Clause | libusb (LGPL-2.1) | 採用不可 |
| libusb のみを同梱して UVC 層を自作する案 | — | libusb (LGPL-2.1) | 採用不可 |

`AndroidUSBCamera` が同梱する libusb は `LIBUSB_API_VERSION 0x01000103` で、同梱の `version.h` が `LIBUSB_MICRO 19` であることから **libusb 1.0.19** にあたる。同梱物のうち libjpeg-turbo は IJG / BSD-3-Clause / zlib の 3 ライセンス、rapidjson は MIT であり、これらはいずれも寛容なライセンスで問題にならない。

### AOSP の libusb はアプリから利用できない

`platform/external/libusb` は AOSP に存在するが、アプリからは使えない。`Android.bp` が用途を制限している。

- `libusb_defaults` は `vendor_available: true` のみで、アプリ向けの `sdk_version` を持たない
- `libusb_platform` は「Android OS level で動くプログラム (Android platform services 等) 専用」と明記され、netlink ソケットの権限を必要とする
- NDK の公開ライブラリに libusb は含まれない
- リンカの名前空間分離により、非公開ライブラリはそもそも解決できない

カーネルの `uvcvideo` ドライバも同様に使えない。UVC デバイスを V4L2 の `/dev/video*` として公開するが、SELinux が `untrusted_app` からの利用を禁じている。`system/sepolicy/private/app.te` の `neverallow { appdomain -device_as_webcam } video_device:chr_file { read write };` が該当する。

### Android の公開 USB Host API はアイソクロナス転送をサポートしない

UVC カメラの映像は、USB 2.0 接続ではアイソクロナス (ISO) 転送で送出される。Android の公開 USB Host API ではこの ISO 転送を扱えない。

`UsbConstants.USB_ENDPOINT_XFER_ISOC` の定義部のコメントは `Isochronous endpoint type (currently not supported)` である。

AOSP のネイティブ実装 `libusbhost` の `usb_request_new()` も、BULK と INTERRUPT 以外のエンドポイントを拒否する。

```c
if ((ep_desc->bmAttributes & USB_ENDPOINT_XFERTYPE_MASK) == USB_ENDPOINT_XFER_BULK)
    urb->type = USBDEVFS_URB_TYPE_BULK;
else if ((ep_desc->bmAttributes & USB_ENDPOINT_XFERTYPE_MASK) == USB_ENDPOINT_XFER_INT)
    urb->type = USBDEVFS_URB_TYPE_INTERRUPT;
else {
    D("Unsupported endpoint type %d", ep_desc->bmAttributes & USB_ENDPOINT_XFERTYPE_MASK);
    free(urb);
    return NULL;
}
```

`UsbRequest` のクラスコメントも「bulk および interrupt エンドポイントで利用できる」と明記している。

### usbfs を直接操作すれば LGPL を避けられる

libusb が提供しているのは usbfs の ioctl ラッパーである。Linux カーネルの `drivers/usb/core/devio.c` は `USBDEVFS_SUBMITURB` で `USBDEVFS_URB_TYPE_ISO` を受け付けるため、`/dev/bus/usb` を直接操作するネイティブコードを自作すれば ISO 転送を発行できる。これが LGPL を避けられる唯一のルートである。

`UsbDeviceConnection.getFileDescriptor()` から得た fd に対して ioctl を発行できる見込みが立っている。この fd は system_server 側で開かれたものを Binder 経由で受け取った複製である。

```cpp
// core/jni/android_hardware_UsbDeviceConnection.cpp
// duplicate the file descriptor, since ParcelFileDescriptor will eventually close its copy
```

### SDK 側の受け口はすでに存在する

`SoraMediaOption.enableVideoUpstream(capturer, eglContext, cameraConfig)` に任意の `org.webrtc.VideoCapturer` を渡せる経路があり、`RTCComponentFactory.createVideoManager` は `mediaOption.userSettingVideoCapturer()` を優先して使う。ソフトウェアバッファを供給するカスタム実装と `eglContext = null` の組み合わせは `issues/closed/0029-add-dummy-video-capturer-sample.md` で動作確認済みである。

**この経路があるため、UVC 対応に SDK 本体の変更は不要である。**

## 設計方針

### 実現性の判定を先に行う

issue 0100 で「LGPL に依存せずに UVC カメラのフレームを取得できるか」を実機で判定する。本 issue の実装は 0100 の結果を前提とする。

- 0100 で成立が確認できた場合にのみ、本 issue の実装に着手する。
- 0100 で「対応しない」と判断された場合、本 issue は pending にして理由を記載する（この状態遷移は 0100 の完了条件に含める）。
- 0100 は検証用の最小構成のモジュールを `sora-android-sdk-uvc/` として追加し、検証を打ち切った場合または「対応しない」と判断した場合は削除する。本 issue が定める下記の構成は、0100 の検証コードを土台にするものの、0100 の時点では確定させない。

### 独立モジュールとして実装する

`sora-android-sdk-uvc/` モジュールを本リポジトリに追加する。SDK 本体の `sora-android-sdk/` は変更しない。

- SDK 本体に依存させない。WebRTC の型（`VideoCapturer` など）は SDK 本体が `api(libs.shiguredo.webrtc.android)` で公開している `com.github.shiguredo:shiguredo-webrtc-android` から直接取得する。これにより Sora 以外の WebRTC アプリからも利用でき、単体でのテストも容易になる。
- 別 artifact として公開し、利用したいアプリだけが依存を追加する。SDK 本体の AAR サイズと依存は増やさない。
- ネイティブコード（usbfs と UVC プロトコル層）はこのモジュールに閉じ込める。SDK 本体のビルドに NDK を持ち込まない。
- UVC 対応が不要になった場合はモジュールごと削除できる。

### 利用者側の統合方法

利用者はモジュールが提供する `VideoCapturer` 実装を、SDK の既存 API に自分で渡す。

```kotlin
val capturer = UvcCameraCapturer(context, usbDevice)
option.enableVideoUpstream(capturer, null)
```

利用者提供の `VideoCapturer` は SDK が所有しないため、キャプチャの開始と `dispose()` は利用者の責任になる。`RTCComponentFactory.createVideoManager` は `RTCLocalVideoManager(it, mediaOption.soraCameraConfig)` を生成し、`isOwnedCapturer` は既定値 `false` のままである。`cameraConfig` を渡しても `isOwnedCapturer` は `false` のため、この挙動は変わらない。この点は `issues/closed/0058-add-dummy-capture-for-e2e-test.md` の知見と同じである。

SDK が `startOwnedCapture()` を呼ぶのは `PeerChannel` が `onAddLocalStream` を通知する直前であり、利用者提供の `VideoCapturer` ではこの呼び出しはスキップされる。利用者が `startCapture()` を呼ぶタイミングは README で示す。

### SDK 本体を変更しない代わりに残る制約

SDK が UVC の存在を知らないため、カメラ制御 API は UVC では機能しない。この制約はモジュール側のドキュメントで明示する。

| API | 制約 |
|---|---|
| `SoraMediaChannel.setVideoHardMute()` | `cameraConfig` が `null` の場合 `SoraMediaOption.canVideoCapturerControllable` のガードで弾かれ `false` を返す |
| `SoraMediaChannel.switchCamera()` | `RTCLocalVideoManager.switchCamera()` の `capturer as? CameraVideoCapturer` のキャストに失敗するため、何も起きない |
| `SoraMediaChannel.changeCaptureFormat()` | SDK 側の設定更新経路では例外にならない。`cameraConfig` が `null` の場合は `RTCLocalVideoManager.changeCaptureFormat()` の `cameraConfig?.let` がスキップされ、SDK が管理するカメラ設定は更新されない。一方 `capturer.changeCaptureFormat()` は常に呼ばれるため、カスタム実装側で処理する。カスタム実装が投げた例外は `SoraMediaChannel.changeCaptureFormat()` では捕捉されず伝播する。未接続時は `peer` が `null` のため何も起きない |

UVC ではカメラが 1 台であるため `switchCamera()` は本質的に不要である。ハードミュートは必要になった時点で、モジュール側が提供する独自 API で代替することを検討する。

## モジュール構成

ディレクトリ名がそのまま Gradle プロジェクト名と公開 artifact 名になる。`sora-android-sdk-` prefix を付けることで、親 artifact との関係が依存記述だけで分かるようにする。

```
sora-android-sdk-uvc/
  build.gradle.kts
  src/main/kotlin/jp/shiguredo/sora/sdk/uvc/
    UvcCameraCapturer.kt       VideoCapturer 実装
    UvcCameraDevice.kt         USB デバイスの検出と権限取得
  src/main/cpp/
    CMakeLists.txt             ネイティブビルドの定義
    usbfs.cpp                  usbfs の ioctl ラッパー
    uvc_device.cpp             UVC 記述子のパースと形式ネゴシエーション
    uvc_stream.cpp             ISO URB の管理とフレーム再構成
    jni_bridge.cpp             Kotlin との境界
  src/test/                    ネイティブに依存しないロジックのテスト
```

公開名は次のようになる。親 artifact と同じ group を使うため、依存記述で関係が分かる。

```
com.github.shiguredo:sora-android-sdk-uvc
```

## 追加が必要なビルド設定

| 対象 | 内容 |
|---|---|
| `settings.gradle.kts` | `include(":sora-android-sdk-uvc")` を追加する |
| `sora-android-sdk-uvc/build.gradle.kts` | Android ライブラリとして構成する。ルートの `build.gradle.kts` は `group` を設定していないため `group = "com.github.shiguredo"` をモジュール側で宣言し、`publishing { singleVariant("release") }` と `android { ndkVersion = ... }` と `externalNativeBuild { cmake { path = ... } }` も設定する |
| `gradle/libs.versions.toml` | NDK バージョンを追加し、モジュール側の `ndkVersion` から参照する |
| `.github/workflows/build.yml` | `./gradlew build` が新モジュールも対象にするため、ワークフロー自体の変更は不要。ただし runner に NDK が無い場合は NDK の導入が必要になる。要否は 0100 で確認する |
| `THIRD_PARTY_LICENSES.md` | モジュールが同梱する依存のライセンスを追記する |

### モジュールのバージョン

SDK 本体のバージョンの正本は `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/util/SDKInfo.kt` の `VERSION` で、`canary.py` はこのファイルのみを更新してタグを作る。UVC モジュールには対応するバージョン定数が無い。

**SDK 本体と同じリリースタグで公開する。** 利用者は SDK と UVC モジュールに同じタグを指定でき、両者のバージョンがずれない。この方式では `canary.py` の変更は不要で、タグ `2026.4.0` に対して `com.github.shiguredo:sora-android-sdk:2026.4.0` と `com.github.shiguredo:sora-android-sdk-uvc:2026.4.0` が公開される。

モジュール側に独自のバージョン定数を持つ必要はない。公開時に使うタグはリポジトリのタグであり、ビルドスクリプトにバージョンを埋め込む必要がないためである。ただし JitPack のビルドでモジュール単体のバージョンが必要になった場合は、この方針を見直す。

## 実装フェーズ

1. **USB デバイス検出**: `UsbManager` で UVC デバイスを列挙し、権限取得フローを実装する。
   - targetSdk 31 (Android 12) 以降では `PendingIntent` の可変性（`FLAG_IMMUTABLE` または `FLAG_MUTABLE`）の指定が必須である。本リポジトリは targetSdk 36 のため Android 12 / 13 の端末でも必要になる。
   - Android 14 以降では、可変な `PendingIntent` に component / package を指定していない `Intent` を渡すと例外になる。どちらのフラグを選ぶかは `UsbManager.requestPermission()` の要件と併せて確定する。
   - UVC クラスのデバイスに対する `UsbManager.requestPermission()` は、targetSdk が P 以上のアプリでは `CAMERA` 権限を併せて要求する。
2. **FrameCapture の実装**: usbfs を直接操作して UVC の MJPEG / YUYV フレームを取得し、I420 へ変換するパイプラインを、0100 の検証コードを土台に実装する。
3. **VideoCapturer 実装**: `VideoCapturer` インターフェースを実装し、`SurfaceTexture` に依存しない経路で動作することを検証する。`changeCaptureFormat()` は SDK から常に呼ばれるため、実装側で処理する。
4. **モジュール化**: 独立モジュールとして切り出し、SDK 本体に依存していないことを確認する。
5. **SDK 連携**: `enableVideoUpstream` でカスタム `VideoCapturer` を渡し、Sora サーバーへ映像が送信されることを確認する。
6. **利用手順の整備**: モジュールの README に導入手順・制約・`startCapture()` を呼ぶタイミング・遅延の計測方法を記載し、`sora-android-sdk-samples` に動作するサンプルを追加する。
7. **評価**: 成功基準の性能条件を計測し、実用性を評価する。

## 検証環境

- 検証端末: Android 10 以上の実機（USB ホスト機能を持つもの）
- 推奨カメラ: Logicool C920 / C922（代表的な UVC カメラ）、加えて最低 1 機種以上の別メーカー品
- 注意点: 端末・カメラの組み合わせや給電条件によって UVC の認識可否が変わる。検証は代表的な組み合わせに限定する。

## 成功基準

以下のすべてを満たすこと。

- USB（UVC）カメラの映像が Sora サーバーに送信され、ブラウザ等で視聴できること。
- 640x480 で送信できること。性能は issue 0100 の段階 4 と同じ条件で判定する。`CapturerObserver.onFrameCaptured()` への供給フレーム数が連続 30 秒間で平均 15fps 以上、フレームの欠落率が 10% 以下であること。
- 遅延 500ms 以内であること。遅延は受信側で映像が表示されるまでのエンドツーエンドとし、計測方法を README に明記すること。
- MJPEG / YUYV から I420 への変換を含めたエンドツーエンドのパイプラインが動作すること。
- `SurfaceTexture` 経由でないソフトウェアバッファの映像を `CapturerObserver.onFrameCaptured()` に供給する `VideoCapturer` 実装が `RTCLocalVideoManager` 上で動作すること。

## 完了条件

- `sora-android-sdk-uvc/` モジュールが独立してビルドでき、`sora-android-sdk/` に依存していないこと。
- SDK 本体（`sora-android-sdk/`）のソースコードが変更されていないこと。
- UVC カメラの映像を Sora に送信でき、`sora-android-sdk-samples` の `samples` モジュールに動作するサンプルがあること。サンプルは公開された UVC モジュールを参照する。
- モジュールの README に、導入手順・利用方法・`startCapture()` を呼ぶタイミング・動作確認済みの端末とカメラ・既知の制約・遅延の計測方法が記載されていること。
- 動作確認済みの端末・カメラ機種一覧と既知の制約事項を本 issue の `## 解決方法` セクションに追記すること。
- ネイティブコードと依存にコピーレフトライセンスが含まれていないことを確認し、`THIRD_PARTY_LICENSES.md` に反映すること。
- `sora-android-sdk-uvc` が SDK 本体と同じリリースタグで公開され、利用者が両方に同じタグを指定できること。
- `CHANGES.md` の `## develop` セクションに `[ADD]` として追記すること。

## 関連 issue

- 0100: LGPL に依存せずに UVC カメラのフレームを取得できるかの実現性を実機で検証する。本 issue は 0100 の結果を受けて実装を扱う。0100 で「対応しない」と判断された場合、本 issue は 0100 側で pending にされる。
- 0052: カメラ以外の入力ソース全般（画面共有等）の検討。本 issue は UVC カメラに限定する。
- 0032: サンプル集を本リポジトリへ統合するかの検討。未結論のため、本 issue は現行どおり `sora-android-sdk-samples` にサンプルを追加する。統合された場合は置き場所が変わる。

## 解決方法
