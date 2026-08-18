# androidTest に onDataChannel の発火タイミングを検証する e2e テストを追加する

- Priority: Medium
- Created: 2026-08-06
- Completed:
- Model: DeepSeek V4 Flash
- Branch: feature/add-e2e-on-data-channel-fire-timing
- Polished: 2026-08-17

## 目的

issue 0051 で変更された `SoraMediaChannel.Listener.onDataChannel` の発火タイミングを検証する e2e テストを追加する。0051 の完了条件「単体テストまたは E2E テストで上記の発火タイミングを検証すること」を満たすための受け皿である。

なお、0051 の完了条件のうち本テストの対象外とする項目は以下のとおり（0051 の完了条件の一部として未検証のまま残る点に注意）:

- `type: switched` 受信時には `onDataChannel` が発火しないこと: e2e では決定的な観測手段がない（switched と onDataChannel の相対順序は仕様上保証されない）ため、本テストでは検証しない。
- `onDataChannelOpened` の全ラベル対象（`#` 以外のラベルを含むこと）: 検証可能だが、本テストの目的（`onDataChannel` のタイミング検証）の範囲外であるため検証しない。0051 の実機検証に委ねる。

発火タイミングの仕様は以下のとおり（0051 の実装および実機検証による）:

- `onDataChannel` はメッセージング用ラベル（`#` で始まるラベル）の DataChannel が**すべて**クライアント側で OPEN になった時点で**一度だけ**発火する。
- `type: switched` 受信とは独立に発火し、両者の相対順序は仕様上保証されない（実機では `onDataChannel` が `switched` より先に発火することを確認済み）。
- `onDataChannelOpened` は**全ラベル対象**（`signaling` / `rpc` / `#` 始まりなど、受け取ったすべての DataChannel）で、OPEN になった時点でラベルごとに一度だけ発火する。最後のメッセージング用ラベルの `onDataChannelOpened` と同じコールスタック内で `onDataChannel` が発火するため、両者の順序は決定的である。

## 優先度根拠

`onDataChannel` の発火タイミングはユーザーが「DataChannel を利用できる」と判断する根拠であり、実疎通での検証が必要。発火タイミングの誤りはメッセージング機能の利用開始タイミングの誤解を招く。緊急ではないが 0051 の検証要件を満たすために必要であり、Medium とする。

## 現状

- `SoraMessagingE2ETest.kt` は `onDataChannel` コールバックを利用しているが、メッセージ送受信テストの前提条件として「待機」に使っているだけであり、発火タイミング自体を検証していない。
- `SoraE2ETestBase.createChannel` には `onDataChannelOpened` のフックが存在しない。
- 既存テストの `sendDataChannelMessage` ポーリング（`SoraMessagingE2ETest.kt:207-219`）とそのコメント（同 47-49 行）は、旧タイミング（`handleSwitched()` 内で発火し DataChannel が未 OPEN）を前提とした記述が残っている。

## 設計方針

### 検証設計

switched との相対順序は検証しない（仕様上保証されないため）。代わりに、実装に裏付けられた以下の決定的な順序を検証する。

1. **`onDataChannelOpened` のラベル個別発火**: メッセージング用ラベルごとに `onDataChannelOpened` が一度だけ発火すること。
2. **`onDataChannelOpened` → `onDataChannel` の順序**: 最後のメッセージング用ラベルの `onDataChannelOpened` 発火後に `onDataChannel` が発火すること（同一コールスタック内のため決定的。SoraMediaChannel.kt:1097-1106）。
3. **`onDataChannel` の一度だけ発火**: 発火回数が 1 回であること（`onDataChannelNotified` による二重発火防止の担保）。
4. **`sendDataChannelMessage` の即時成功**: `switched` 受信と `onDataChannel` 発火の**両方を確認した後**、最初の `sendDataChannelMessage()` が `OK` を返すこと。`LABEL_NOT_FOUND` / `INVALID_STATE` のポーリングが不要であること。
   - 注意: `NOT_READY` は `switchedToDataChannel` フラグ（`switched` 受信で true）で決まるため、`switched` 受信を待ってから送信することで発生しない。DataChannel の OPEN 状態とは無関係である（SoraMediaChannel.kt:2112-2114）。
   - 送信先ラベルは、offer から抽出した `#` ラベル集合の**先頭要素**を使用する（定数ラベルに固定しない）。定数に固定すると、offer のラベルが定数と異なるサーバ構成で `LABEL_NOT_FOUND` により誤って失敗するため。

### ラベル構成

