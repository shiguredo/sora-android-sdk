# USB 接続カメラ（UVC）への対応方法を調査し PoC を実施する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/add-uvc-camera-support

## 目的

組み込み Android 端末などで利用される USB 接続カメラ（UVC デバイス）の映像を Sora に送信する方法を調査し、カスタム `VideoCapturer` 実装による PoC を実施する。PoC の結果を踏まえて、SDK 本体への組み込み可否を判断する。

## 事前調査結果

一般的な市販 UVC カメラは Android の CameraService に登録されないため、`Camera2` / `CameraX` では扱えない。`CameraX` は `Camera2` の論理カメラのみを扱う。
一部の産業用端末などでメーカーが USB カメラを `Camera2` デバイスとして実装している場合は、例外的に `CameraX` で扱える。

一般的な UVC カメラに対応するには、次の構成が現実的である。

1. `UsbManager` で USB デバイスを検出し、利用権限を取得する。
2. UVC 対応ライブラリ（libuvc / UVCCamera 系）で映像フレームを取得する。
3. 取得したフレームを Sora SDK のカスタム映像ソース（`VideoCapturer` 実装）として供給する。

## SDK の既存制約

調査および PoC にあたり、以下の SDK 制約を考慮する必要がある。

### カスタム VideoCapturer のキャプチャ開始

`enableVideoUpstream(capturer, eglContext, cameraConfig = null)` でカスタム `VideoCapturer` を渡した場合、SDK は `capturer.startCapture()` を自動で呼ばない。`RTCLocalVideoManager.kt:87-88` で `isOwnedCapturer == false` のとき `startOwnedCapture()` がスキップされるため、SDK 利用者が自前で接続前または `onAddLocalStream` コールバック後に `startCapture()` を呼ぶ必要がある。また `capturer.dispose()` の解放も SDK 利用者の責任である。

### SurfaceTextureHelper の必須性

`RTCLocalVideoManager.initTrack()` は `SurfaceTextureHelper.create(...)` を呼び出し、そのインスタンスを `capturer.initialize()` に渡している。UVC カメラの出力は通常ソフトウェアバッファ（NV21/YUV）であり `SurfaceTexture` を介さないため、`VideoCapturer.initialize()` に `null` の `SurfaceTextureHelper` を渡せるか、あるいは代替経路の実装が必要かを検証する。

### CameraVideoCapturer 専用 API の制限

以下の SDK API は `CameraVideoCapturer` であることを前提としており、UVC のカスタム `VideoCapturer` では機能しない。PoC のスコープからは除外する。

| API | 制約 |
|---|---|
| `SoraMediaChannel.switchCamera()` | `RTCLocalVideoManager.kt:119` で `capturer as? CameraVideoCapturer` にキャスト失敗 → 何も起きない |
| `SoraMediaChannel.setVideoHardMute()` | `cameraConfig` が `null` の場合ガードで弾かれる (`SoraMediaOption.canVideoCapturerControllable`) |
| `SoraMediaChannel.changeCaptureFormat()` | `cameraConfig` が `null` の場合 `IllegalStateException` |
| `SoraMediaChannel.startVideoCapture()` | `cameraConfig` が `null` の場合 `IllegalStateException` |

### EGL コンテキストの要否

`enableVideoUpstream(capturer, eglContext, cameraConfig)` の `eglContext` は `PeerConnectionFactory` のエンコーダー初期化で使用される。UVC フレームがソフトウェアバッファ経由の場合、EGL コンテキストを `null` にできるか、あるいは必須かの検証が必要である。

### NV21 → I420 変換

多くの UVC カメラの出力フォーマットである NV21 を、WebRTC の `VideoCapturer` が期待する I420 に変換する必要がある。この変換はソフトウェア処理となり、性能面での影響を評価する必要がある。

## 設計方針

SDK 本体への組み込み可否は PoC の結果を踏まえて別途判断する。まず quickstart / samples 側でカスタム `VideoCapturer` 実装として PoC を進め、再現可能な手順を確立する。

## 調査フェーズ

1. **ライブラリ選定**: UVCCamera 系ライブラリを評価し、PoC に使用する 1 つを選定する。
2. **USB デバイス検出**: `UsbManager` で UVC デバイスを列挙し、権限取得フローを実装する。
3. **フレーム取得**: 選定ライブラリで NV21 フレームを取得し、I420 へ変換するパイプラインを構築する。
4. **VideoCapturer 実装**: `VideoCapturer` インターフェースを実装し、`SurfaceTextureHelper` 不要の経路で動作することを検証する。
5. **SDK 連携**: `enableVideoUpstream` でカスタム `VideoCapturer` を渡し、Sora サーバーへ映像が送信されることを確認する。
6. **評価**: フレームレート・遅延・CPU 使用率を計測し、実用性を評価する。

## ライブラリ評価基準

以下の観点で UVCCamera 系ライブラリ（saki4510t/UVCCamera、AndroidUSBCamera 等）を評価する。

| 観点 | 説明 |
|---|---|
| メンテナンス状況 | 最終更新日、issue/PR の応答性 |
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
- 640x480 で 15fps 以上、遅延 500ms 以内の映像送信が安定して行えること。
- NV21 → I420 変換を含めたエンドツーエンドのパイプラインが動作すること。
- `SurfaceTextureHelper` 不要の `VideoCapturer` 実装が `RTCLocalVideoManager` 上で動作すること。

## 完了条件

- `sora-android-sdk-samples` リポジトリの `samples/` 配下に `uvc-camera/` ディレクトリを作成し、動作可能なサンプルコードと README を残すこと。
- 動作確認済みの端末・カメラ機種一覧と既知の制約事項を本 issue の `## 解決方法` セクションに追記すること。
- SDK 本体で対応すべきか、サンプル側のカスタム映像ソースとして提供すべきかの方針を結論づけること。
