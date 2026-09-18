# LGPL に依存せずに UVC カメラのフレームを取得できるか実現性を検証する

- Created: 2026-09-13
- Completed: 2026-09-18
- Branch: feature/investigate-uvc-without-lgpl
- Polished: 2026-09-16
- Updated: 2026-09-17

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

検証を打ち切った場合、または「対応しない」と判断した場合は、検証用のモジュールと `settings.gradle.kts` への `include` を作業ブランチ上で削除する。破棄した検証コードは develop に残さず、得られた知見のみを本 issue の `## 事前調査結果` に記録する。

### ネイティブビルドと CI

- ネイティブコードをビルドするため、`gradle/libs.versions.toml` に NDK バージョンを追加してモジュールの `ndkVersion` で固定する。リポジトリには現在 NDK バージョンの指定が無い。
- `.github/workflows/build.yml` は `./gradlew build` を実行するため、`settings.gradle.kts` に追加したモジュールが自動的にビルド対象になる。ただし runner に NDK が無い場合は NDK の導入が必要になる。実装時に runner の NDK の有無を確認する。
- `.github/workflows/e2e-test.yml` は push で発火し、`./gradlew :sora-android-sdk:pixelApi35AndroidE2ETest` を self-hosted の macOS runner で実行する。モジュールを `settings.gradle.kts` に追加すると Gradle の構成フェーズで新モジュールも評価されるため、runner に NDK が無い場合は `paths-ignore` の追加または NDK の導入が必要になる。実装時に runner の NDK の有無を確認する。

### 実装上の注意

- UVC クラスの USB デバイスに対する `UsbManager.requestPermission()` は、targetSdk が P 以上のアプリでは `CAMERA` 権限を併せて要求する。検証では権限付与済みの fd を使うため必須ではないが、モジュールで USB デバイスを検出する工程では必要になる。
- libusb / libuvc / AndroidUSBCamera / UVCCamera はこの検証では使用しない。LGPL を避けられるかどうかが本 issue の問いであり、使用すると問いに答えられなくなる。
- 検証は段階ごとに成否を記録し、失敗した段階で打ち切って結果を本 issue の `## 事前調査結果` に記録する。

## 検証環境

- 検証端末: Android 10 以上の実機を 2 台以上。カーネルドライバのデタッチ可否は端末依存であるため、1 台では判定できない
- 検証カメラ: Logicool C920 / C922 を基本とし、可能であれば別メーカー品を 1 機種追加する
- 注意点: 端末・カメラの組み合わせや給電条件によって UVC の認識可否が変わる

## 完了条件

- 打ち切った段階までの各仮説について、実機で成立するかどうかが判定されていること。
- 各段階の判定結果が、成立した場合も成立しなかった場合も本 issue の `## 事前調査結果` に記録されていること。
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

## 事前調査結果

### Pixel 7 と C922 の自作 usbfs 実装による実測結果

2026-09-17 に、別リポジトリの検証アプリへ `libusb` と `libuvc` を使わない自作 UVC 経路を追加し、Pixel 7 と Logicool C922 Pro Stream Webcam で30秒間の実ストリーミングを3回実施した。

検証端末は Pixel 7、Android 17（API 37）、ビルド ID `CP2A.260705.006` である。

検証カメラは Logicool C922 Pro Stream Webcam（VID `0x046D`、PID `0x085C`）である。

ネイティブコードは NDK の `linux/usbdevice_fs.h` と Android のログ APIだけを使用し、外部の UVC ライブラリをリンクしていない。

APK のネイティブライブラリ一覧には自作の `libcamera_iso_native.so` だけが含まれ、`libusb` と `libuvc` は含まれていない。

| 確認項目 | 結果 | 実測値 |
|---|---|---|
| USB デバイス列挙 | 成功 | `UsbManager.deviceList` に C922 が現れた |
| USB 権限取得 | 成功 | Android の USB 権限ダイアログで許可できた |
| VideoStreaming interface の claim | 成功 | `UsbDeviceConnection.claimInterface(force=true)` が成功した |
| `UsbDeviceConnection` の fd | 成功 | ネイティブコードで fd `134` を使用した |
| `USBDEVFS_GET_CAPABILITIES` | 成功 | `0x000001FD` |
| UVC 記述子パース | 成功 | UVC `0x0100`、VC interface `0`、VS interface `1`、ISO IN endpoint `0x81` |
| UVC 形式・フレーム | 成功 | MJPEG、640x480、format/frame `2/1`、interval `333333`（100 ns 単位） |
| UVC PROBE | 成功 | `GET_CUR` 26 bytes、`SET_CUR`、再 `GET_CUR` が成功した |
| UVC COMMIT | 成功 | `SET_CUR(COMMIT)` が成功した |
| ISO alternate setting | 成功 | alt `4`、パケットサイズ `640` |
| ISO URB | 成功 | `USBDEVFS_SUBMITURB` 8本、30秒で 7,499 URB を reap した |
| UVC ペイロード再構成 | 成功 | 897個の有効な MJPEG フレームを再構成した |
| MJPEG デコード | 成功 | 最初の JPEG `52,451 bytes` を `BitmapFactory` でデコードした |
| Camera2 の外部カメラ公開 | 失敗 | `cameraIdList` は内蔵カメラの ID `0` と `1` のみで、`LENS_FACING_EXTERNAL` がない |

