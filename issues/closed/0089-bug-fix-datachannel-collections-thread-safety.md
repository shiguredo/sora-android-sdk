# DataChannel の状態コレクションがスレッドセーフでない問題を修正する

- Created: 2026-09-10
- Completed: 2026-10-28
- Branch: feature/fix-datachannel-collections-thread-safety
- Polished: 2026-10-28

## 目的

`SoraMediaChannel` が保持する DataChannel の状態コレクションがスレッドセーフでないため、libwebrtc のコールバックと切断処理が並行するとコレクションが構造破壊を起こし、要素の消失や参照の不整合に至り得る。3 コレクション (`dataChannels` / `openedMessagingLabels` / `openedDataChannelLabels`) とその更新経路を並行アクセスに対して安全にし、切断処理の完了後に旧セッションの状態が残らないようにする。

## 現状

- `dataChannels: MutableMap<String, DataChannel>` は `mutableMapOf()`、`openedMessagingLabels: MutableSet<String>` と `openedDataChannelLabels: MutableSet<String>` は `mutableSetOf()` で保持されている。いずれもスレッドセーフではない。
- `dataChannels` への追加は `onDataChannelOpen` の 1 箇所だけである。`onDataChannelOpen` は `PeerChannelImpl` の `DataChannel.Observer.onStateChange` と `PeerConnection.Observer.onDataChannel` から呼ばれ、libwebrtc のコールバックとして実行される。
- `dataChannels` の参照は 3 箇所ある。
  - `sendDataChannelMessage` の `dataChannels[label]`
  - `rpc` の `dataChannels["rpc"]`
  - `sendDisconnectOverDataChannel` の `dataChannels["signaling"]` (`internalDisconnect` からのみ到達する)
- `dataChannels` の消去は `internalDisconnect` の `dataChannels.clear()` の 1 箇所だけである。
- `openedMessagingLabels` は `onDataChannelOpen` で追加され、`maybeNotifyDataChannelAvailable` で参照され、`handleInitialOffer` と `internalDisconnect` で `clear()` される。
- `openedDataChannelLabels` は `onDataChannelOpen` で追加され (`add` の戻り値で `onDataChannelOpened` の一度だけ発火を制御している)、`handleInitialOffer` と `internalDisconnect` で `clear()` される。
- `internalDisconnect` に到達する経路は 1 つのスレッドに閉じていない。次の 4 系統がある。
  - 公開 API の `disconnect()` (呼び出し元のスレッド)
  - `signalingListener` の `onDisconnect`。`SignalingChannelImpl.disconnect` が同期的に呼ぶため、呼び出し元は `internalDisconnect` 自身と WebSocket のコールバック (`onFailure` / `onClosed` / `onMessage` のエラー処理 / `closeWithError`、いずれも OkHttp のスレッド) である
  - `peerListener` の `onDisconnect` (`PeerChannelImpl` の単一スレッド executor) と `onDataChannelClosed` (libwebrtc の DataChannel オブザーバコールバック)
  - `onTimeout` (`java.util.Timer` のスレッド)
- `signalingListener` の `onError` は `internalDisconnect` を呼ばない。WebSocket の `onFailure` が `onError` を通知した後に `SignalingChannelImpl.disconnect` を呼び、`onDisconnect` を経由して到達する。
- `onRedirect` 経由では `receivedRedirectMessage` により `onDisconnect` が発火しないため、上記の `signalingListener.onDisconnect` 経路には入らない。`handleSwitched` の遅延 WebSocket 切断も `switchedToDataChannel` と `switchedIgnoreDisconnectWebSocket` により無視される。
- `handleInitialOffer` の `clear()` は `SignalingChannelImpl` の WebSocket コールバック (`onOfferMessage` から `onInitialOffer`) で実行される。
- `closing` は既に `@Volatile` である。ただし `internalDisconnect` の冒頭にある確認と設定は原子的ではないため、`internalDisconnect` 自体が並行実行され得る (この TOCTOU は closed の 0002 で既知であり、未解決のまま残っている)。
- `onDataChannelOpen` には `closing` ガードがない。`internalDisconnect` が `closing = true` を立てた後に `dataChannels.clear()` を実行しても、それと並行する `onDataChannelOpen` は `dataChannels[label] = dataChannel` を実行し得る。追加された `DataChannel` は切断中の `PeerConnection` に属するため、`sendDataChannelMessage` や `rpc` がその `state()` や `send()` を呼ぶと解放済みのネイティブオブジェクトへ触れることになる。
- `sendDataChannelMessage` と `rpc` をどのスレッドから呼ぶかを SDK は規定していない。`SoraMediaChannel.Listener` のコールバック内から送信する利用も想定されるため、「アプリ側スレッド」という前提は置けない。
- `onDataChannelNotified` に付与した `@Volatile` は可視性を保証するが、コレクション自体の並行更新は保護しない。また `maybeNotifyDataChannelAvailable` は `onDataChannelNotified` の確認から代入までを原子的に行わない。

