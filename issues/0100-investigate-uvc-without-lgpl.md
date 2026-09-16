# LGPL に依存せずに UVC カメラのフレームを取得できるか実現性を検証する

- Created: 2026-09-13
- Completed: {YYYY-MM-DD}
- Branch: feature/investigate-uvc-without-lgpl
- Polished: 2026-09-16

## 目的

UVC カメラ対応で第三者ライブラリを採用できないことが確定したため、LGPL に依存せずに UVC カメラのフレームを取得できるかどうかを、実装に着手する前に実機で判定する。

本 issue は実現性の判定だけを目的とし、UVC 対応そのものの実装は扱わない。判定結果によって「完全自作で進める」「対応しない」のいずれに進むかが決まる。

## 現状

2026-09-13 時点の一次情報による調査結果を示す。

### 第三者 UVC ライブラリは採用できない

検討した UVC カメラ向けライブラリは、いずれも libusb (LGPL-2.1) に行き着く。トップレベルのライセンスが寛容でも、同梱物に LGPL が含まれるため採用できない。

| 候補 | トップレベル | 実際の依存 | 判定 |
|---|---|---|---|
| `jiangdongguo/AndroidUSBCamera` | Apache-2.0 | `libuvc/src/main/jni/libusb/COPYING` が GNU LGPL Version 2.1。`jniLibs/<abi>/libusb100.so` としてプリビルドを同梱 | 採用不可 |
| `saki4510t/UVCCamera` | LICENSE ファイルは無いが README に Apache-2.0 の表記あり。JNI 配下は別ライセンス | `libuvccamera/src/main/jni/libusb` を同梱しており LGPL-2.1 | 採用不可 |
| `libuvc/libuvc` | BSD-3-Clause | libusb (LGPL-2.1) | 採用不可 |
| 自作して libusb を同梱 | — | libusb (LGPL-2.1) | 採用不可 |

`AndroidUSBCamera` が同梱する libusb は `LIBUSB_API_VERSION 0x01000103` で、同梱の `version.h` が `LIBUSB_MICRO 19` であることから **libusb 1.0.19** にあたる。同梱物のうち libjpeg-turbo は IJG / BSD-3-Clause / zlib の 3 ライセンス、rapidjson は MIT で、これらはいずれも寛容なライセンスであり問題にならない。

したがって「LGPL を避ける」と「既存ライブラリを使う」は両立しない。

### libusb を避けても usbfs は直接利用できる

libusb が提供しているのは usbfs の ioctl ラッパーである。libusb の Linux usbfs バックエンド `libusb/os/linux_usbfs.c` は `USBFS_URB_TYPE_ISO` を使っており、独自実装でも同じ ioctl を直接発行すれば同じことができる。

つまり libusb を外しても ISO 転送の手段は残る。自作する範囲は次のとおりである。

- usbfs 操作: `USBDEVFS_CLAIMINTERFACE` / `USBDEVFS_SETINTERFACE` / `USBDEVFS_SUBMITURB` / `USBDEVFS_REAPURB` / `USBDEVFS_CONTROL` などの ioctl と、カーネルドライバのデタッチ（`USBDEVFS_DISCONNECT` / `USBDEVFS_DISCONNECT_CLAIM`）
- UVC 制御: VideoControl / VideoStreaming 記述子のパースと、形式・フレームサイズのネゴシエーション
- ISO 転送: `usbdevfs_urb` の組み立てと ISO パケットの処理
- フレーム再構成: UVC のペイロードヘッダを解釈した 1 フレームの組み立て
- MJPEG デコード: Android の `BitmapFactory` が利用できるため自作は不要

fd はカーネルドライバのデタッチに使う `USBDEVFS_DISCONNECT` も含め、`UsbDeviceConnection` が返す fd に対して発行する。

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

`UsbRequest` のクラスコメントも「bulk および interrupt エンドポイントで利用できる」と明記している。したがって `UsbRequest` / `UsbDeviceConnection` の組み合わせでは ISO 転送を発行できない。制御転送は `UsbDeviceConnection.controlTransfer()` で別途利用できる。

### Linux カーネル側は ISO 転送をサポートする