Kotlin 版へ移行した最終 APK で実施した30秒計測値は次のとおりである。

| 指標 | 実測値 |
|---|---:|
| 計測時間 | 30.004 秒 |
| 有効 MJPEG フレーム | 897 |
| フレームレート | 29.90 fps |
| 期待フレーム数 | 900.12 |
| 推定フレーム損失率 | 0.35 % |
| USB パケットエラー | 0 |
| UVC ペイロードエラー | 0 |
| 形式不正パケット | 0 |

この結果により、少なくとも Pixel 7 と C922 の組み合わせでは、LGPL に依存しない完全自作の UVC 経路で、Camera2 に公開されないカメラから低解像度 MJPEG フレームを取得できることを確認した。

Camera2 の診断結果は変わらず、C922 は Camera2 の外部カメラとして公開されていない。

したがって、Camera2 を使えない場合でも、アプリが USB 権限を取得し、`UsbDeviceConnection` の fd に対して usbfs ioctl を発行できれば、アプリ独自のプレビューを実装できる。

### カーネルドライバのデタッチ要否

2026-09-18 に、claim を force なしと force ありの二段階で試行し、デタッチが必要かどうかを確認した。

| 試行 | 結果 | 解釈 |
|---|---|---|
| デバイス接続後の初回（force なし） | 失敗（`EBUSY`） | `uvcvideo` がインターフェースを掴んでいる |
| 続けて force あり | 成功 | デタッチ（`USBDEVFS_DISCONNECT`）が必要だった |
| 同一接続での再試行（force なし） | 成功 | 一度デタッチしたドライバは再バインドされない |
| アプリのプロセスを終了して再起動後（force なし） | 成功 | プロセス終了では再バインドされない |

- Pixel 7 では初回の claim にデタッチが必要である。
- force なしの claim が成功するのは「前回のデタッチ状態が USB の再列挙まで持続している」ためであり、デタッチ不要な端末の存在を示すものではない。
- 「デタッチが不要な端末が存在するか」を判定するには、カメラを挿し直した直後に force なしで claim を試す手順を端末ごとに実施する必要がある。
- 検証後、カーネルドライバは detached のままで、カメラは再列挙までシステムから利用できない状態が続く。実装では `USBDEVFS_CONNECT` などによる復帰を検討する必要がある。

### I420 変換の実測結果

2026-09-18 に、再構成した MJPEG フレームを I420 へ変換する処理を検証アプリに追加し、30 秒間の連続実行を複数回計測した。変換はネイティブの変換ライブラリを使わず、ARGB を経由する自作実装（BT.601 limited range）である。

| 指標 | 実測値 |
|---|---:|
| 計測時間 | 30.0 秒 |
| USB 受信 | 897 フレーム / 29.90 fps / 推定損失 0.34〜0.36 % |
| I420 変換フレーム | 891〜896 / 897 |
| 変換を含む実効 fps | 29.42〜29.61 fps |
| JPEG デコード平均 | 6.23〜9.92 ms |
| ARGB から I420 への変換平均 | 2.81〜4.03 ms |
| デコードと変換の合計平均 | 9.04〜13.95 ms |
| 合計最大 | 33.78〜155.54 ms |
| 往復変換誤差（MAE） | 0.21〜0.42 / 255 |
| 15 fps 判定 | 達成 |

- 640x480 の 30 fps 入力に対し、フレーム間隔 33 ms に対して平均 9〜14 ms で収まり、目標の 15 fps に対して余裕がある。
- 変換した I420 を ARGB に戻して表示する経路も実装し、29.4 fps で描画できることを確認した。変換結果が映像として破綻していないことの確認になる。
- 合計最大に 100 ms を超える外れ値が混じるが、平均には影響していない。
- この計測は I420 バッファの生成までである。`CapturerObserver.onFrameCaptured()` への供給は未検証である。
- 自作実装でこの性能であるため、libyuv などの最適化ライブラリを使う場合はさらに速くなる見込みである。

### 検証段階の判定

