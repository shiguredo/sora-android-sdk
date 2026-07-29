# adaptivePtime に対応する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/add-adaptive-ptime

## 目的

音声送信における adaptivePtime（適応的パケット化時間）を設定できるようにする。libwebrtc 側に対応が入ったため、SDK 側でも利用できるようにする。

## 前提

libwebrtc の `RtpParameters.Encoding` に以下の API が存在することを確認済み:

- フィールド: `boolean adaptiveAudioPacketTime`（`RtpParameters.java:92`）
- getter: `getAdaptivePTime()`（`RtpParameters.java:177`）
- 設定先は `RtpParameters.Encoding` ごと（`RtpParameters` 直下ではない）

## 現状

- `SoraMediaOption.kt:338`: 映像の `degradationPreference` は `RtpParameters.degradationPreference` 経由で設定済み。
- `PeerChannel.kt:211`: `audioSender: RtpSender?` は既に保持されている。
- `PeerChannel.kt:555-567`: `configureSenderDegradationPreference` で `RtpSender.getParameters()` → 変更 → `setParameters()` のパターンが確立済み。
- ただし `handleUpdatedRemoteOffer`（`PeerChannel.kt:417-435`）では `setRemoteDescription` 後のパラメータ再設定が `videoSender` のみで、`audioSender` のパラメータ再設定経路は存在しない。
- `setTrack`（`PeerChannel.kt:437-454`）も `configureSenderDegradationPreference` が video の場合のみ呼ばれており、audio に対するパラメータ設定は未実装。

## 設計方針

- `SoraMediaOption` に `enableAdaptivePtime: Boolean = false` を追加する。`SoraMediaOption` 直下に置く根拠は、`degradationPreference` と同様に `RtpSender` の `RtpParameters` を直接操作するパラメータであるため。
- 設定先は `RtpParameters.Encoding.adaptiveAudioPacketTime`（libwebrtc 側のフィールド名）。
- `configureSenderDegradationPreference` と同様の try-catch パターンで、audioSender 向けのパラメータ設定メソッドを新設する。
- `setTrack` の audio 分岐内で新設メソッドを呼び出す。
- `handleUpdatedRemoteOffer` にも audioSender の再設定呼び出しを追加する。
- `handleInitialRemoteOffer` の audioSender 設定箇所でも同様に呼び出す。

## 完了条件

- `SoraMediaOption.enableAdaptivePtime` を `true` にすると、audioSender の `RtpParameters` に adaptivePtime が設定されること。
- `setRemoteDescription` 後も adaptivePtime が再設定されること。
- `CHANGES.md` の `develop` セクションに以下を追記すること:
  ```
  - [ADD] adaptivePtime を設定できるようにする
    - @担当者
  ```

## 解決方法