`drivers/usb/core/devio.c` は `USBDEVFS_SUBMITURB` で `usb_alloc_urb(numisoframes, ...)` と `iso_frame_desc` を扱い、`USBDEVFS_URB_TYPE_ISO` を受け付ける。ISO 転送の可否を分けているのはカーネルではなく Android のユーザー空間側であり、`/dev/bus/usb` を直接操作できれば ISO 転送を発行できる可能性がある。

### 自作する場合の実装規模

これは libuvc が提供している機能の再実装にあたる。libuvc の主要な実装は C の物理行で `device.c` が 1996 行、`stream.c` が 1595 行、`frame.c` が 534 行、`frame-mjpeg.c` が 250 行で、別途 2259 行の自動生成の制御要求コード (`ctrl-gen.c`) を持つ。自作する場合はこのうち必要な範囲を書き起こすことになる。

## 検証する仮説

次の順に検証する。前段が成立しない場合はそこで打ち切り、後続は実施しない。

段階 1 を先に置く理由は次のとおりである。`uvcvideo` がインターフェースを掴んでいる場合、`USBDEVFS_CLAIMINTERFACE` は `EBUSY` で失敗し、その解消はデタッチにあたる。claim の可否はデタッチを試した後に確定するため、デタッチを段階 1 で扱う。

### 1. カーネルドライバをデタッチしてインターフェースを claim できるか

`uvcvideo` などのカーネルドライバが対象インターフェースを掴んでいる場合に、`USBDEVFS_DISCONNECT` または `USBDEVFS_DISCONNECT_CLAIM` でデタッチして claim できるかを確認する。

- 対象端末のうち 1 台以上で、対象インターフェースの claim に成功すること
- デタッチが不要な端末が存在するか

`UsbDeviceConnection.claimInterface(intf, force = true)` は内部で `EBUSY` のときに `USBDEVFS_DISCONNECT` して再 claim するため、この経路でも確認する。

### 2. `UsbDeviceConnection` の fd で usbfs を直接操作できるか

段階 1 で claim に使ったのと同じ fd を対象に、ネイティブコードから USBDEVFS の ioctl を発行できるかを確認する。転送を伴わない ioctl で判定し、インターフェースの状態を変える操作は段階 3 に含める。

- `UsbDeviceConnection.getFileDescriptor()` から得た fd に対して `USBDEVFS_GET_CAPABILITIES` が成功すること
- 得られた fd が `open()` で複製されたものであり、アプリのプロセスから usbfs の ioctl を発行できること

### 3. ISO URB を発行して UVC のフレームを取得できるか

`USBDEVFS_URB_TYPE_ISO` の URB を発行し、UVC カメラから実際に映像フレームが得られるかを確認する。

- `USBDEVFS_SETINTERFACE` で ISO エンドポイントを持つ alternate setting を選択できること
  - UVC の VideoStreaming インターフェースは alternate setting 0 に ISO エンドポイントを持たないため、この選択を行わないと `USBDEVFS_SUBMITURB` は `-ENOENT` で失敗する
- `USBDEVFS_SUBMITURB` が ISO URB を受け付けること
- 制御転送で UVC の形式とフレームサイズを指定できること
- ISO パケットから 1 フレームを再構成できること

### 4. 実用的な性能が出るか

再構成したフレームを I420 へ変換して `CapturerObserver.onFrameCaptured()` に供給し、目標性能に到達するかを計測する。

- `onFrameCaptured()` への供給フレーム数が、連続 30 秒間で平均 15fps 以上であること
- 目標解像度は 640x480 とし、フレームの欠落率が 10% 以下であること

640x480・15fps の YUYV は 9.2 MB/s、MJPEG はこれを下回る。USB 2.0 High Speed のアイソクロナス転送の上限は約 24.5 MB/s であるため、帯域自体は目標に対して余裕がある。

MJPEG は USB 帯域を抑えられるがデコードの CPU 負荷が高い。YUYV はデコード不要だが帯域を大きく消費する。どちらを採用するかは計測結果で決める。

## 設計方針

### 検証コードの置き場所

検証コードは本リポジトリに追加する `sora-android-sdk-uvc/` モジュールに置く。`sora-android-sdk/` には入れず、SDK 本体に依存させない。モジュールの最終的な構成は issue 0015 で定義する。