- メッセージング用ラベルを **2 つ以上** 指定する（例: `#spam` / `#egg`）。単一ラベルでは「最初のラベル OPEN」と「全ラベル OPEN」を区別できず、`maybeNotifyDataChannelAvailable()` の全ラベル判定（SoraMediaChannel.kt:1110-1128）の退行を検知できないため。
- テストの待ち対象は **offer の `data_channels` から `#` ラベルを動的に抽出して構築する**。テスト内の定数ラベルと offer の `#` ラベル集合が一致しない場合の挙動を未定義にしないため、以下のとおり扱う:
  - offer の `#` ラベルが 2 つ未満の場合: 本テストの前提（全ラベル判定の検証）を満たさないためスキップする。
  - offer の `#` ラベルが定数と異なる場合: `onDataChannel` 発火時点での発火済みラベル集合の検証は、offer から抽出したラベル集合に対して行う（定数に限定しない）。

### テスト構成

- `SoraE2ETestBase.createChannel` に `onDataChannelOpened` フック（`(SoraMediaChannel, String) -> Unit`）を追加し、`SoraMediaChannel.Listener.onDataChannelOpened` から転送する。デフォルト `null` で既存呼び出し元に影響なし。
  - `onDataChannelOpened` は全ラベル対象で発火するため、テストフック側で `label.startsWith("#")` により `#` ラベルのみを検証対象とする（`signaling` / `rpc` 等の OPEN で待ちが誤って完了しないようにする）。
- `SoraMessagingE2ETest.kt` に発火タイミング検証のテストメソッドを追加する。既存の送受信テスト（0070 由来）は**シナリオとアサーションを維持**し、ポーリング実装のみ簡素化する（後述）。
- タイミング検証は単一チャネルで完結するため、基底クラスの `channel` フィールドを使用する（既存の単一チャネル E2E テストと同様。tearDown による切断も基底クラスに任せる）。2 チャネル構成（0070 のローカル変数管理 + finally 切断）は不要である。

### テストフロー

```
1. dataChannelSignaling = true で接続（dataChannels に # ラベルを 2 つ以上指定）
2. offer の data_channels から # ラベル集合を抽出する
   - data_channels が null / 空、または # ラベルが 2 つ未満の場合は skip 判定
   - skip 判定はテストスレッドで行う（コールバックスレッド内で assumeTrue を呼んでもテストへ伝播しない。
     0067 と同様にフラグを立て、テストスレッドの待機ループで判定する）
   - 抽出結果は AtomicReference 等でテスト本体へ公開する（SDK スレッドとテストスレッドで共有されるため）
   - ラベル別の CompletableDeferred はこの時点で登録する（# ラベルの OPEN は switched 受信より先に
     完了し得るため、ステップ 4 の待機時点で登録すると発火済みイベントを取り逃がしてタイムアウトする）
3. switched 受信を待つ（0067 と同様のパターン。onClose / onError が先に来た場合は
   dataChannelSignalingUnsupported 相当のフラグを再確認してから skip 判定する）
4. 各 # ラベルの onDataChannelOpened をラベル別 CompletableDeferred で待つ
   - コールバックスレッドで発火し、フック側で label.startsWith("#") により # ラベルのみを対象とする
   - 各ラベルについて一度だけ発火すること（発火回数を AtomicInteger でラベル別にカウント）
   - 注: フックは # 以外のラベル（signaling / rpc 等）も受け取るが、これらは待ちを完了させない
5. onDataChannel の発火を待つ
   - onDataChannel コールバック内（発火時点・同一コールスタック）で、その時点の発火済み # ラベル集合を
     toSet() で防御的コピーして CompletableDeferred の値としてテスト本体へ渡す
   - complete() の返り値が false の場合（2 回目の発火）は重複発火として AtomicBoolean に記録する
   - テスト本体で await 後に「onDataChannel 発火時点で全 # ラベルの onDataChannelOpened が発火済みであること」を検証する
   - complete() の返り値の記録が 1 回であること（onDataChannel の二重発火がないこと）を確認する
   - 注: コールバックスレッド内で直接 assert しない（例外がテスト本体へ伝播しないため）。スナップショットをテスト本体へ渡してから検証する
   - 注: onDataChannel 発火後に await して確認する実装では、全ラベル判定の退行（先頭ラベル OPEN で発火）を検知できない。発火時点の状態をスナップショットとして受け取ることが必須である
6. sendDataChannelMessage を 1 回呼び（宛先は offer 抽出 # ラベル集合の先頭要素）、
   SoraMessagingError.OK が返ることを確認（ポーリングなし）
```

- タイムアウト値は既存テストを踏襲する: 接続 60s / switched 30s / onDataChannelOpened のラベル待ち全体 10s / onDataChannel 発火待ち 10s / 送信 10s。

### 既存テストのポーリング整理

