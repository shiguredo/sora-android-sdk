# androidTest に RPC (RequestSimulcastRid) の e2e テストを追加する

- Priority: Medium
- Created: 2026-07-13
- Completed:
- Model: DeepSeek V4 Pro
- Branch: feature/add-e2e-rpc-request-simulcast-rid
- Polished: 2026-08-19

## 目的

RPC 機能で simulcast の受信 rid を切り替えられることを e2e で検証する。sora-js-sdk の `e2e-tests/tests/rpc.test.ts` (RequestSimulcastRid) に相当する。

## 優先度根拠

- RPC は Sora 2025.2.0 で追加された実験的機能であり、`rpc()` の実接続における往復と副作用 (rid 切替) は e2e でしか担保できない。
- 依存 issue 0066 (simulcast rid stats) の実装後に着手する前提のため Medium とする。

## 現状

- `SoraMediaChannel.rpc()` は実装済み (SoraMediaChannel.kt:1943) だが、実際の RPC 往復を検証する自動テストがない。
- RPC は「データチャネルシグナリング有効 + 認証時に `rpc_methods` を払い出し」が前提であり、offer の `data_channels` に `rpc` ラベルがあり `rpc_methods` が空でない場合のみ有効化される (SoraMediaChannel.kt:1749-1758)。未払い出しの場合は `rpc()` が `SoraRpcException(NOT_AVAILABLE)` を投げる。
- `rpc()` は `dataChannels["rpc"]` が OPEN でない場合 `SoraRpcException(DATA_CHANNEL_CLOSED)` を投げる (SoraMediaChannel.kt:1965-1967)。そのため RPC 実行前に `rpc` ラベルの DataChannel の OPEN 待機が必要。

## 環境前提

接続先 Sora の設定で `data_channel_signaling` と `data_channel_rpc` が有効であり、接続先サーバが認証時に以下を払い出すこと (js-sdk の先例 `e2e-tests/rpc/main.ts:56-64` に倣う):

- `rpc_methods`: `["2025.2.0/RequestSimulcastRid"]`
- `simulcast`: `true`
- `simulcast_request_rid`: `"r2"`（初期受信 rid。本テストの検証（r0 < r2）が成立する値であること）
- `simulcast_rpc_rids`: `["none", "r0", "r1", "r2"]`

### 着手時の確認タスク

1. 接続先サーバーが上記の払い出し（js-sdk e2e のテストサーバー固有機能、rpc/main.ts:1-7）に対応しているかを確認する。
2. 対応していない場合は、テストサーバー側の対応（JWT の private claim への `rpc_methods` 埋め込み）を別 issue として依頼する。本 issue ではテストサーバー側の対応が完了した環境を前提とする。
3. エミュレータの SW エンコーダ・デコーダで「r0 の受信解像度 < r2 の受信解像度」が実際に成立することを確認する。成立しない場合は、テストの検証方法（解像度差の代替手段）を issue に反映してから着手する。
4. `frameWidth` / `frameHeight` が `getStats()` の `members` に現れることを確認する。現れない場合は `framesDecoded` 等の変化で代替する。
5. `receiver_connection_id` を省略した `{"rid": "r0"}` 形式が接続先 Sora で機能すること（RPC が `SoraRpcResult.Success` を返すこと）を確認する。省略が機能しない場合は、params に `receiver_connection_id` を含める方式に変更する。

## 設計方針

### 検証手段

- rid 切替の効果は **受信映像の解像度変化** で確認する。`getStats()` の `inbound-rtp` の `frameWidth` / `frameHeight` が、RPC 実行前後で変化することを検証する。
- 解像度差の前提: Sora のデフォルトのサイマルキャスト設定では r0 = 一辺 1/4（960x540 なら 240x135）、r2 = 元解像度（960x540）。r0 と r2 の間に明確な解像度差があるため、`frameWidth` / `frameHeight` の比較で切り替えを検出できる。
- `simulcast.switched` 通知の `current_rid` による確認は**行わない**。理由: Android SDK では `onSignalingMessage` の通知対象に `notify` が含まれず (SoraMediaChannel.kt:147-148)、また `NotificationMessage` に `current_rid` / `rpc_rids` フィールドが存在しない (Catalog.kt:198-232) ため、Android 側で `current_rid` を取得できない。

### チャネル構成

