# redirect 時に DataChannel の状態がリセットされず旧セッションの状態が残る問題を修正する

- Created: 2026-09-10
- Completed: {YYYY-MM-DD}
- Branch: feature/fix-redirect-datachannel-state-reset
- Polished: 2026-09-17

## 目的

Sora の redirect で接続先が切り替わった際に、旧セッションの DataChannel とシグナリング状態が残らないようにし、新セッションで誤ったチャネルへ送信しないようにする。

## 現状

- `SoraMediaChannel.onRedirect` は旧 `signaling` を切断して `connectSignalingChannel` を呼び、新しい `offer` を受信すると `handleInitialOffer` が新しい `PeerChannelImpl` を `peer` に設定する。
- `handleInitialOffer` は `openedMessagingLabels` / `openedDataChannelLabels` / `onDataChannelNotified` をリセットするが、`dataChannels` と `switchedToDataChannel` はリセットしない。
- `dataChannels.clear()` は `internalDisconnect` でのみ実行される。redirect 経路では `SignalingChannelImpl.disconnect` が `receivedRedirectMessage` により `onDisconnect` を発火しないため `internalDisconnect` へ到達せず、旧セッションの `dataChannels` と旧 `peer` が残る。
- 旧 `peer` は `handleInitialOffer` で上書きされ、redirect 経路では明示的に切断されない。切断されない旧 `peer` が後に FAILED / CLOSED へ遷移すると、`PeerChannelImpl` の `onConnectionChange` が `disconnect` を呼び、`listener.onDisconnect` 経由で `SoraMediaChannel.internalDisconnect` が発火して、redirect で開始した新セッションまで終了し得る。
- `switchedToDataChannel` は `handleSwitched` で `true` になるだけで、リセットされない。`switchedIgnoreDisconnectWebSocket` も同様にリセットされない。
- `onDataChannelNotified` は `handleInitialOffer` まで旧値 `true` のまま残るため、redirect 受信から新 `offer` 到達までの間も `sendDataChannelMessage` は旧セッションの `dataChannels` へ送信し得る。また、新セッションの DataChannel が OPEN になるまでの間は `rpc` が `dataChannels["rpc"]` の状態のみで判定するため、旧セッションの `rpc` チャネルへ送信し得る。
- `handleInitialOffer` で `onDataChannelNotified` がリセットされた後は、新セッションの DataChannel が OPEN になるまで `sendDataChannelMessage` は `NOT_READY` になる。ただし `dataChannels` に旧セッションのチャネルが残るため、新セッションで同一ラベルが OPEN になるまでの間に状態が混在し得る。

## 設計方針

- redirect メッセージ受信時 (`SoraMediaChannel.onRedirect`) に、新セッションの接続を開始する前に旧セッションの状態を無効化する:
  - 旧 `peer` を切断し、`dataChannels` をクリアする
  - `onDataChannelNotified` を `false` にし、`openedMessagingLabels` / `openedDataChannelLabels` をクリアする
  - `switchedToDataChannel` を `false` にリセットする（すべての参照箇所である `sendDisconnectIfNeeded` / `signalingListener` の `onDisconnect` / `onError` が「切替完了済み」を条件にしており、新セッション開始時は `false` が正しいことを確認済み）
  - `switchedIgnoreDisconnectWebSocket` も整合のため `false` にリセットする
- `handleInitialOffer` は初回接続と redirect 後の再送の両方で呼ばれるため、上記のリセット処理は `handleInitialOffer` でも実行し、初回接続時 (`peer == null`・空コレクション) は no-op、二重実行でも安全（冪等）にする。
- 旧 `peer` の切断に注意が必要: `PeerChannelImpl.disconnect` は非同期で `closeInternal` を実行し、`listener.onDisconnect` 経由で `SoraMediaChannel.internalDisconnect` に到達する（ガードは `closing` チェックのみ）。この経路を遮断するため、旧 `peer` を切断する前にリスナーを解除する手段（例: `PeerChannel` / `PeerChannelImpl` への `detachListener()` の追加）が必要である。リスナー解除により、旧 `peer` 由来の `onDisconnect` / `onDataChannelClosed` などのコールバックが redirect 後の新セッションへ影響しないことも保証する。
- redirect 後に旧セッションの状態を参照して送信しないよう、送信可否 (`onDataChannelNotified`) と切断経路 (`switchedToDataChannel`) の状態が新セッションの開始時点で一貫するようにする。
- redirect の実機検証が難しい場合は、ユニットテストまたは e2e テストで状態リセットを検証する方法を検討する。

## 完了条件

- redirect 受信後に旧セッションの DataChannel / 状態が残らないこと。
- redirect 後の送信が新セッションの DataChannel に対してのみ行われること（同一ラベルが OPEN になる前は旧チャネルへ送信しないこと）。
- redirect 後に旧 `peer` の切断が新セッションを終了させないこと（新セッションの接続・切断が正常に行われること）。
- `CHANGES.md` の `develop` セクションにエントリを追記すること。

## 変更対象ファイル

- `SoraMediaChannel` の `onRedirect` / `handleInitialOffer` / `internalDisconnect` / `peerListener`
- `PeerChannel` / `PeerChannelImpl` (`PeerChannel.kt`): 旧 `peer` のリスナー解除手段の追加（設計方針のとおりリスナーを解除してから切断する場合のみ）

## 解決方法