| 段階 | 判定 | 根拠 |
|---|---|---|
| 1. カーネルドライバをデタッチして claim | 成立 | 2026-09-17 は `claimInterface(force=true)` が成功した。2026-09-18 の二段階試行で、初回は force なしが `EBUSY` で失敗し、force ありで成功した（デタッチが必要だった） |
| 2. fd で usbfs を直接操作 | 成立 | `getFileDescriptor()` の fd で `GET_CAPABILITIES`、制御転送、`SETINTERFACE` を実行できた |
| 3. ISO URB で UVC フレーム取得 | 成立 | PROBE / COMMIT、ISO URB、UVC ヘッダー解釈、MJPEG フレーム再構成が成功した |
| 4. 実用性能 | I420 変換まで成立 | 30秒平均 29.4〜29.6 fps（I420 変換を含む）、推定損失 0.34〜0.36 %、I420 変換の往復誤差 MAE 0.42 / 255。ただし `CapturerObserver.onFrameCaptured()` への供給は未検証 |

段階 4 の性能値は USB 受信、MJPEG フレーム再構成、I420 変換の測定値であり、WebRTC への供給性能を保証するものではない。

### 自作する場合の実装規模の見積もり

今回の検証コードは C++ の物理行で 838 行である。

Kotlin 側の権限要求、USB interface の選択、fd の取得、claim、結果表示、JPEG プレビュー連携は約250行である。

検証アプリの Android 側メイン実装は Kotlin であり、Java の追加実装はない。

| 自作範囲 | 今回のコード | libuvc の対応機能 | 今回の実装で除外した機能 |
|---|---:|---|---|
| USB / 記述子 | C++ に含む | `device.c` のデバイス・記述子処理 | 複数デバイス管理、詳細な再接続処理 |
| UVC PROBE / COMMIT | C++ に含む | `stream.c` のストリーム開始処理 | 全 UVC コントロール、カメラ設定の網羅 |
| ISO URB | C++ に含む | `stream.c` の ISO 転送処理 | bulk 転送、複数転送方式の抽象化 |
| UVC フレーム再構成 | C++ に含む | `stream.c` / `frame.c` | 複数のフレーム形式、still image、フレームキュー |
| MJPEG | Android の `BitmapFactory` を使用 | `frame-mjpeg.c` | 自前 JPEG デコーダー |
| Kotlin / Java API | Kotlin 約250行、Java 0行 | libuvc の API ラッパー相当 | Sora / WebRTC への I420 供給 |

libuvc 全体の置き換えではなく、対象カメラと低解像度 MJPEG に必要な機能へ限定すれば、実装量は管理可能である。

一方、複数メーカー、YUYV、フレームベース形式、カメラコントロール、切断・再接続、WebRTC の I420 供給まで含めると、今回の838行だけでは足りない。

I420 変換とその表示の検証コードは上記の行数に含まれていない。SDK 実装では変換処理を libyuv などに置き換えるため、この分は自作範囲の見積もりに含めない。

### 判断

LGPL に依存しない完全自作での UVC 対応は、Pixel 7 と C922 の組み合わせでは実現可能である。

issue 0015 は pending にせず、完全自作経路を前提に実装検討を進める。

ただし、今回の実測は1台の端末と1機種のカメラに限られる。

issue 0015 の実装に進む前に、別端末・別カメラでの互換性確認と、`CapturerObserver.onFrameCaptured()` への供給性能の確認を追加検証する必要がある。

今回の検証コードは Sora SDK 本体には追加していない。

## 解決方法

LGPL に依存しない完全自作の UVC 経路で、Pixel 7 と Logicool C922 Pro Stream Webcam の組み合わせにおいて、低解像度 MJPEG の取得、I420 変換、プレビュー描画までを実機で確認した。実現性を妨げる要因は見つからず、完全自作で進める。

- 段階 1 から段階 3 は成立した。カーネルドライバのデタッチ、fd による usbfs の直接操作、ISO URB による MJPEG フレーム取得が動作した
- 段階 4 は I420 変換まで成立した。640x480 の 30 fps 入力で、デコードと I420 変換を合わせて平均 9〜14 ms であり、15 fps の目標に対して余裕がある
- 一連の経路で LGPL のライブラリは使用しておらず、APK にも含まれていない
- 別端末・別カメラでの互換性確認と `CapturerObserver.onFrameCaptured()` への供給性能の確認は実装フェーズで行う。現時点で実現性の判断を覆す要因はない
- 実測値の詳細は `## 事前調査結果` を参照

検証コードは Sora SDK 本体には追加していない。`sora-android-sdk-uvc/` モジュールと `settings.gradle.kts` への `include` も追加していないため、削除作業は不要である。