- sendonly チャネル: simulcast 有効 (`enableSimulcast()`) + `DummyVideoCapturer` で映像送信。エミュレータでは HW エンコーダの simulcast が使えないため、`softwareVideoEncoderOnly = true` (SIMULCAST_SOFTWARE 経路) を指定する。`capturer.startCapture(width, height, fps)` は接続後に明示的に呼び出す (SoraStatsE2ETest.kt:72-73 のパターン踏襲)。issue 0066 の構成に倣う。
  - **ストリーム 3 本 (r0 / r1 / r2) を出力するための必要最低設定** (Sora ドキュメント SIMULCAST の「解像度とビットレートとストリーム数の関係」に基づく): 解像度 **960x540**、ビットレート **1200 kbps** (`videoBitRate = 1200_000`)。これ未満 (例: 640x360 / 700 kbps) では 2 本以下になり、r2 が立ち上がらないため本テストの前提 (r0 と r2 の両方が立ち上がる) を満たせない。コーデックは VP8 を使用する (VP8 / H.264 の表の値)。
  - 参考: 1280x720 / 2500 kbps でも 3 本出力されるが、SW エンコーダの負荷を抑えるため 960x540 / 1200 kbps を採用する。
- recvonly チャネル: simulcast 受信 + `enableVideoDownstream(null)`（受信デコードの有効化。`inbound-rtp` の `frameWidth` を得るために必須）。初期受信 rid は `enableSimulcast(requestRid)` で指定 (`"r2"`)。RPC はこのチャネルのみで実行する。`dataChannelSignaling = true` は recvonly チャネルのみに指定する（RPC の前提。sendonly には不要）。
- 両チャネルは**同一 Sora ルーム（同一 channelId）**に接続する。
- 2 チャネル管理は issue 0070 で確立したローカル変数管理 + finally 切断のパターンを踏襲する (基底クラスは 2 チャネル非対応のため)。

### RPC 呼び出し

- RPC メソッド名は `2025.2.0/RequestSimulcastRid`。パラメータは `{"rid": "r0"}` の形式 (sora-android-sdk-samples の `SoraVideoChannel.requestSimulcastRid` (facade/SoraVideoChannel.kt:776-779) に倣う)。
  - `receiver_connection_id` は Sora 側で現在接続中のコネクションの値が利用されるため指定不要と想定している (js-sdk は自身の connection_id を明示しているが、Android の `rpc()` は params を無加工で転送するだけであり、サンプルも省略している)。この想定が接続先 Sora で成立するかは着手時の確認タスクで検証する。
- `rpc()` は suspend 関数のため、テストは `runBlocking` 内で呼び出す。
- `rpc()` の戻り値が `SoraRpcResult.Success` であることを検証する。

### テストフロー

```
1. sendonly チャネルを接続 (simulcast 有効 + softwareVideoEncoderOnly)
2. recvonly チャネルを接続 (simulcast 有効 + requestRid + dataChannelSignaling)
3. 両チャネルの onConnect を待つ
4. recvonly チャネルの offer で rpc ラベルと rpc_methods の存在を確認
   (スキップ判定は「スキップ判定」セクション参照)
5. recvonly チャネルの switched 受信を待つ (0067 と同様のパターン)
6. recvonly チャネルの rpc ラベルの onDataChannelOpened を待つ
   (rpc() が DATA_CHANNEL_CLOSED を投げないようにする)
7. capturer.startCapture() を呼び出し、映像送信を開始
8. sendonly チャネルの outbound-rtp を rid 別に分類し（members の rid フィールドで判定。0066 の実装を参照）、
   r0 と r2 の両方で bytesSent > 0 になるまでポーリング (10 回 × 1 秒) する
9. recvonly チャネルの inbound-rtp で frameWidth > 0 になるまで待機 (10s) し、
   安定のため 3 秒待機して初期解像度 (r2) をサンプル (js-sdk rpc.test.ts:67 のパターン)
10. recvonly チャネルで rpc() を呼び出し、rid を r0 に切り替え (SoraRpcResult.Success を確認)
11. 解像度変化をポーリング (10 回 × 1 秒。SoraStatsE2ETest.kt:96-138 のパターン)
12. r0 の frameWidth と frameHeight が初期解像度 (r2) より小さいことを確認
    (js-sdk rpc.test.ts:102-103 の toBeLessThan 相当。width / height 両方を比較)
13. rpc() で rid を r2 に戻し、解像度が初期解像度に戻る（または r0 より大きくなる）ことを
    ステップ 11 と同じポーリングで確認 (js-sdk rpc.test.ts:108-126 のパターン)
```

