# PeerConnection.Observer に onIceCandidateError のログを追加する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch: feature/add-on-ice-candidate-error-log

## 目的

libwebrtc に追加された `onIceCandidateError` コールバックを `PeerConnection.Observer` で実装し、ICE candidate のエラー情報をログ出力できるようにする。ICE 接続のトラブルシュートを容易にする。

## 優先度根拠

- 既存の ICE 接続処理の挙動を変えるものではなく、デバッグ用のログ追加であるため緊急性は低い。
- 障害調査時の情報が増える利点はあるため対応する価値はあるが、Low とする。

## 現状

`PeerChannel.kt` の `connectionObserver`（`object : PeerConnection.Observer`）には ICE 関連のコールバックとして以下が実装されており、いずれも `SoraLogger.d` でログ出力している。

- `onIceCandidate(candidate: IceCandidate?)`
- `onIceCandidatesRemoved(candidates: Array<out IceCandidate>?)`
- `onIceConnectionChange(state: PeerConnection.IceConnectionState?)`
- `onIceConnectionReceivingChange(received: Boolean)`
- `onIceGatheringChange(state: PeerConnection.IceGatheringState?)`
- `onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?)`

一方、ICE candidate の収集失敗を通知する `onIceCandidateError` はオーバーライドしておらず、エラー発生時に何も記録されない。libwebrtc 側では objc / java 層に ICE candidate error のコールバックが追加されている。

## 設計方針

- `connectionObserver` に `onIceCandidateError` のオーバーライドを追加し、`SoraLogger` でエラー内容（address、port、url、errorCode、errorText など、コールバックが提供する情報）をログ出力する。
- まずはログ出力のみを行う。アプリケーションへのコールバック通知が必要かどうかは別途判断する（本 issue ではログ出力に限定する）。
- `onIceCandidateError` の引数の型（`PeerConnection.IceCandidateErrorEvent` 相当）は実装時に `org.webrtc` の API で確認する。

## 完了条件

- ICE candidate のエラー発生時に `onIceCandidateError` がエラー内容をログ出力すること。
- 既存の ICE 関連コールバックの挙動が変わらないこと。
- `CHANGES.md` の `develop` セクションに該当エントリを追記すること。

## 解決方法
