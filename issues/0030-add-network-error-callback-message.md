# ネットワーク切断エラーの内容をコールバックで返却する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch: feature/add-network-error-callback-message

## 目的

URL 不正や Wi-Fi 切断などのネットワーク切断エラーが発生した際に、詳細なエラー内容をコールバック（`onError`）でアプリへ返却できるようにする。

現状はエラーの詳細がログメッセージでしか確認できず、アプリ側でエラー内容を取得して判断できない。アプリがエラー内容に応じた処理を行えるようにする。

## 優先度根拠

- ネットワーク切断時の原因をアプリ側で判別できないため、利用者がエラーハンドリングを実装しにくい。
- 機能としての重要度は高いが、現状でも切断自体は検知でき、エラー内容はログで確認できるため致命的ではない。他 SDK との対応状況も踏まえ Medium とする。

## 現状

WebSocket 切断時の詳細なエラー情報はコールバックに伝搬されておらず、`SoraErrorReason` のみが渡される。

- `SignalingChannel.kt` の `WebSocketListener.onFailure(webSocket, t: Throwable, response: Response?)` で、`t` と `response` の情報はログ出力されるが、コールバックには渡されていない。`listener?.onError(SoraErrorReason.SIGNALING_FAILURE)` のように理由のみが渡される。
  - 同箇所には「`WebSocketListener.onClose` で呼び出す `onError` とはエラーの性質が異なるため、コールバックを分けることを検討する」という TODO コメントがある。
- `SignalingChannel.Listener.onError(reason: SoraErrorReason)` は理由のみを引数に取り、`Throwable` や `Response` を受け取れない。
- `SoraMediaChannel.kt` の `Listener.onError(mediaChannel, reason: SoraErrorReason, message: String)` は `message: String` を取れるが、`onFailure` 経由のエラーでは空文字（`""`）が渡される箇所が複数あり、詳細が反映されていない。

## 設計方針

- `SignalingChannel.onFailure` で得られる `Throwable`（および `Response`）の情報を `SignalingChannel.Listener` 経由で `SoraMediaChannel` まで伝搬させる。
- `SoraMediaChannel.Listener.onError` の `message` に、切断理由を判別できるエラー内容（例外メッセージ等）を渡す。WebSocket 切断時だけでなく、DataChannel 利用時も同様に扱えるようにする。
- 既存の `onError(reason)` と性質の異なるエラー（`onClose` 由来など）の扱いを TODO コメントの方針に沿って整理する。

## 完了条件

- URL 不正・Wi-Fi 切断などのネットワーク切断時に、アプリの `onError` コールバックでエラー内容（例外情報）を取得できること。
- WebSocket 利用時・DataChannel 利用時の双方で詳細なエラー内容が伝搬されること。
- 後方互換のある追加であれば `CHANGES.md` の `develop` セクションに `[ADD]`、API 変更を伴う場合は `[CHANGE]` エントリを追記すること。

## 解決方法
