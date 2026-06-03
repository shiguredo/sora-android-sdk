# PeerConnection.Observer に onIceCandidateError のログを追加する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/add-on-ice-candidate-error-log

## 目的

libwebrtc に追加された `onIceCandidateError` コールバックを `PeerConnection.Observer` で実装し、ICE candidate のエラー情報をログ出力できるようにする。ICE 接続のトラブルシュートを容易にする。

## 現状

`PeerChannel.kt` の `connectionObserver`（`object : PeerConnection.Observer`）には ICE 関連のコールバックとして以下が実装されており、いずれも `SoraLogger.d` でログ出力している。

- `onIceCandidate(candidate: IceCandidate?)`
- `onIceCandidatesRemoved(candidates: Array<out IceCandidate>?)`
- `onIceConnectionChange(state: PeerConnection.IceConnectionState?)`
- `onIceConnectionReceivingChange(received: Boolean)`
- `onIceGatheringChange(state: PeerConnection.IceGatheringState?)`
- `onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?)`

ICE candidate の収集失敗を通知する `onIceCandidateError` はオーバーライドしておらず、エラー発生時に何も記録されない。

## 設計方針

- `connectionObserver` に `onIceCandidateError` のオーバーライドを追加し、既存の ICE コールバックと同様の書式 `[rtc] @onIceCandidateError: ...` で、コールバックが提供する全情報（address、port、url、errorCode、errorText 等）をログ出力する。
- ログレベルは `SoraLogger.w`（warning）とし、エラー情報であることを明確にする。
- 本 issue のスコープはログ出力に限定し、アプリケーションへのコールバック通知追加は別途判断する。
- `onIceCandidateError` のシグネチャ（引数の型）は libwebrtc の API に従う。

## 完了条件

- ICE candidate のエラー発生時に `onIceCandidateError` がエラー内容をログ出力すること。
- 既存の ICE 関連コールバックの挙動が変わらないこと。
- `CHANGES.md` の `develop` セクションに以下を追記すること:
  ```
  - [ADD] PeerConnection.Observer に onIceCandidateError のログ出力を追加する
    - @担当者
  ```

## 解決方法
