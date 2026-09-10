# サンプルと SDK の upstream/downstream 指定を Sora 仕様のロールに統一する

- Priority: Medium
- Created: 2026-09-10
- Completed:
- Branch:

## pending 理由

SDK の公開 API を upstream/downstream ベースから Sora 仕様のロールベースへ移行するか、サンプル側の変換だけをなくすか、後方互換をどう担保するかという設計判断が必要であり、合意が得られていないため pending とする。この判断は `SoraMediaOption.role` の切り戻しを検討している `0046` の結論と、libwebrtc-c への rewrite を検討している `0061` の API 再設計のタイミングにも依存する。

## 目的

`SoraMediaOption` とサンプルアプリのロール指定を、upstream/downstream を経由せず Sora 仕様のロール（`SENDONLY` / `RECVONLY` / `SENDRECV`）で直接行えるように統一する。

現状は利用者が `enableVideoUpstream()` / `enableVideoDownstream()` / `enableAudioUpstream()` / `enableAudioDownstream()` を組み合わせて送受信の有無を指定し、SDK がそこから `requiredRole` を逆算する。ロールは Sora 仕様の語彙で指定するのが本来分かりやすく、送受信フラグの組み合わせからロールを逆算する二重表現をなくしたい。

## 優先度根拠

- サンプルおよび SDK の利用者に見える API の分かりにくさであり、設計判断が長引くほど後方互換を保ったまま直すコストが増える。
- 一方で後方互換のない公開 API 変更であり、`0046` の `role` 切り戻しの結論と `0061` の rewrite の設計に整合させる必要があるため、即時対応ではなく Medium とする。

## 現状

- SDK の `SoraMediaOption` は `enableVideoUpstream()` / `enableVideoDownstream()` / `enableAudioUpstream()` / `enableAudioDownstream()` で送受信の有無を指定し、内部の `upstreamIsRequired` / `downstreamIsRequired` から `requiredRole` を決定する。
- `SoraMediaOption` には `role: SoraChannelRole?` による上書きがあり、`SoraMediaChannel` は `mediaOption.role ?: mediaOption.requiredRole` でロールを決定する。この上書きは messaging only 対応のために追加されたもので、切り戻しは `0046` で検討中である。
- サンプルの `SoraRoleType` は enum 名としては `SENDONLY` / `RECVONLY` / `SENDRECV` を使うが、`hasUpstream()` / `hasDownstream()` を持ち、`SoraVideoChannel` と `SoraAudioChannel` がこれを使って `enableVideoUpstream()` 等へ変換している。
- サンプルの `MessagingActivity` は messaging only のために `mediaOption.role = SoraChannelRole.RECVONLY` を直接設定している。
- 関連する upstream/downstream 由来のフラグ整理は、サンプルでマルチストリーム無効時にも `SENDRECV` が選べてしまう問題として過去に議論された経緯がある。

## 設計方針

- upstream/downstream ベースの API をやめ、Sora 仕様のロールを直接指定する API に統一する方向を検討する。後方互換の担保方法（既存 API の維持、`@Deprecated` 化、メジャーバージョンでの削除など）を整理する。
- 音声・映像の送受信有無とロールの関係を明確にする。`SENDONLY` で映像のみ送信する場合など、メディアの有無とロールの組み合わせが矛盾しない設計にする。
- サンプルの `SoraRoleType` から `hasUpstream()` / `hasDownstream()` を削除し、GUI で選択したロールをそのまま SDK に渡せるようにする。
- `0046` の `role` 切り戻しの結論と整合させる。ロールを SDK の主要な指定方法にするなら、messaging only の扱いも含めて再設計する。
- `0061` の rewrite で API を見直す場合は、その Phase とタイミングを合わせる。

## 完了条件

- SDK のロール指定方法（upstream/downstream ベースを維持するか、ロールベースへ移行するか）の方針が定まること。
- 移行する場合、後方互換の扱いとサンプルの修正方針が整理されていること。
- サンプルの `SoraRoleType` に残る upstream/downstream 変換の扱いが決まっていること。

## 解決方法
