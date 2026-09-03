# answer SDP の Opus fmtp に stereo=1 / sprop-stereo=1 を追記する処理を組み込む

- Created: 2026-09-03
- Completed:
- Branch: feature/add-android-stereo-audio-receive-sdp
- Polished: 2026-09-03

## 目的

Android のステレオ音声受信を実機で成立させるための対応の一環として、`useStereoOutput = true` が設定された接続の answer SDP に対し、Opus の `fmtp` 行へ `stereo=1;sprop-stereo=1` を追記する処理を SDK 本体に組み込む。0022 の試作では手元での書き換えのみで、SDK 本体には取り込まれていない状態である。

本 issue 単体では実機ステレオ受信は成立しない。0081-add-android-stereo-audio-receive-audio-attributes と併用して初めて実機での受信ステレオを検証できる。

関連 issue: 0022 (ステレオ音声受信の調査), 0076 (androidTest にステレオ音声送受信の e2e テストを追加する)。

## 現状

- libwebrtc の WebRTC API では受信側の Opus `fmtp` に `stereo` / `sprop-stereo` を付与する手段が用意されていない (W3C webrtc-extensions issue 63)
- そのため answer SDP を直接書き換える必要がある
- 0022 の試作では `PeerChannelImpl` の `createAnswer` → `setLocalDescription` の間に SDP を書き換えて `stereo=1;sprop-stereo=1` を追記すると、`WebRtcAudioTrackExternal.initPlayout` のログで `channels=2` が出るところまでは確認できている
- 試作コードは SDK に取り込まれておらず、`useStereoOutput` が有効な接続でも標準では上記書き換えは行われない
- answer SDP を組み立てる箇所は `PeerChannel.kt` (`sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannel.kt`) の `PeerChannelImpl` 内で `createAnswer()` 後に `setLocalDescription()` を呼ぶ 2 箇所 (`handleInitialRemoteOffer` / `handleUpdatedRemoteOffer`)
- 書き換えはクライアント側が answer SDP を組み立てる経路でのみ機能する。クライアント offer 経路 (Sora が answer を組み立てる構成) ではクライアント側に answer SDP が存在しないため、本対応の対象外である

## 設計方針

1. `PeerChannel.kt` に answer SDP の Opus `fmtp` 行を書き換えるヘルパーを追加する
   - 入力: `SessionDescription` と有効化フラグ
   - 出力: `stereo=1;sprop-stereo=1` を追記した新しい `SessionDescription`
   - 対象は Opus の payload type に対応する `a=fmtp:{PT}` のみとし、他 codec の fmtp は変更しない
   - m= 行を跨がず、対象 codec の m= 行以下だけを見る
   - `sprop-stereo` (送信ステレオを許容する宣言) と `stereo` (受信ステレオを希望する宣言) の意味の違いを踏まえたコメントを残す
2. `SoraAudioOption.useStereoOutput` が true の場合のみ書き換えを行う (既定挙動を破壊しない)
3. `PeerChannelImpl` の 2 箇所の `createAnswer` → `setLocalDescription` 経路の両方で書き換え後の SessionDescription を渡す
4. SDP 書き換えのユニットテストを追加する (実機を要さないパーサレベルの検証)
   - 単一 audio m= 行、audio+video 混在、既存 fmtp が空、既存 fmtp に他のパラメータあり、Opus 以外の audio codec (未該当)、audio m= 行が無い、の各ケース
5. `SoraMediaChannel` の設定サマリログに、`useStereoOutput = true` の場合に SDP 書き換えが有効である旨を出力する。実際の書き換え実行時には `PeerChannelImpl` 側で書き換えを適用した旨のログを出力する

## 完了条件

- `useStereoOutput = true` の接続で answer SDP の Opus `fmtp` に `stereo=1;sprop-stereo=1` が付与されること
- `useStereoOutput = false` (既定) では既存挙動どおり書き換えないこと
- 複数 m= 行がある SDP で Opus 以外の fmtp を壊さないこと (ユニットテスト)
- 0081 と併用した実機検証で、Sora が offer を生成する構成の接続で実機イヤホンからステレオ音声が聴こえること (実機検証結果は 0081 側にも記録する)
- CHANGES.md に追記があること

## 変更履歴案

- [ADD] answer SDP の Opus fmtp に stereo=1 / sprop-stereo=1 を追記する処理を組み込む
