# sendDataChannelMessage と rpc で DataChannel 利用可否の判定基準を揃える

- Created: 2026-09-10
- Completed: {YYYY-MM-DD}
- Branch: feature/refactor-unify-datachannel-readiness
- Polished: 2026-09-17

## 目的

`SoraMediaChannel` で DataChannel が利用可能かどうかの判定基準が API ごとに異なる状態を整理し、保守時に混乱しないようにする。

## 現状

- `sendDataChannelMessage` は、メッセージング用 DataChannel（`#` で始まるラベル）がすべて OPEN になったことを表す `onDataChannelNotified` が `false` なら `SoraMessagingError.NOT_READY` を返し、対象ラベルの `dataChannels[label]` が存在しないなら `SoraMessagingError.LABEL_NOT_FOUND`、`state()` が OPEN でないなら `SoraMessagingError.INVALID_STATE` を返す。
- `rpc` は、`rpcEnabled`（`configureRpc` が offer の `rpc` ラベルと `rpc_methods` の有無から決定する）が `false` なら `SoraRpcException(NOT_AVAILABLE)` を投げ、`dataChannels["rpc"]` が存在しないなら `SoraRpcException(DATA_CHANNEL_UNAVAILABLE)`、`state()` が OPEN でないなら `SoraRpcException(DATA_CHANNEL_CLOSED)` を投げる。
- どちらも最終的に対象ラベルの DataChannel の `state()` が OPEN かどうかを確認しており、「DataChannel が使える条件」の差は前段のゲート（メッセージングはメッセージング用ラベルすべてが OPEN であること、RPC は機能が有効であること）にある。
- メッセージングと RPC で対象ラベルが異なるため用途自体は別だが、上記の差が共通の述語として明示されていないため、片方を変更したときに他方との整合が読み取りにくい。

## 設計方針

- `state()` が OPEN かどうかの確認を共通の述語に統一できるか検討する。共通述語にできるのは対象ラベルの DataChannel の OPEN 確認までであり、前段のゲート（メッセージングはメッセージング用ラベルすべての OPEN、RPC は `rpcEnabled`）は用途ごとに残ることを前提とする。
- 統一しない場合は、それぞれの判定意図（メッセージングはメッセージング用ラベルすべてが OPEN になったこと、RPC は rpc ラベル単体が OPEN になったこと）をコードコメントまたは KDoc で明示する。
- 既存の公開 API の挙動は変えない。

## 完了条件

- DataChannel 利用可否の判定基準（共通述語の有無と前段のゲートの差）がコードまたはドキュメントから読み取れること。
- 既存の送受信・RPC の挙動が変わらないこと。
- `CHANGES.md` の `develop` セクションにエントリを追記すること（機能に直接影響しない場合は `### misc`）。

## 変更対象ファイル

- `SoraMediaChannel` の `sendDataChannelMessage` / `rpc`

## 解決方法