## 設計方針

- `dataChannels` を `ConcurrentHashMap<String, DataChannel>` に置き換える。`openedMessagingLabels` と `openedDataChannelLabels` は `Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())` に置き換える。
  - 本 SDK の `minSdk` は 21 であり、`ConcurrentHashMap.newKeySet()` は Android API 24 以降でしか提供されない。`sora-android-sdk` の `compileOptions` に core library desugaring は設定していないため、`newKeySet()` を使うと API 21 から 23 で `NoSuchMethodError` になる。`Collections.newSetFromMap` は API 21 で利用できる。
  - `PeerChannelImpl` の `trackToStreamId` が、同じく libwebrtc のコールバックと `closeInternal` から並行アクセスされる状態に対して `ConcurrentHashMap` を採用している (`issues/closed/0016` に方針が明記されている)。この前例に揃える。
  - 送信側 (`sendDataChannelMessage` / `rpc`) はロックを取らずに参照できるため、読み取りをブロックしない。
- アクセスを単一のスレッドへ集約する案は採らない。`sendDataChannelMessage` は同期戻り値として `SoraMessagingError` を返し、`rpc` は suspend 関数だが DataChannel の利用可否を戻り値ではなく例外で通知する。読み取りを別のスレッドへ移すと公開 API の契約と `SoraMediaChannel.Listener` のコールバック実行スレッドが変わってしまう。
- `onDataChannelNotified` の `@Volatile` は残す。コレクションのスレッドセーフ化とは独立に、`onDataChannelOpen` から `true` にする書き込みを他のスレッドから参照するために必要である。
- `closing` の宣言は既に `@Volatile` のため変更しない。`internalDisconnect` の TOCTOU (closed の 0002) も本 issue では扱わない。
- 共有可変状態を保護する専用のロック (値を持たない `Any()` のフィールド) を追加し、次の 3 つの区間を同じロックで囲む。`@Volatile` による可視性だけでは、状態の確認から更新までが原子的にならないため。
  - `onDataChannelOpen` の `closing` 確認から 3 コレクションの更新、および `maybeNotifyDataChannelAvailable` の `onDataChannelNotified` の確認と代入まで
  - `internalDisconnect` の 3 コレクションの `clear()` と `onDataChannelNotified = false`
  - `handleInitialOffer` の `openedMessagingLabels` / `openedDataChannelLabels` の `clear()` と `onDataChannelNotified = false`
  - この 3 区間を直列化すると、`onDataChannelOpen` が追加した要素は必ず `internalDisconnect` の `clear()` の前か後になる。前なら `clear()` で消え、後なら `closing = true` を観測して追加しないため、切断処理の完了後に旧セッションの `DataChannel` が `dataChannels` に残らない。同様に `internalDisconnect` の完了後に `onDataChannelNotified` が `true` に戻らない。
  - `handleInitialOffer` の区間も直列化の対象に含めるが、redirect 時に切断されない旧 `peer` のコールバックが新セッションの状態を消し得る問題自体は closed されていない `0088` が扱う。本 issue は区間の直列化までを扱う。
