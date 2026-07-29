# ステレオ音声の受信が行えない事象を調査する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch:

## 目的

Sora Android SDK でステレオ音声を受信できない事象の原因を調査し、ステレオ受信を実現するために必要な対応を明確にする。

`useStereoOutput` を有効にしても受信音声がステレオにならない事象が確認されており、原因の切り分けと対応方針の確立を行う。

## 優先度根拠

- ステレオ音声の受信は利用者から求められる機能であり、対応の必要性がある。
- 一方で原因が SDP 生成・libwebrtc・Android の `AudioManager` 設定のいずれにあるかが未確定の調査段階であり、緊急性は中程度のため Medium とする。

## 現状

調査により以下が判明している。

### answer SDP に stereo=1 が付与されない

- `recvonly` で接続した際、answer SDP の opus の `fmtp` 行に `stereo=1` / `sprop-stereo=1` が含まれていない。これによりステレオ受信が行われていない。
- 受信側の `fmtp` 行は `minptime=10;useinbandfec=1` のみとなっており、ステレオ指定が欠落している。
- libwebrtc の WebRTC API では受信側の opus の `fmtp` に `stereo` を付与する手段が用意されていない（W3C webrtc-extensions issue 63 を参照）。このため API 経由ではなく answer SDP を直接書き換える必要がある。

### SDP 書き換えの対象箇所

- answer 生成は `PeerChannel.kt` の `PeerChannelImpl` 内で `createAnswer()` 後に `setLocalDescription()` を呼ぶ箇所が 2 箇所ある。
- ここで `answer.description` の opus の `fmtp` 行を置換し `stereo=1;sprop-stereo=1` を追記してから `SessionDescription` を再生成して `setLocalDescription()` に渡す方法を試行した。
- 書き換えにより Sora 側のログ上の answer SDP には `stereo=1;sprop-stereo=1` が付与されることを確認した。

### 書き換え後も受信音声がステレオにならない

- SDP 書き換え後、`WebRtcAudioTrackExternal` の `initPlayout` ログで `channels=2` が出力されるようになった（書き換え前は `channels=1`）。
- それでもイヤホンでの左右出力確認ではステレオ受信ができていない。再生経路（SDK 外のスピーカー出力や Android の `AudioManager` 設定）が原因の可能性がある。
- Android の音声モードを `AudioManager.MODE_IN_COMMUNICATION` から `AudioManager.MODE_NORMAL` に変更しても受信はステレオにならなかった。
- ステレオ送信自体は行えているが、受信がステレオにならない状態である。

### 関連する設定・コード

- `SoraAudioOption.useStereoOutput` / `useStereoInput`（`SoraAudioOption.kt`）。
- `RTCComponentFactory.kt` の `setUseStereoInput` / `setUseStereoOutput`。
- ステレオ受信判定の参考として、JavaScript SDK は左右の音声の差分を取って判定している。

## 設計方針

- 本 issue は調査までを範囲とする。以下を切り分ける。
  1. answer SDP の opus `fmtp` に `stereo=1` / `sprop-stereo=1` を付与する処理を SDK へ正式に組み込むべきか（API では付与できないため SDP 書き換えが必要）。
  2. SDP 書き換えで `channels=2` になっても受信がステレオにならない原因が、Android の再生経路（`AudioManager` のモードや出力デバイス）にあるのか、libwebrtc 側の再生処理にあるのかを特定する。
- 調査結果を踏まえ、SDK 本体での対応（SDP 書き換えの正式実装）と利用者側での対応（音声モードや出力設定）の切り分けを行い、別 issue として対応方針を立てる。

## 完了条件

- answer SDP に `stereo=1` / `sprop-stereo=1` が付与されない理由と、付与しても受信がステレオにならない原因を特定すること。
- ステレオ受信を実現するために必要な対応（SDK 本体の修正か利用者側の設定か）を結論づけること。

## 解決方法
