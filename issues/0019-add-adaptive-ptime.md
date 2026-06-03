# adaptivePtime に対応する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch: feature/add-adaptive-ptime

## 目的

音声送信における adaptivePtime（適応的パケット化時間）を設定できるようにする。Sora Android SDK にサイマルキャストを実装した時点では `libwebrtc/sdk/android` が adaptivePtime の API を実装していなかったが、その後 libwebrtc 側に対応が入ったため、SDK 側でも利用できるようにする。

## 優先度根拠

- libwebrtc 側は既に対応済みで、SDK 側の対応待ちの状態である。
- 利用者が明示的に求める頻度は高くなく、緊急性は低いため Low とする。

## 現状

- 音声送信の RtpParameters / RtpSender に対して adaptivePtime を設定する経路が SDK に存在しない。
- 映像送信の `degradationPreference` は `PeerChannel.kt` の `updateSenderOfferEncodings` / `configureSenderDegradationPreference` で `RtpSender#getParameters` / `setParameters` を介して設定しており、送信側パラメーターを操作する仕組みは既にある。adaptivePtime も同様の経路で設定できると考えられる。
- `org.webrtc.RtpParameters` には adaptivePtime に対応する API が存在するため、`audioSender`（`PeerChannel.kt` に `private var audioSender: RtpSender? = null` として保持）の `parameters` に対して設定できるかを実装時に確認する。

## 設計方針

- `SoraMediaOption` に adaptivePtime を有効化するオプションを追加する。
- 音声送信用の `RtpSender`（`audioSender`）の `RtpParameters`（`encodings` 等）へ adaptivePtime を設定する。設定対象のフィールドと設定タイミングは、`degradationPreference` の設定経路（`RtpSender#getParameters` で取得し、値を変更してから `setParameters` で適用する）にならって実装する。
- `setRemoteDescription` でパラメーターがリセットされる可能性があるため、`degradationPreference` の再設定と同じ箇所で adaptivePtime も再設定できるかを確認する。

## 完了条件

- `SoraMediaOption` で adaptivePtime を有効化でき、音声送信の RtpParameters に反映されること。
- `CHANGES.md` の `develop` セクションに `[ADD]` エントリを追記すること。

## 解決方法
