# DataChannel の状態コレクションがスレッドセーフでない問題を修正する

- Created: 2026-09-10
- Completed: {YYYY-MM-DD}
- Branch: feature/fix-datachannel-collections-thread-safety
- Polished: {YYYY-MM-DD}

## 目的

`SoraMediaChannel` が保持する DataChannel の状態コレクションへの並行アクセスを安全にし、シグナリングスレッドとアプリ側スレッドの競合による未定義動作を防ぐ。

## 現状

- `dataChannels: MutableMap<String, DataChannel>` と `openedMessagingLabels: MutableSet<String>` は通常の `mutableMapOf()` / `mutableSetOf()` で保持されている。
- `dataChannels` は `onDataChannelOpen` (libwebrtc のシグナリングスレッド) で追加され、`sendDataChannelMessage` (アプリ側スレッド) で参照され、`internalDisconnect` (任意のスレッド) で `clear()` される。
- `openedMessagingLabels` は `onDataChannelOpen` で追加され、`maybeNotifyDataChannelAvailable` で参照され、`handleInitialOffer` / `internalDisconnect` で `clear()` される。
- これらのコレクションはスレッドセーフではないため、並行する put / get / clear で未定義動作になり得る。
- `onDataChannelNotified` に付与した `@Volatile` は可視性を保証するが、コレクション自体の並行更新は保護しない。

## 設計方針

- `dataChannels` と集合を `ConcurrentHashMap` / `ConcurrentHashMap.newKeySet()` に置き換える、またはアクセスを単一のスレッド / ロックに集約する。
- `clear()` と要素追加が並行しても安全性が保たれることを確認する。
- 既存の `@Volatile` による可視性保証との役割分担を整理する。

## 完了条件

- シグナリングスレッドとアプリ側スレッドから並行してアクセスしても未定義動作にならないこと。
- 既存の送受信・切断・リダイレクトの挙動が変わらないこと。
- `CHANGES.md` の `develop` セクションにエントリを追記すること。

## 変更対象ファイル

- `SoraMediaChannel` の `dataChannels` / `openedMessagingLabels` / `openedDataChannelLabels` とその参照箇所

## 解決方法
