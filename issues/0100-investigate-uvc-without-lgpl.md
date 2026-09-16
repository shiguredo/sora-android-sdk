# LGPL に依存せずに UVC カメラのフレームを取得できるか実現性を検証する

- Created: 2026-09-13
- Completed: {YYYY-MM-DD}
- Branch: feature/investigate-uvc-without-lgpl
- Polished: {YYYY-MM-DD}

## 目的

UVC カメラ対応で第三者ライブラリを採用できないことが確定したため、LGPL に依存せずに UVC カメラのフレームを取得できるかどうかを、実装に着手する前に実機で判定する。

本 issue は実現性の判定だけを目的とし、UVC 対応そのものの実装は扱わない。判定結果によって「完全自作で進める」「対応しない」のいずれに進むかが決まる。

## 現状

2026-09-13 時点の一次情報による調査結果を示す。

### LGPL に依存するライブラリは採用できない

UVC カメラを扱う既存ライブラリは、いずれも libusb (LGPL-2.1) に行き着く。トップレベルのライセンスが寛容でも、同梱物に LGPL が含まれるため採用できない。

| 候補 | トップレベル | 実際の依存 | 判定 |
|---|---|---|---|
| `jiangdongguo/AndroidUSBCamera` | Apache-2.0 | `libuvc/src/main/jni/libusb/COPYING` が GNU LGPL Version 2.1。`jniLibs/<abi>/libusb100.so` としてプリビルドを同梱 | 採用不可 |
| `saki4510t/UVCCamera` | リポジトリにライセンス表記なし | libusb と libuvc を JNI でビルド | 採用不可 |
| `libuvc/libuvc` | BSD-3-Clause | libusb (LGPL-2.1) | 採用不可 |
| 自作して libusb を同梱 | — | libusb (LGPL-2.1) | 採用不可 |

`AndroidUSBCamera` が同梱する libusb は `LIBUSB_API_VERSION 0x01000103` (libusb 1.0.22 系) である。同梱物のうち libjpeg-turbo は BSD-3-Clause、rapidjson は MIT で、これらは問題にならない。

したがって「LGPL を避ける」と「既存ライブラリを使う」は両立しない。

### libusb を避けても usbfs は直接利用できる

libusb が提供しているのは usbfs の ioctl ラッパーである。libusb の Linux usbfs バックエンド `libusb/os/linux_usbfs.c` は `USBFS_URB_TYPE_ISO` を使っており、独自実装でも同じ ioctl を直接発行すれば同じことができる。

つまり libusb を外しても ISO 転送の手段は残る。自作する範囲は次のとおりである。

- usbfs 操作: `/dev/bus/usb/<bus>/<dev>` の open と `USBDEVFS_CLAIMINTERFACE` / `USBDEVFS_SUBMITURB` / `USBDEVFS_REAPURB` / `USBDEVFS_CONTROL` などの ioctl
- UVC 制御: VideoControl / VideoStreaming 記述子のパースと、形式・フレームサイズのネゴシエーション
- ISO 転送: `usbdevfs_urb` の組み立てと ISO パケットの処理
- フレーム再構成: `payload_header` の解釈と 1 フレームの組み立て
- MJPEG デコード: Android の `BitmapFactory` が利用できるため自作は不要

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

`UsbRequest` のクラスコメントも「bulk および interrupt エンドポイントで利用できる」と明記している。したがって `UsbRequest` / `UsbDeviceConnection` をどう組み合わせても ISO 転送は発行できない。

### Linux カーネル側は ISO 転送をサポートする

`drivers/usb/core/devio.c` は `USBDEVFS_SUBMITURB` で `usb_alloc_urb(numisoframes, ...)` と `iso_frame_desc` を扱い、`USBDEVFS_URB_TYPE_ISO` を受け付ける。ISO 転送の可否を分けているのはカーネルではなく Android のユーザー空間側であり、`/dev/bus/usb` を直接操作できれば ISO 転送を発行できる可能性がある。

### 自作する場合の実装規模