- 既存テストの `sendDataChannelMessage` ポーリング（`SoraMessagingE2ETest.kt:207-219`）と旧前提のコメント（同 47-49 行）は、新仕様（switched 受信後に送信すれば `NOT_READY` は発生しない）に合わせて簡素化する。シナリオとアサーションは維持し、ポーリング実装のみを単発の `assertEquals(OK)` に置き換える。これにより既存テスト自体が「onDataChannel 発火後は最初の送信で成功する」という新タイミングの検証に転化する。これは 0078 のスコープに含める（同一ファイル・同一機能のテストであり、分離すると新規テストと既存テストで前提が食い違うため）。
  - `onDataChannel` 発火待ちループ（同 200-204 行）と `dataChannelReadyA/B` フラグ（同 49-50 行）は維持する（単発 `assertEquals(OK)` が確実に成功する前提として、`onDataChannel` 発火の待機は引き続き必要）。
- 既存テストのスキップ判定（`SoraMessagingE2ETest.kt:84-90`）も、`#` ラベル不在の場合にタイムアウトではなくスキップとなるよう拡張する。ただし拡張範囲は「data_channels 非存在 / `#` ラベル不在」に限定する。既存テストは単一ラベル（`#messaging`）を使用するため、「`#` ラベルが 2 つ未満はスキップ」という新規テストの基準（後述）は適用しない（適用すると既存テストが常にスキップになり、0070 の検証が失われるため）。

### スキップ判定

- 接続先 Sora が `data_channel_signaling` 非対応（offer に `data_channels` が含まれない）の場合はスキップする（既存パターン踏襲）。
- offer の `data_channels` は存在するが `#` ラベルが含まれない場合、`onDataChannel` は発火しない（SoraMediaChannel.kt:1118-1121）ため、タイムアウトではなくスキップと判定する。
- offer の `#` ラベルが 2 つ未満の場合もスキップする（本テストの前提を満たさないため）。

## 完了条件

- 新規テスト: `onDataChannelOpened` がメッセージング用ラベルごとに一度だけ発火し、**onDataChannel 発火時点で**全メッセージング用ラベルの `onDataChannelOpened` が発火済みであること、`onDataChannel` が一度だけ発火することを検証すること。
- 新規テスト: `switched` 受信と `onDataChannel` 発火の両方を確認した後の最初の `sendDataChannelMessage()` が `OK` を返すこと（`LABEL_NOT_FOUND` / `INVALID_STATE` のポーリングなし）を検証すること。
- 新規テスト: 接続先 Sora が `data_channel_signaling` 非対応、`#` ラベルを含まない場合、`#` ラベルが 2 つ未満の場合はテストがスキップされること。
- 既存テスト: ポーリング簡素化（単発 `assertEquals(OK)` 化）とスキップ判定拡張（`data_channels` 非存在 / `#` ラベル不在）を実施し、既存テストが引き続き完走すること。
- Gradle Managed Device (pixelApi35) で完走すること。
- `CHANGES.md` の `develop` セクションに `### misc` サブセクションを新設し、エントリを追記すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraE2ETestBase.kt`
  - `createChannel` に `onDataChannelOpened` フックを追加
- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraMessagingE2ETest.kt`
  - 発火タイミング検証のテストメソッドを追加
  - 既存テストのポーリング・コメントを新仕様に合わせて簡素化
  - 既存テストのスキップ判定を `#` ラベル不在に対応（「2 つ未満はスキップ」は新規テストのみに適用）
- `CHANGES.md`

## 依存関係

- issue 0051（`onDataChannel` の発火タイミング変更・`onDataChannelOpened` 追加）— 実装済み・マージ済み（コミット dd01135）
- issue 0067（`SoraE2ETestBase`・`onSignalingMessage` フック・switched 待機パターン）— 完了済み
- issue 0070（`SoraMessagingE2ETest.kt` 基盤・`SoraE2ETestBase.createChannel` フック）— 完了済み

## テストの限界

- 新旧実装の識別力: 旧実装（0051 変更前）には `onDataChannelOpened` 自体が存在しないため、本テストは `onDataChannelOpened` 待ちで失敗し、旧実装との区別は決定的である。一方、**送信成功の検証のみでは**新旧実装を区別できない（旧実装でも SCTP 確立が switched 受信より先なら送信が成功し得る）。決定的な検証は「onDataChannel 発火時点で全ラベル OPEN 済み・一度だけ発火」の順序検証であることを認識すること。
- 「一度だけ発火」の検証: 防御的チェック（PeerChannel.kt:430-432）と `onStateChange`（同 404-406）の両方で同じラベルの `onDataChannelOpen` が通知され得るため、`onDataChannelNotified` ガード（SoraMediaChannel.kt:1111）が削除された退行では、最後のラベルの OPEN 通知 2 回目で `onDataChannel` が 2 回発火し得る。本テストは `complete()` の返り値記録によりこの二重発火を検出する。なお、防御的チェックの重複通知が実際に発生するかは libwebrtc の `RegisterObserver` 挙動依存であり、プロジェクト内でも PeerChannel.kt:426 のコメント（即時通知する）と 0051 issue の記述（即時通知しない）が矛盾している点に注意する。

## 解決方法