- `Listener` の通知 (`onDataChannelOpened` / `onDataChannel`) はロックの外で行う。通知順は `onDataChannelOpened` が `onDataChannel` より先になる現行の順序を維持する。
- `onDataChannelOpen` の冒頭に `closing` ガードを追加する。`PeerChannelImpl` の `onAddTrack` / `onRemoveTrack` / `onRemoveStream` が `trackToStreamId` の更新前に `closing` ガードを置いている実装に揃える。このガードにより、切断開始後に届いた `onDataChannelOpened` の通知も抑止される。これは利用者から見た挙動の変更であるため、`CHANGES.md` に記載する。
- `clear()` と要素追加が並行しても、`ConcurrentHashMap` と `Collections.newSetFromMap` の各操作は原子的であるため構造破壊は起きない。
- 送信側はロックを取らないため、リセットと交差した場合は API ごとに次の結果になり得る。
  - `sendDataChannelMessage` は `SoraMessagingError` の `NOT_READY` / `LABEL_NOT_FOUND` / `INVALID_STATE` / `PEER_CHANNEL_UNAVAILABLE` / `SEND_FAILED` のいずれかを返し得る。
  - `rpc` は `SoraRpcException` の `SoraRpcErrorReason` として `NOT_AVAILABLE` / `PEER_UNAVAILABLE` / `DATA_CHANNEL_UNAVAILABLE` / `DATA_CHANNEL_CLOSED` / `SEND_FAILED` を投げ得る。
  - `dataChannels` から取得済みの `DataChannel` を保持したまま `state()` / `send()` を呼ぶ窓は残る。これは libwebrtc の Java ラッパーの解放と JNI 呼び出しの競合であり、`0101` の UAF 修正の範囲とする。
  - `sendDataChannelMessage` が `peer` の null 確認をした後に `peer!!` を使う箇所は、`internalDisconnect` の `peer = null` と競合すると `NullPointerException` になり得る。これはコレクションのスレッドセーフ性とは別の欠陥であり、本 issue では扱わない。

## 完了条件

- `dataChannels` / `openedMessagingLabels` / `openedDataChannelLabels` への全アクセス (追加・参照・`clear()`) がスレッドセーフなデータ構造の上で行われ、上のロックで囲むべき 3 区間が実際に囲まれていることをコードレビューで確認できること。対象経路は `onDataChannelOpen` / `maybeNotifyDataChannelAvailable` / `handleInitialOffer` / `internalDisconnect` / `sendDataChannelMessage` / `rpc` / `sendDisconnectOverDataChannel` のすべてとする。
- `internalDisconnect` がどのスレッドから呼ばれても、`dataChannels` / `openedMessagingLabels` / `openedDataChannelLabels` の更新と競合して構造破壊が起きないこと。
- 切断処理の完了後に、旧セッションの `DataChannel` が `dataChannels` に残らず、`openedMessagingLabels` / `openedDataChannelLabels` が空であり、`onDataChannelNotified` が `true` にならないこと。
- 切断開始後に `onDataChannelOpen` の呼び出しが始まった場合、`dataChannels` へ追加されず `onDataChannelOpened` も通知されないこと。
- `SoraMessagingE2ETest` の `DataChannelメッセージングで2チャネル間の送受信ができること` と `onDataChannelとonDataChannelOpenedの発火タイミングが検証できること`、`SoraRpcE2ETest` の `RPCでsimulcastの受信ridを切り替えて解像度が変化すること`、`SoraCloseTypeE2ETest` の `DataChannelシグナリングのみの切断経路がDataChannelであることを検証すること` が CI で成功すること。
- リダイレクトは androidTest にテストが存在しないため、`onRedirect` から `handleInitialOffer` に至る経路に回帰がないことをコードレビューで確認すること。
- `CHANGES.md` の `develop` セクションにエントリを追記すること。切断開始後に `onDataChannelOpened` を通知しなくなる挙動の変更も記載すること。

## 変更対象ファイル

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/SoraMediaChannel.kt`
  - `dataChannels` / `openedMessagingLabels` / `openedDataChannelLabels` の宣言 (スレッドセーフなデータ構造への置き換え)
  - 共有可変状態を保護するロック (新設)
  - `onDataChannelOpen` (`closing` ガードとロック区間の追加)
  - `maybeNotifyDataChannelAvailable` (`onDataChannelNotified` の確認と代入のロック内への移動、通知のロック外への分離)
  - `internalDisconnect` と `handleInitialOffer` (リセット処理のロック区間への移動)
  - `closing` は参照のみ (宣言は変更しない)
- `CHANGES.md`
- 次の共有可変状態は本 issue の対象外とする。
  - `dataChannelsForMessaging` と `rpcEnabled`: 共有コレクション自体を更新するのではなく、単一フィールドの可視性の問題である。コレクションの構造破壊とは別の欠陥のため対象外とする
  - `rpcPendingResponses`: 全アクセスが `synchronized(rpcPendingResponses)` で保護済みである
  - `peer` の null 確認と `peer!!` の間の競合 (上記)

## 関連 issue

- `0088`: redirect 時に `dataChannels` などをリセットする issue。本 issue と同じフィールドを触るが、あちらはリセット漏れと旧 `peer` の切断を、こちらはアクセスの安全性を扱う。本 issue を先に実施し、0088 の着手時に `internalDisconnect` と `handleInitialOffer` のロック区間を再確認すること。0088 が先にマージされる場合は、本 issue の `## 現状` にある消去箇所を再確認すること。
- `0090`: DataChannel の利用可否の判定基準を統一する issue。`onDataChannelNotified` の扱いに触れるため、本 issue では `@Volatile` を外さず、判定基準の統一は 0090 に委ねる。
- `0101`: libwebrtc の Android UAF 対策を取り込む issue。送信側が `DataChannel` の参照を保持したままリセットと交差する窓は、`0101` の範囲とする。

