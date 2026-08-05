# ネットワーク切断エラーの内容をコールバックで返却する

- Priority: Medium
- Created: 2026-06-03
- Completed: 2026-08-05
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/add-network-error-callback-message

## 目的

URL 不正や Wi-Fi 切断などのネットワーク切断エラーが発生した際に、詳細なエラー内容をコールバック（`onError`）でアプリへ返却できるようにする。

現状はエラーの詳細がログメッセージでしか確認できず、アプリ側でエラー内容を取得して判断できない。アプリがエラー内容に応じた処理を行えるようにする。

## 優先度根拠

- ネットワーク切断時の原因をアプリ側で判別できないため、利用者がエラーハンドリングを実装しにくい。
- 機能としての重要度は高いが、現状でも切断自体は検知でき、エラー内容はログで確認できるため致命的ではない。他 SDK との対応状況も踏まえ Medium とする。

## 現状

アプリが受信できるエラー経路 (`SoraMediaChannel.Listener.onError(mediaChannel, reason, message)`) 自体は既に存在し、`message: String` も受け取れる。実際に足りないのは、**WebSocket 失敗時に詳細情報が `message` に反映されず、空文字で潰れていること**である。

具体的な欠落箇所は次のとおり。

- `SignalingChannel.kt` の `WebSocketListener.onFailure(webSocket, t: Throwable, response: Response?)` で、`t` と `response` の情報はログ出力されるが、コールバックには渡されていない。`listener?.onError(SoraErrorReason.SIGNALING_FAILURE)` のように理由のみが渡される。
  - 同箇所には「`WebSocketListener.onClose` で呼び出す `onError` とはエラーの性質が異なるため、コールバックを分けることを検討する」という TODO コメントがある。
- `SignalingChannel.Listener.onError(reason: SoraErrorReason)` は理由のみを引数に取り、`Throwable` や `Response` を受け取れない。このため `onFailure` で得た詳細情報を `SoraMediaChannel` へ伝搬する経路がない。
- `SoraMediaChannel.kt` の `Listener.onError(mediaChannel, reason: SoraErrorReason, message: String)` (`SoraMediaChannel.kt:408`) は `message: String` を取れるが、`onFailure` 経由のエラーでは `SignalingChannel.Listener.onError(reason)` しか呼ばれないため、`SoraMediaChannel.kt:944` で `listener?.onError(this@SoraMediaChannel, reason, "")` と空文字が渡され、詳細が反映されない。
  - 空文字が渡される箇所は他にもある (`SoraMediaChannel.kt:1128` の PeerChannel 由来、`SoraMediaChannel.kt:1277` のタイムアウト) が、本 issue の対象は WebSocket の `onFailure` 経路のみとする。

DataChannel 経由のシグナリングのエラー経路は次のとおり。

- `SoraMediaChannel.kt` の DataChannel メッセージ処理 (`onDataChannelMessage`) で例外が発生した場合、`catch (e: Exception)` でログ出力のみ行い、コールバックは呼ばれない。
- DataChannel がクローズされた場合 (`onDataChannelClosed`)、`internalDisconnect(null)` でエラー理由なしに切断する。DataChannel クローズの理由は SDK 側で取得できないため、詳細化の対象外とする。
- WebSocket から DataChannel へシグナリングが切り替わった後 (`switchedToDataChannel`) に WebSocket の `onError` が発生した場合は、`ignoreError` 設定に応じて無視される。

## 設計方針