検証用のモジュールは検証に必要な最小構成とし、0015 が定める構成を先取りしない。

```
sora-android-sdk-uvc/
  build.gradle.kts
  src/main/kotlin/jp/shiguredo/sora/sdk/uvc/   検証用の呼び出しコード
  src/main/cpp/                                検証用のネイティブコード
```

検証を打ち切った場合、または「対応しない」と判断した場合は、検証用のモジュールと `settings.gradle.kts` への `include` を作業ブランチ上で削除する。破棄した検証コードは develop に残さず、得られた知見のみを本 issue の `## 解決方法` に記録する。

### ネイティブビルドと CI

- ネイティブコードをビルドするため、`gradle/libs.versions.toml` に NDK バージョンを追加してモジュールの `ndkVersion` で固定する。リポジトリには現在 NDK バージョンの指定が無い。
- `.github/workflows/build.yml` は `./gradlew build` を実行するため、`settings.gradle.kts` に追加したモジュールが自動的にビルド対象になる。ただし runner に NDK が無い場合は NDK の導入が必要になる。実装時に runner の NDK の有無を確認する。
- `.github/workflows/e2e-test.yml` は push で発火し、`./gradlew :sora-android-sdk:pixelApi35AndroidE2ETest` を self-hosted の macOS runner で実行する。モジュールを `settings.gradle.kts` に追加すると Gradle の構成フェーズで新モジュールも評価されるため、runner に NDK が無い場合は `paths-ignore` の追加または NDK の導入が必要になる。実装時に runner の NDK の有無を確認する。

### 実装上の注意

- UVC クラスの USB デバイスに対する `UsbManager.requestPermission()` は、targetSdk が P 以上のアプリでは `CAMERA` 権限を併せて要求する。検証では権限付与済みの fd を使うため必須ではないが、モジュールで USB デバイスを検出する工程では必要になる。
- libusb / libuvc / AndroidUSBCamera / UVCCamera はこの検証では使用しない。LGPL を避けられるかどうかが本 issue の問いであり、使用すると問いに答えられなくなる。
- 検証は段階ごとに成否を記録し、失敗した段階で打ち切って結果を本 issue の `## 解決方法` に記録する。

## 検証環境

- 検証端末: Android 10 以上の実機を 2 台以上。カーネルドライバのデタッチ可否は端末依存であるため、1 台では判定できない
- 検証カメラ: Logicool C920 / C922 を基本とし、可能であれば別メーカー品を 1 機種追加する
- 注意点: 端末・カメラの組み合わせや給電条件によって UVC の認識可否が変わる

## 完了条件

- 打ち切った段階までの各仮説について、実機で成立するかどうかが判定されていること。
- 各段階の判定結果が、成立した場合も成立しなかった場合も本 issue の `## 解決方法` に記録されていること。
- 検証に使用した端末の機種・Android バージョンとカメラの機種が記録されていること。
- 成立しなかった場合は、どの段階で何が原因で成立しなかったかが具体的に記録されていること。
- 成立した場合は、LGPL に依存しない完全自作での UVC 対応が現実的かどうかの判断材料が示されていること。判断材料には次を含める。
  - `### 自作する場合の実装規模` に示した libuvc の C の物理行を基準に、自作が必要な範囲の行数を見積もる。見積もりは次の条件で示す。
    - 自作するコードも物理行で数える
    - ネイティブコード（C++）と Kotlin を分けて示す
    - libuvc のどのファイルの機能に対応するかを対応付ける
    - libuvc で除外できる機能とその理由を併記する
  - 段階 4 の実測性能
- 検証結果を踏まえ、issue 0015 の進め方（完全自作 / 対応しない）のいずれを選ぶかの判断が示されていること。
- 「対応しない」と判断した場合は、issue 0015 を pending にして理由を記載するところまでを行うこと。
- 検証を打ち切った場合、または「対応しない」と判断した場合は、`sora-android-sdk-uvc/` モジュールと `settings.gradle.kts` への `include` が削除されていること。
- 検証用コードに LGPL その他のコピーレフトライセンスが含まれていないこと。

## 解決方法
