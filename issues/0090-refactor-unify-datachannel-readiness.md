# sendDataChannelMessage と rpc で DataChannel 利用可否の判定基準を揃える

- Created: 2026-09-10
- Completed: {YYYY-MM-DD}
- Branch: feature/refactor-unify-datachannel-readiness
- Polished: {YYYY-MM-DD}

## 目的

`SoraMediaChannel` で DataChannel が利用可能かどうかの判定基準が API ごとに異なる状態を整理し、保守時に混乱しないようにする。

## 現状

- `sendDataChannelMessage` は、メッセージング用 DataChannel がすべて OPEN になったことを表す `onDataChannelNotified` で判定する。
- `rpc` は `dataChannels["rpc"]` の `state()` が OPEN かどうかで判定する。
- メッセージングと RPC で対象ラベルが異なるため用途自体は別だが、「DataChannel が使える条件」の定義が 2 系統あるため、片方を変更したときに他方との整合が読み取りにくい。

## 設計方針

- 用途の違いを踏まえ、DataChannel の利用可否を判定する共通の述語に統一できるか検討する。
- 統一しない場合は、それぞれの判定意図（メッセージングは全ラベル、RPC は rpc ラベル単体）をコードコメントまたは KDoc で明示する。
- 既存の公開 API の挙動は変えない。

## 完了条件

- DataChannel 利用可否の判定基準の関係がコードまたはドキュメントから読み取れること。
- 既存の送受信・RPC の挙動が変わらないこと。
- `CHANGES.md` の `develop` セクションにエントリを追記すること（機能に直接影響しない場合は `### misc`）。

## 変更対象ファイル

- `SoraMediaChannel` の `sendDataChannelMessage` / `rpc`

## 解決方法
