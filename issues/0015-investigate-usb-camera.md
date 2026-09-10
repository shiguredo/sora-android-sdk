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

SDK 本体、`sora-android-sdk-samples`、`sora-android-sdk-quickstart` のいずれにも、現時点で UVC / USB カメラ対応の実装は存在しない（2026-09-04 時点）。

一般的な UVC カメラに対応するには、次の構成が現実的である。

1. `UsbManager` で USB デバイスを検出し、利用権限を取得する。
2. UVC 対応ライブラリ（libuvc / UVCCamera 系）で映像フレームを取得する。
3. 取得したフレームを Sora SDK のカスタム映像ソース（`VideoCapturer` 実装）として供給する。

## SDK の既存制約

調査および PoC にあたり、以下の SDK 制約を考慮する必要がある。
なお、`issues/closed/0029-add-dummy-video-capturer-sample.md` の対応で、カスタム `VideoCapturer`（ソフトウェアバッファ、`eglContext = null`、`cameraConfig = null`）による Sora への映像送信はすでに動作確認済みである。以下はこの結果と合わせて前提にできる。

### カスタム VideoCapturer のキャプチャ開始

`enableVideoUpstream(capturer, eglContext, cameraConfig = null)` でカスタム `VideoCapturer` を渡した場合、SDK は `capturer.startCapture()` を自動で呼ばない。`RTCLocalVideoManager.startOwnedCapture()` 内の `isOwnedCapturer == false` の分岐でスキップされる（既定値は `false`）ため、SDK 利用者が自前で接続前または `onAddLocalStream` コールバック後に `startCapture()` を呼ぶ必要がある。また `capturer.dispose()` の解放も SDK 利用者の責任である。

### SurfaceTextureHelper の必須性

`RTCLocalVideoManager.initTrack()` は `SurfaceTextureHelper.create(...)` を呼び出し、そのインスタンスを `capturer.initialize()` に渡している。カスタム実装は `SurfaceTextureHelper` の `handler` をスケジューリングに利用できる（issue 0029 の `DummyVideoCapturer` もこの `handler` を利用している）。UVC カメラの出力は通常ソフトウェアバッファ（NV21 / YUV）であり `SurfaceTexture` を介さないため、`SurfaceTexture` に依存しない実装で足りるか、あるいは `initialize()` に `null` の `SurfaceTextureHelper` を渡すための SDK 側の変更が必要かを検証する。

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

### NV21 → I420 変換

多くの UVC カメラは USB レベルでは MJPEG / YUYV 等を出力し、libuvc / UVCCamera 系ライブラリが NV21 へ変換して提供することが多い。WebRTC の `VideoCapturer` が期待する I420 への変換が必要になる場合があり、この変換はソフトウェア処理となるため、性能面での影響を評価する必要がある。

## 設計方針

SDK 本体への組み込み可否は PoC の結果を踏まえて別途判断する。まず `sora-android-sdk-samples` / `sora-android-sdk-quickstart` 側でカスタム `VideoCapturer` 実装として PoC を進め、再現可能な手順を確立する。成果物は `sora-android-sdk-samples` に残す。

## 調査フェーズ

1. **ライブラリ選定**: UVCCamera 系ライブラリを評価し、PoC に使用する 1 つを選定する。
2. **USB デバイス検出**: `UsbManager` で UVC デバイスを列挙し、権限取得フローを実装する。
3. **フレーム取得**: 選定ライブラリで NV21 フレームを取得し、I420 へ変換するパイプラインを構築する。
4. **VideoCapturer 実装**: `VideoCapturer` インターフェースを実装し、`SurfaceTexture` に依存しない経路で動作することを検証する。
5. **SDK 連携**: `enableVideoUpstream` でカスタム `VideoCapturer` を渡し、Sora サーバーへ映像が送信されることを確認する。
6. **評価**: フレームレート・遅延・CPU 使用率を計測し、実用性を評価する。

## ライブラリ評価基準

以下の観点で UVCCamera 系ライブラリ（saki4510t/UVCCamera、AndroidUSBCamera 等）を評価する。

| 観点 | 説明 |
|---|---|
| メンテナンス状況 | 最終更新日、issue / PR の応答性 |
| 対応 API レベル | minSdk、targetSdk |
| 対応解像度・フレームレート | 出力可能なフォーマット一覧 |
| NV21 → I420 変換 | 内蔵しているか、自前実装が必要か |
| USB 権限処理 | `BroadcastReceiver` / `PendingIntent` 対応状況、ホットプラグ対応 |
| ライセンス | Apache 2.0 互換か |
| 依存ライブラリ | 数・サイズ |
| Android 14+ 対応 | ブロードキャスト・`PendingIntent` の新しい制約への適合 |

## 検証環境

- 検証端末: Android 10 以上の実機
- 推奨カメラ: Logicool C920 / C922（代表的な UVC カメラ）、加えて最低 1 機種以上の別メーカー品
- 注意点: 端末・カメラの組み合わせや給電条件によって UVC の認識可否が変わる。PoC では代表的な組み合わせに限定する。

## PoC 成功基準

以下のすべてを満たすこと。

- USB （UVC） カメラの映像が Sora サーバーに送信され、ブラウザ等で視聴できること。
- 640x480 で 15fps 以上、遅延 500ms 以内の映像送信が安定して行えること。遅延は受信側で映像が表示されるまでのエンドツーエンドとし、計測方法を README に明記すること。
- NV21 → I420 変換を含めたエンドツーエンドのパイプラインが動作すること。
- `SurfaceTexture` 経由でないソフトウェアバッファの映像を `CapturerObserver.onFrameCaptured()` に供給する `VideoCapturer` 実装が `RTCLocalVideoManager` 上で動作すること。

## 完了条件

- `sora-android-sdk-samples` リポジトリの `samples` モジュール（`samples/src/main/kotlin/jp/shiguredo/sora/sample/` 配下。既存の `camera/DummyVideoCapturer.kt` と同様の配置）に動作可能なサンプルコードと、実機での検証・計測手順を記載した README を残すこと。
- 動作確認済みの端末・カメラ機種一覧と既知の制約事項を本 issue の `## 解決方法` セクションに追記すること。
- SDK 本体で対応すべきか、サンプル側のカスタム映像ソースとして提供すべきかの方針を結論づけること。