## 解決方法

### 実装

- `dataChannels` を `ConcurrentHashMap<String, DataChannel>` に変更した
- `openedMessagingLabels` と `openedDataChannelLabels` を `Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())` に変更した
  - `ConcurrentHashMap.newKeySet()` は Android API 24 以降でしか提供されない。`minSdk` は 21 で core library desugaring も導入していないため使えない
- `dataChannelStateLock` を追加し、次の 3 区間を同じロックで直列化した
  - `onDataChannelOpen` の `closing` 確認から 3 コレクションの更新、および `onDataChannelNotified` の確認と代入まで
  - `internalDisconnect` の 3 コレクションの `clear()` と `onDataChannelNotified = false`
  - `handleInitialOffer` の `openedMessagingLabels` / `openedDataChannelLabels` の `clear()` と `onDataChannelNotified = false`
- `onDataChannelOpen` の冒頭に `closing` ガードを追加し、切断開始後に届いた OPEN 通知では状態を更新しないようにした
- `Listener` の通知はロックの外で行い、`onDataChannelOpened` が `onDataChannel` より先という順序は維持した
- `maybeNotifyDataChannelAvailable` を、ロックを保持した状態で呼ぶ `tryMarkDataChannelNotifiedLocked` に変更した。通知は行わず、通知の要否だけを返す
- 2 箇所に重複していたリセット処理を `resetDataChannelNotificationStateLocked` に切り出した
- `closing` の宣言は既に `@Volatile` だったため変更していない

### 挙動の変更

- 切断処理が開始済みの場合は `onDataChannelOpened` と `onDataChannel` を通知しない
- 最後のメッセージング用 DataChannel の `onDataChannelOpened` を通知する時点で `onDataChannelNotified` が立つため、その時点から `sendDataChannelMessage` が成功する

### テスト

- `sora-android-sdk/src/test/kotlin/jp/shiguredo/sora/sdk/channel/SoraMediaChannelDataChannelStateTest.kt` を追加した
  - `closing` のときに届いた OPEN 通知は状態を更新しないこと
  - すべてのメッセージング用ラベルが OPEN になると `onDataChannelOpened` のあとに `onDataChannel` が一度だけ通知されること
  - メッセージング用でないラベルが OPEN になっても `onDataChannel` を通知しないこと
  - 切断処理の完了後は DataChannel の状態が残らず、その後に届いた OPEN 通知でも状態が復活しないこと
- `org.webrtc.DataChannel` の public な `DataChannel(long)` を使うとネイティブ呼び出しを伴わずにインスタンスを生成できるため、`peerListener` をリフレクションで取得して `onDataChannelOpen` を直接呼ぶ形で状態遷移を検証している。モックやスタブは利用していない
- 並行インターリーブそのものは決定的に再現できないためテストでは検証せず、データ構造とロックの設計で担保する

### 確認

- `./gradlew ktlintCheck build` が成功し、ユニットテスト 89 件が成功した
- `CHANGES.md` の `develop` セクションに `[FIX]` エントリを追記した
- E2E テストは Sora サーバとテスト端末が必要なためローカルでは実行していない。CI (`pixelApi35` の Gradle Managed Device) で確認する

### 対象外

- 切断開始後に通知が抑制される範囲は、`onDataChannelOpen` の呼び出しが切断開始後に始まった場合に限る。ロックを解放した後に切断が始まる競合では通知されることがある (公開 KDoc に明記している)
- `dataChannelsForMessaging` と `rpcEnabled` の可視性、`sendDataChannelMessage` の `peer` 参照競合、送信側が保持した `DataChannel` の解放競合は本 issue の対象外とする