これは libuvc が提供している機能の再実装にあたる。libuvc の主要な実装は `device.c` が約 2000 行、`stream.c` が約 1600 行、`frame.c` が約 530 行、`frame-mjpeg.c` が約 250 行で、別途 2000 行を超える自動生成の制御要求コードを持つ。自作する場合はこのうち必要な範囲を書き起こすことになる。

## 検証する仮説

次の順に検証し、どこかで成立しなければそこで打ち切る。後続の手順は前段が成立した場合のみ実施する。

### 1. `UsbDeviceConnection` の fd で usbfs を直接操作できるか

`UsbDeviceConnection.getFileDescriptor()` から得た fd を使い、ネイティブコードから USBDEVFS の ioctl を発行できるかを確認する。

- アプリが USB 権限を取得した状態で、その fd に対して `USBDEVFS_CLAIMINTERFACE` が成功すること
- `USBDEVFS_GET_CAPABILITIES` など、転送を伴わない ioctl が成功すること

対象ファイル: `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/` には該当実装がない。検証用コードは本リポジトリに追加する `usb-camera/` モジュールに置く（モジュール構成は issue 0015 を参照）。

### 2. カーネルドライバをデタッチできるか

`uvcvideo` などのカーネルドライバが対象インターフェースを掴んでいる場合に、`USBDEVFS_DISCONNECT` または `USBDEVFS_DISCONNECT_CLAIM` でデタッチして claim できるかを確認する。

- デタッチに成功する端末と失敗する端末の条件
- デタッチが不要な端末が存在するか

### 3. ISO URB を発行して UVC のフレームを取得できるか

`USBDEVFS_URB_TYPE_ISO` の URB を発行し、UVC カメラから実際に映像フレームが得られるかを確認する。

- `USBDEVFS_SUBMITURB` が ISO URB を受け付けること
- 制御転送で UVC の形式とフレームサイズを指定できること
- ISO パケットから 1 フレームを再構成できること

### 4. 実用的な性能が出るか

再構成したフレームを I420 へ変換して `CapturerObserver.onFrameCaptured()` に供給し、目標性能に到達するかを計測する。

- 640x480 で 15fps 以上を安定して維持できること
- MJPEG と YUYV のどちらが現実的かを判断できること

MJPEG は USB 帯域を抑えられるがデコードの CPU 負荷が高い。YUYV はデコード不要だが帯域を大きく消費する。どちらを採用するかは計測結果で決める。

## 設計方針

- 検証コードは本リポジトリに追加する `usb-camera/` モジュールに置く。`sora-android-sdk/` には入れず、SDK 本体に依存させない。
- ネイティブコードをビルドする必要があるため、`usb-camera/` モジュールに NDK ビルドの構成を追加する。追加するネイティブコードは検証用の最小限にとどめる。
- libusb / libuvc / AndroidUSBCamera / UVCCamera はこの検証では使用しない。LGPL を避けられるかどうかが本 issue の問いであり、使用すると問いに答えられなくなる。
- 検証は段階ごとに成否を記録し、失敗した段階で打ち切って結果を本 issue の `## 解決方法` に記録する。
- 検証で得た知見は issue 0015 の方針判断に渡す。

## 検証環境

- 検証端末: Android 10 以上の実機（USB ホスト機能を持つもの）
- 検証カメラ: 代表的な UVC カメラ（Logicool C920 / C922 等）

## 完了条件

- 前掲の 4 段階それぞれについて、実機で成立するかどうかが判定されていること。
- 成立した場合は、LGPL に依存しない完全自作での UVC 対応が現実的かどうかの判断材料（実装規模の見積もりと実測性能）が示されていること。
- 成立しなかった場合は、どの段階で何が原因で成立しなかったかが具体的に記録されていること。
- 検証結果を踏まえ、issue 0015 の進め方（完全自作 / 対応しない）のいずれを選ぶかの判断が示されていること。
- 検証用のネイティブコードが `usb-camera/` モジュールにあり、`sora-android-sdk/` から参照されていないこと。

## 解決方法
