# adaptivePtime に対応する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Polished: 2026-09-25
- Model: Opus 4.8
- Branch: feature/add-adaptive-ptime

## 目的

音声送信における adaptivePtime（適応的パケット化時間）を設定できるようにする。libwebrtc 側に対応が入ったため、SDK 側でも利用できるようにする。

## 前提

libwebrtc の `RtpParameters.Encoding` に以下の API が存在することを確認済み（libwebrtc 150.7871.3.0 の classes.jar で確認）:

- フィールド: `boolean adaptiveAudioPacketTime`
- getter: `getAdaptivePTime()`
- 設定先は `RtpParameters.Encoding` ごと（`RtpParameters` 直下ではない）

## 現状

- `SoraMediaOption.degradationPreference`: 映像の `degradationPreference` は `RtpParameters.degradationPreference` 経由で設定済み。
- `PeerChannel.audioSender`: `audioSender: RtpSender?` は既に保持されている。
- `PeerChannel.configureSenderDegradationPreference`: `RtpSender.getParameters()` → 変更 → `setParameters()` のパターンが確立済み。
- ただし `PeerChannel.handleUpdatedRemoteOffer` では `setRemoteDescription` 後のパラメータ再設定が `videoSender` のみで、`audioSender` のパラメータ再設定経路は存在しない。
- `PeerChannel.setTrack` も `configureSenderDegradationPreference` が video の場合のみ呼ばれており、audio に対するパラメータ設定は未実装。

## 設計方針

- `SoraMediaOption` に `enableAdaptivePtime: Boolean = false` を追加する。`SoraMediaOption` 直下に置く根拠は、`degradationPreference` と同様に `RtpSender` の `RtpParameters` を直接操作するパラメータであるため。
- 設定先は `RtpParameters.Encoding.adaptiveAudioPacketTime`（libwebrtc 側のフィールド名）。
- `configureSenderDegradationPreference` と同様の try-catch パターンで、audioSender 向けのパラメータ設定メソッド（例: `configureSenderAdaptivePtime(sender: RtpSender)`）を新設する。`enableAdaptivePtime` が `false` の場合は何もしない。
- 呼び出し箇所は次の 2 つに限定する:
  - `setTrack` 内: `track.kind() == "audio"` の場合。これにより `handleInitialRemoteOffer` の初期接続と、音声録音一時停止からの復帰（`PeerChannel.updateAudioSenderTrack` 経由の `setTrack`）の両経路で設定される。
  - `handleUpdatedRemoteOffer` 内: `setRemoteDescription` 後の再設定。simulcast 用の `if` / `else` 分岐と無関係に必ず実行される位置に置く（simulcast は映像のみの概念であり、audioSender の設定は分岐の内外どちらにも依存させない）。
- `handleInitialRemoteOffer` には明示的な呼び出しを追加しない（`setTrack` 経由で設定されるため）。

## 完了条件

- `SoraMediaOption.enableAdaptivePtime` を `true` にすると、audioSender の `RtpParameters` の `Encoding.adaptiveAudioPacketTime` が `true` になること。
- `setRemoteDescription` 後（`handleUpdatedRemoteOffer` 経由）も adaptivePtime が再設定されること。
- 音声録音の一時停止からの復帰後にも adaptivePtime の設定が維持されること。
- `CHANGES.md` の `develop` セクションに以下を追記すること:
  ```
  - [ADD] adaptivePtime を設定できるようにする
    - @担当者
  ```

## 解決方法
