# カメラ関連のコールバック関数を追加するか検討する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## pending 理由

カメラ関連のコールバックを追加すべきかどうかの対応可否がまだ検討段階であり、追加する API の範囲も未確定のため pending とする。

## 目的

他社の Android 向けクライアント SDK にカメラに関するコールバック関数が追加されたことを踏まえ、Sora Android SDK でも同等のコールバック関数を追加するかどうかを検討する。

カメラのエラー発生時などをアプリ側で検知できるようにすることで、利用者がカメラ異常時のハンドリングを実装できるようになる。

## 優先度根拠

- 既存機能の不具合ではなく、利便性向上のための機能追加検討であり緊急性は低い。
- 追加するかどうか自体が未確定であり、まず必要性を見極める段階のため Low とする。

## 現状

- Sora Android SDK では `CameraCapturerFactory` が `org.webrtc.CameraVideoCapturer` を生成しており、カメラのイベントハンドラ（`CameraEventsHandler`）は `null` を渡している。
  - `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/camera/CameraCapturerFactory.kt`
    - `findDeviceCamera()` 内で `enumerator.createCapturer(deviceName, null)` のように第 2 引数に `null` を渡している。
- このため、libwebrtc の `CameraVideoCapturer.CameraEventsHandler`（`onCameraError` / `onCameraDisconnected` / `onCameraFreezed` 等）が提供するカメライベントが、SDK の公開 API としては利用者に伝搬されていない。
- Android プラットフォームの `android.hardware.camera2.CameraDevice.StateCallback#onError` のように、カメラデバイス由来のエラーを通知する仕組みがプラットフォームには存在する。

## 設計方針

- 他社 SDK が追加したカメラ関連コールバックの内容を確認し、Sora Android SDK で必要なコールバックの範囲を洗い出す。
- libwebrtc の `CameraVideoCapturer.CameraEventsHandler` が提供するイベントを SDK の公開 API として利用者へ伝搬する形を検討する。
- 既存の `CameraCapturerFactory.create()` の API 互換性を維持したまま、カメライベントを受け取る手段を追加できるかを検討する。

## 完了条件

- カメラ関連のコールバック関数を追加するかどうかの方針が決定されていること。
- 追加する場合は、伝搬すべきカメライベントの種類と公開 API の形が定義されていること。

## 解決方法