- `SignalingChannel.Listener.onError(reason: SoraErrorReason)` を `onError(reason: SoraErrorReason, message: String = "")` に拡張する。デフォルト引数により後方互換を維持する。
- `SignalingChannel.onFailure` で得られる `Throwable`（および `Response`）の情報を `message` 文字列に変換して `SignalingChannel.Listener` 経由で `SoraMediaChannel` まで伝搬させる。`Throwable` 自体は渡さず、`message` 文字列に変換する (`SoraMediaChannel.Listener` の API 形状と揃えるため)。
- `message` に含める情報は次のとおりとする。
  - `Throwable`: `toString()` の結果 (`message` のみだと例外の種別が分からないため)。URL 不正なら `UnknownHostException`、TLS 失敗なら `SSLException` など、例外の型が原因判別に有用な情報を含む。
  - `Response`: HTTP ステータスコード (`response.code`) と理由句 (`response.message`)。エンドポイント URL は既に `SoraMediaChannel.Listener.onClose` の `SoraCloseEvent` で取得できるため含めない。
  - `Response.headers` と `Response.body` は含めない。ヘッダーは `Authorization` などの機密情報を含みうるため、本文は長大化や機密情報漏えいのリスクがあるため。
- `message` の組み立ては `response` の有無で次のとおりとする。
  - `response` あり: `"$t (HTTP ${response.code} ${response.message})"` — 例外情報に HTTP ステータスコードと理由句を補足として付ける。
  - `response` なし: `"$t"` — 例外情報のみ (Wi-Fi 切断や URL 不正など、HTTP レスポンスが返らないケース)。
- `message` の最大長の制約は設けない。長大化するケースは実害が出てから検討する (YAGNI)。
- `SoraMediaChannel.Listener.onError` の `message` に、切断理由を判別できるエラー内容（上記の変換結果）を渡す。
- DataChannel 経由のシグナリングでは、メッセージ処理で例外が発生した場合にその例外情報 (`Throwable.toString()`) を `message` として伝搬する。DataChannel クローズ (`internalDisconnect(null)`) は SDK 側で理由を取得できないため対象外とする。
- 既存の `onError(reason)` と性質の異なるエラー（`onClose` 由来など）の扱いを TODO コメントの方針に沿って整理する。

## 完了条件

- URL 不正・Wi-Fi 切断などのネットワーク切断時に、アプリの `onError` コールバックでエラー内容（例外情報）を取得できること。
- WebSocket 利用時は `onFailure` の `Throwable` / `Response` を `message` に変換して伝搬されること。`message` には例外の `toString()` と HTTP ステータスコード・理由句のみが含まれ、ヘッダー・本文は含まれないこと。`response` ありの場合は `"$t (HTTP ${code} ${message})"`、`response` なしの場合は `"$t"` の形式であること。
- DataChannel 利用時はシグナリングメッセージ処理の例外が `message` として伝搬されること。DataChannel クローズ (`internalDisconnect(null)`) は対象外とする。
- 後方互換のある追加であれば `CHANGES.md` の `develop` セクションに `[ADD]`、API 変更を伴う場合は `[CHANGE]` エントリを追記すること。

## 解決方法

- `SignalingChannel.Listener` に `onError(reason, message: String)` を追加した
  - 既存の `onError(reason)` は維持し、新メソッドはデフォルト実装で `onError(reason)` に委譲する形にした
  - これにより既存の外部実装の source / binary 互換を維持している
- `SignalingChannelImpl.WebSocketListener.onFailure` で、`Throwable` と `Response` から `message` を組み立てて伝搬するようにした
  - 組み立ては `buildOnFailureMessage(t, response)` 関数に切り出した (純粋関数として単体テスト可能)
  - `response` あり: `"$t (HTTP ${code} ${message})"`、`response` なし: `"$t"` の形式
  - `response` のヘッダー・本文は機密情報や長大な内容を含みうるため含めない
- `SoraMediaChannel` の `SignalingChannel.Listener` 実装 (`onError`) で、受け取った `message` をそのまま `SoraMediaChannel.Listener.onError` へ伝搬するようにした
- DataChannel 経由のシグナリングでメッセージ処理に失敗した場合 (`onDataChannelMessage` の `catch`)、例外情報 (`Throwable.toString()`) を `message` として伝搬するようにした
- `BuildOnFailureMessageTest` を新規追加した
  - `response` なし: 例外の `toString()` のみが返ること
  - `response` あり: HTTP ステータスコードと理由句が付与されること
  - `response` のヘッダー・本文が `message` に含まれないこと
- `CHANGES.md` の `## develop` セクションに `[ADD]` エントリを追記した
