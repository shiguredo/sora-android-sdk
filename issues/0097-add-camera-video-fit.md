# 送信するカメラ映像の出力サイズ指定と contain / cover に対応する

- Created: 2026-09-10
- Completed: {YYYY-MM-DD}
- Branch: feature/add-camera-video-fit
- Polished: {YYYY-MM-DD}

## 目的

送信するカメラ映像の出力サイズを指定し、カメラ映像をそのサイズへどのように収めるかを CSS の object-fit 相当の contain / cover で選べるようにする。利用者から、送信するカメラ映像のサイズと縦横比の扱いを指定したいという要望が挙がっている。カメラの取得サイズの縦横比と、アプリが送信したい映像の縦横比が一致しない場合に、切り取り (cover) と余白 (contain) の扱いを SDK 側で統一できるようにする。

## 現状

- `SoraMediaOption.SoraCameraConfig` で指定できるのは取得サイズ (`width` / `height`)、フレームレート、フロントカメラの優先、初期ハードミュートのみで、送信する映像の出力サイズや縦横比を指定する API は無い。
- `RTCComponentFactory.createVideoManager` は `CameraCapturerFactory.create` で生成した `CameraVideoCapturer` を `RTCLocalVideoManager` に渡す。
- `RTCLocalVideoManager.initTrack` は `SurfaceTextureHelper` と `VideoSource` を生成し、`capturer.initialize(surfaceTextureHelper, appContext, source.capturerObserver)` でカメラのフレームを `VideoSource` へ直接渡す。`startCapture(width, height, fps)` は取得サイズを指定するだけで、送信フレームの変換は行わない。
- `VideoFrame.Buffer.cropAndScale(cropX, cropY, cropWidth, cropHeight, scaleWidth, scaleHeight)` が利用できる。`TextureBufferImpl.cropAndScale` は `applyTransformMatrix` を使うため I420 変換を伴わない。
- `VideoSource.adaptOutputFormat` は指定サイズへの縮小と縦横比に合わせた切り取り (cover 相当) を行うが、SDK からは呼ばれていない。contain の余白生成と拡大には対応していない。
- `VideoSource.setVideoProcessor` は `VideoProcessor` にフレームを渡せるが、フレームを置き換えて `VideoSource` へ戻す用途には使えない。
- pending の `0045-add-rotate-video-to-follow-device-orientation.md` で、回転に加えてサイズ指定や切り取りまで対応範囲に含めるかが未決のまま言及されている。

## 設計方針

- `SoraMediaOption.SoraCameraConfig` に映像の出力サイズ (`width` / `height`) とフィットモード (`contain` / `cover`) を追加する。出力サイズ未指定時は取得サイズをそのまま送信し、現状の挙動を変えない。
- `RTCLocalVideoManager.initTrack` で `capturer.initialize` に渡す `CapturerObserver` をラップし、`onFrameCaptured` でフレームを変換してから `VideoSource` の `CapturerObserver` へ渡す。start / stop はそのまま転送し、既存の `VideoSource` と `VideoTrack` の構成を変えない。
- cover は、フレームの回転を考慮した表示サイズと出力サイズから中央基準の切り取り矩形を計算し、`VideoFrame.Buffer.cropAndScale` で出力サイズへ拡大縮小する。
- contain は、フィットする矩形を計算した上で、出力サイズの `JavaI420Buffer` を黒で塗り、フィットした映像を中央にコピーする。出力サイズが変わらない間はバッファを使い回す。contain は I420 への変換を伴うため、性能要件によっては GPU 上での合成を検討する。
- カメラフレームは `rotation` を持つため、切り取り矩形は `VideoFrame.getRotatedWidth` / `getRotatedHeight` を基準に計算し、変換後の `VideoFrame` には元の `rotation` とタイムスタンプを引き継ぐ。
- 変換は `RTCLocalVideoManager` のフレーム経路に追加し、出力サイズが指定された場合は利用者提供の `VideoCapturer` を含めて適用する。未指定時は従来どおり変換しない。
- 端末の向きに追随する回転は本 issue の対象外とする。

## テスト方針

- contain / cover の切り取り矩形と出力サイズを計算するロジックを internal に切り出し、`sora-android-sdk/src/test` から既知サイズ・回転のフレームを与えて検証する。モックやスタブは使用しない。
- 縦横比が一致する場合 (切り取り・余白が発生しない場合) と一致しない場合の両方で、切り取り矩形と出力サイズが期待どおりになることを検証する。
- `DummyVideoCapturer` (androidTest) を使い、実カメラなしで変換後のフレームが `VideoSource` へ渡ることを確認する。
- 実機のカメラで縦持ち・横持ちの両方を確認し、送信側と受信側で指定サイズと余白・切り取りが意図どおりになることを確認する。
- 出力サイズ未指定時に送信フレームのサイズが変わらないことを確認する。

## 完了条件

- `SoraCameraConfig` でカメラ映像の出力サイズと contain / cover を指定できること。
- cover でカメラ映像が指定サイズいっぱいに拡大縮小され、はみ出した部分が切り取られて送信されること。
- contain でカメラ映像の全体が指定サイズ内に収まり、残りの領域が黒くなって送信されること。
- 出力サイズを指定しない場合の送信フレームのサイズと既存 API の挙動が変わらないこと。
- 公開 API の KDoc を追加すること。
- `CHANGES.md` に追加を記載すること。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/option/SoraMediaOption.kt`
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/RTCLocalVideoManager.kt`
- `sora-android-sdk/src/test/kotlin/jp/shiguredo/sora/sdk/` (追加するテスト)

## 関連 issue

- `0045`: 端末の向きに追随する回転の要否を扱う。本 issue はサイズ指定と contain / cover を扱い、回転は対象外とする。
- `0043`: 映像加工の拡張ポイントをどうするかを扱う。本 issue で `CapturerObserver` のラップを導入するため、拡張ポイントの整理が必要になる。

## 解決方法
