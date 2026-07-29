# ローカル MediaStream.id に Sora の connectionId を設定できるか調査する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/investigate-local-mediastream-id-connection-id

## 目的

ローカルの `MediaStream.id` に、現状の SDK 生成 UUID ではなく Sora が発行した `connectionId` を設定できるかを調査し、実現可能性と制約を明確にする。

## 優先度根拠

- 機能上の不具合ではなく、リモートとローカルで `MediaStream.id` の意味付けが揃っていないという一貫性の課題である。
- 実現すると `onAddLocalStream` で自身の `connectionId` を取得できるなどの利便性向上が見込めるが、緊急性は低いため Low とする。

## 現状

- ローカルの `MediaStream.id` には SDK が生成した UUID 文字列が入っている。
  - `PeerChannel.kt`: `private val localStreamId: String = UUID.randomUUID().toString()`
  - `PeerChannel.kt`: `factory!!.createLocalMediaStream(localStreamId)` でローカルストリームを生成している。
- リモートの `MediaStream.id` には Sora が発行した `connectionId` が入る。
  - `SoraMediaChannel.kt` の `onAddRemoteStream` では `ms.id == connectionId` の比較を行っており、リモートストリームの id が `connectionId` であることを前提としている。
- `connectionId` は offer メッセージで受信する（`OfferMessage.connectionId`）。`SoraMediaChannel.kt` で `connectionId = offerMessage.connectionId` として保持する。
- ローカルストリームは `createLocalMediaStream(localStreamId)` の時点で id が確定するが、`connectionId` は offer 受信後に判明する。生成タイミングと connectionId 確定タイミングの前後関係が論点となる。

## 設計方針

- ローカルストリームの生成タイミングと offer 受信（`connectionId` 確定）のタイミングを整理し、ローカルストリームの id を `connectionId` に設定できる順序かどうかを確認する。
- `org.webrtc.MediaStream` の id を生成後に変更できるか、もしくは `connectionId` 確定後にローカルストリームを生成する構成へ変更できるかを調査する。
- 変更が困難な場合は、`onAddLocalStream` 経由でアプリ側へ `connectionId` を別途通知する代替案を検討する。
- 既存のローカルストリーム id（UUID）に依存している箇所がないかを確認し、後方互換への影響を評価する。

## 完了条件

- ローカル `MediaStream.id` に `connectionId` を設定できるかどうかを、生成タイミングの制約を踏まえて結論づけること。
- 実現可能な場合は変更方針と後方互換への影響を整理すること。実現困難な場合は代替案を提示すること。

## 解決方法