- タイムアウト値: 接続 60s / switched 30s / rpc DataChannel OPEN 待機 10s / 送信側 rid ポーリング 10s / 初期解像度サンプル 10s / 解像度変化ポーリング 10s。
- 各待機の失敗時（タイムアウト）は、受信側 inbound-rtp の実測値（レポート有無 / bytesReceived / framesDecoded）を含むメッセージで失敗とする。

### スキップ判定

- recvonly チャネルの offer で以下を確認し、満たさない場合はスキップする。`onSignalingMessage` フックで offer を受信し、`AtomicBoolean` で検出してテストスレッドで `assumeTrue(false)` によりスキップする (0067 / 0070 のパターン踏襲):
  - `data_channels` に `rpc` ラベルが含まれる
  - `rpc_methods` に `2025.2.0/RequestSimulcastRid` が含まれる
  - `simulcast` が `true`
  - 注: この確認は **recvonly チャネル**の offer に対して行う（RPC は recvonly でのみ実行するため、recvonly の offer で確認する。sendonly の offer には rpc ラベルが含まれない想定だが、それに依存しない）
- ステップ 8 の送信側 rid ポーリングで、startCapture 後に r0 と r2 の両方で `bytesSent > 0` が観測できない場合のみスキップする（エミュレータ制約による rid 未生成のため）。この場合は RPC の実行を含め**検証は一切行われず**、テスト全体がスキップされる。**両 rid が立ち上がっているのに解像度が変化しない場合は失敗とする**（機能故障の検出）。
  - スキップメッセージには、sendonly outbound-rtp の rid 別 bytesSent の実測値を含め、CI で常時スキップになっていることがログから判別できるようにする。
  - このテストは、エミュレータ制約下（r2 が立ち上がらない環境）では丸ごとスキップされ、rid 切替の回帰検出は両 rid が立ち上がる環境でのみ有効である。GMD (pixelApi35) で r2 が立ち上がらない場合はスキップとなり、実質的な検証は両 rid が立ち上がる別環境（実機等）で行う（完了条件参照）。

## 完了条件

- `rpc()` で rid を切り替え、その効果を受信映像の解像度変化 (inbound-rtp の frameWidth / frameHeight) で確認する e2e テストが追加されていること。r2 に戻した際の解像度復帰も検証すること。
- 前提条件 (rpc_methods 払い出し) を満たさない環境、および送信側で対象 rid が立ち上がらない環境ではテストがスキップされること。対象 rid が立ち上がっているのに解像度が変化しない場合は失敗とすること。
- **両 rid (r0 / r2) が立ち上がる環境（実機等。GMD 以外）で、テストがパスすること（スキップのみでの完了は不可）**。どの環境で実測確認するかは、0066 の実装結果（エミュレータで r2 が立ち上がるか）を確認したうえで決定し、issue に記録する。
- **スキップされない環境（両チャネルが実際に接続される環境）で、複数チャネル同時接続によるネイティブクラッシュ（SIGABRT）が発生しないことを確認すること**（issue 0079 の修正の検証を兼ねる）。
- Gradle Managed Device (pixelApi35) では、テストがスキップなしで完走する場合はパス扱い、スキップする場合はスキップ許容とする（スキップが発生した場合はログで判別可能なこと）。GMD 単体でのパスは完了条件としない。
- `CHANGES.md` の `develop` セクション `### misc` にエントリを追記すること。

## 変更対象ファイル

- `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/SoraRpcE2ETest.kt`（新規）
- `CHANGES.md`

## 依存関係

- issue 0066 (simulcast 送信の e2e 基盤) — open。sendonly チャネルの構成 (softwareVideoEncoderOnly / outbound-rtp の rid 別分類) は 0066 の実装を参照する。0066 の完了条件は「最低でも r0 が送信されること」であり、r2 の立ち上がりは未保証。0066 の実装結果で r2 が立ち上がらないと判明した場合は、本 issue の検証方法を再検討する
- issue 0058 (DummyVideoCapturer) — 完了済み。直接依存
- issue 0070 (2 チャネル構成のローカル管理パターン) — 完了済み
- issue 0067 (onSignalingMessage フック・スキップ判定パターン) — 完了済み

## 解決方法
