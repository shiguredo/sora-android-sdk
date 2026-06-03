# 複数の PeerConnectionFactory が同時に利用される場合の挙動を調査する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch:

## pending 理由

挙動を確認するための調査タスクであり、需要が顕在化していないため pending とする。

## 目的

複数の `PeerConnectionFactory` が同時に存在し、同時に利用される（各 `PeerConnectionFactory` が生成したストリームがサーバーに接続されている）場合の挙動を調査する。

現状の実装は接続ごとに `PeerConnectionFactory` を生成しているため、複数同時利用時の問題の有無を確認しておきたい。

## 優先度根拠

- 現時点で複数同時利用による具体的な不具合は報告されていない。
- 将来的な実装方針判断のための調査であり、緊急性は低いため Low とする。

## 現状

- `PeerChannel.kt`: `componentFactory.createPeerConnectionFactory(appContext)` により `PeerConnectionFactory` を生成し、`factory` フィールドで保持している。`PeerConnectionFactory.initialize()` はプロセスで一度だけ行う一方、`PeerConnectionFactory` 自体は `PeerChannel`（実質ピア接続）ごとに生成している。
- `RTCComponentFactory.kt`: `createPeerConnectionFactory()` で `PeerConnectionFactory` を組み立てている。
- 複数の `PeerConnectionFactory` を同時利用したときのスレッド・メモリ挙動は未確認である。

## 設計方針

以下の観点を調査する。

- `PeerConnectionFactory` が生成するスレッド群（network_thread、worker_thread、signaling_thread）がそれぞれ複数生成されるのか。
- `PeerConnectionFactory.dispose()` 後にスレッドが残り続けるのか（残ること自体は問題ではないが、メモリリークは問題）。
- メモリリークが発生しないか。
- 映像の送受信に問題がないか。

調査の結果、問題があるようであれば 1 つの `PeerConnectionFactory` を共有する実装への切り替えを検討する。

## 完了条件

- 複数 `PeerConnectionFactory` 同時利用時のスレッド・メモリ挙動を把握すること。
- メモリリークや送受信不良の有無を確認し、実装方針を見直す必要があるかを判断すること。

## 解決方法
