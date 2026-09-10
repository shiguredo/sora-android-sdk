# redirect 時に DataChannel の状態がリセットされず旧セッションの状態が残る問題を修正する

- Created: 2026-09-10
- Completed: {YYYY-MM-DD}
- Branch: feature/fix-redirect-datachannel-state-reset
- Polished: {YYYY-MM-DD}

## 目的

Sora の redirect で接続先が切り替わった際に、旧セッションの DataChannel とシグナリング状態が残らないようにし、新セッションで誤ったチャネルへ送信しないようにする。

## 現状

- `SoraMediaChannel.onRedirect` は旧 `signaling` を切断して `connectSignalingChannel` を呼び、新しい `offer` を受信すると `handleInitialOffer` が新しい `PeerChannelImpl` を `peer` に設定する。
- `handleInitialOffer` は `openedMessagingLabels` / `openedDataChannelLabels` / `onDataChannelNotified` をリセットするが、`dataChannels` と `switchedToDataChannel` はリセットしない。
- `dataChannels.clear()` は `internalDisconnect` でのみ実行される。
- 旧 `peer` は `handleInitialOffer` で上書きされ、redirect 経路では明示的に切断されない。
- `switchedToDataChannel` は `handleSwitched` で `true` になるだけで、リセットされない。
- `onDataChannelNotified` がリセットされるため、新セッションの DataChannel が OPEN になるまで送信は `NOT_READY` になる。ただし `dataChannels` に旧セッションのチャネルが残るため、新セッションで同一ラベルが OPEN になるまでの間に状態が混在し得る。

## 設計方針

- redirect で新セッションを開始する際に `dataChannels` をクリアし、旧 `peer` を切断する。
- `switchedToDataChannel` は `sendDisconnectIfNeeded` などの切断経路判定に使われるため、リセットが妥当かを精査してから扱いを決める。
- 送信可否 (`onDataChannelNotified`) と切断経路 (`switchedToDataChannel`) の状態が、新セッションで一貫するようにする。
- redirect の実機検証が難しい場合は、ユニットテストまたは e2e テストで状態リセットを検証する方法を検討する。

## 完了条件

- redirect 後に旧セッションの DataChannel / 状態が残らないこと。
- redirect 後の送信が新セッションの DataChannel に対してのみ行われること。
- `CHANGES.md` の `develop` セクションにエントリを追記すること。

## 変更対象ファイル

- `SoraMediaChannel` の `onRedirect` / `handleInitialOffer` / `internalDisconnect`

## 解決方法
