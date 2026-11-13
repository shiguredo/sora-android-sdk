# PeerConnection.Observer に onIceCandidateError のログを追加する

- Priority: Low
- Created: 2026-06-03
- Completed:
- Polished: 2026-11-13
- Model: Opus 4.8
- Branch: feature/add-on-ice-candidate-error-log

## 目的

libwebrtc が提供する `onIceCandidateError` コールバックを `PeerConnection.Observer` で実装し、ICE candidate のエラー情報をログ出力できるようにする。ICE 接続のトラブルシュートを容易にする。

## 現状

`PeerChannel.kt` の `connectionObserver`（`object : PeerConnection.Observer`）には ICE 関連のコールバックとして以下が実装されており、いずれも `SoraLogger.d` でログ出力している。

- `onIceCandidate(candidate: IceCandidate?)`
- `onIceCandidatesRemoved(candidates: Array<out IceCandidate>?)`
- `onIceConnectionChange(state: PeerConnection.IceConnectionState?)`
- `onIceConnectionReceivingChange(received: Boolean)`
- `onIceGatheringChange(state: PeerConnection.IceGatheringState?)`
- `onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?)`

ICE candidate の収集失敗を通知する `onIceCandidateError` はオーバーライドしておらず、SDK のログには何も記録されない。

## 設計方針

- `connectionObserver` に `override fun onIceCandidateError(event: IceCandidateErrorEvent)`（`org.webrtc.IceCandidateErrorEvent`）を追加する。デフォルト実装は空のため `super` は呼ばない。
- コールバックが提供する全情報（`address` / `port` / `url` / `errorCode` / `errorText`）を 1 行でログ出力する（匿名化されたループバック表現のイベントは除く）。書式は既存の複数フィールドを持つコールバック（`@onDataChannel` など）と同様の `key=value` 形式とし、次の文字列を出力する。

  ```
  [rtc] @onIceCandidateError address=${event.address}, port=${event.port}, url=${event.url}, errorCode=${event.errorCode}, errorText=${event.errorText}
  ```

  - `IceCandidateErrorEvent` は `toString()` をオーバーライドしていないため、フィールドを個別に文字列化する。
  - `errorText` は空白やカンマを含み得るため末尾に置く。
  - `errorText` はサーバー由来の自由文字列であり、改行を含み得るため `\r\n` / `\r` / `\n` を空白に置換して 1 行にする。
- `address` は libwebrtc の `SocketAddress::HostAsSensitiveURIString()` が返す匿名化済みのローカルアドレスである。IPv4 は先頭 3 オクテットを残して最終オクテットを `.x` に置換し（ループバックは `127.0.0.x`）、IPv6 は先頭 48 bit を残して残りを `x` に置換する（ループバックは `[0:0:0:x:x:x:x:x]`）。実際の IP アドレスではないため `InetAddress` では判定できない。
- `PeerChannelImpl` の `companion object` に `internal fun isLoopbackSensitiveAddress(address: String): Boolean` を追加し、匿名化された文字列をそのまま判定する。
- 匿名化されたループバック表現（`127.*` または `[0:0:0:x:x:x:x:x]`）のイベントは、プロキシ未使用時に限りログ出力しない。ループバックを経由する ICE サーバーへの試行は通常の構成では接続に寄与しないため、トラブルシュートのノイズになる。プロキシ経由の TURN/TCP ではローカルソケットが localhost に束縛されることがあり、その失敗は診断に必要なので除外しない。
- IPv6 は先頭 48 bit しか残らないため、先頭 48 bit が 0 のアドレス（IPv4-mapped など）はループバックと同じ表現になり区別できない。本判定はログのノイズ除去のための近似であり、アドレスの同一性判定には使わない。ローカルの ICE サーバー（`turn:127.0.0.1:3478` など）を指定する構成は対象外とする。
- ログレベルは `SoraLogger.d`（debug）とする。個々の ICE サーバー試行の失敗は正常な接続でも発生するため、warning ではなく診断用のログとして扱う。
- `url` は libwebrtc が ICE サーバーのアドレスから再構成した URL（`turn:<host>:<port>?transport=<proto>` など）であり、設定された URL と完全には一致しない。`username` / `credential` は Sora の `config.iceServers` で別フィールドとして渡しているため、`url` に資格情報は含まれない。
- `address` は匿名化されるため、ログから完全なローカルアドレスは分からない。IPv4 は先頭 3 オクテット、IPv6 は先頭 48 bit とポートが残るため、同じ時刻の libwebrtc の `Net[...]` ログとポートで照合してネットワークを特定する。
- `address` が空、`port` が 0 のイベントがある（TURN/TCP/TLS でサーバーがプライベート IP の場合など）。その場合は `address=, port=0` として出力する。
- 本 issue のスコープはログ出力に限定し、アプリケーションへのコールバック通知追加は別途判断する。

## 完了条件

- ICE candidate のエラー発生時に、`onIceCandidateError` が `address` / `port` / `url` / `errorCode` / `errorText` の 5 つを設計方針の書式で 1 行に debug ログとして出力すること（`SoraLogger.enabled = true` の場合に logcat の `PeerChannelImpl` タグへ出力される）。
- 匿名化されたループバック表現（`127.*` / `[0:0:0:x:x:x:x:x]`）のイベントは、プロキシ未使用時にログ出力されないこと。プロキシ設定時は出力されること。
- `isLoopbackSensitiveAddress` の単体テストが追加され、`127.0.0.x` / `[0:0:0:x:x:x:x:x]` をループバック、`192.168.40.x` / `[2400:4051:1234:x:x:x:x:x]` / 空文字を非ループバックとして検証すること。
- `shouldLogIceCandidateError` の単体テストが追加され、プロキシ未使用時はループバックを出力せず、プロキシ設定時は出力することを検証すること。
- `formatIceCandidateError` の単体テストが追加され、5 つのフィールドの書式と、`errorText` に改行が含まれる場合も 1 行になることを検証すること。
- 既存の ICE 関連コールバックの挙動が変わらないこと。
- `CHANGES.md` の `develop` セクションに、種別の順序（CHANGE → ADD → UPDATE → FIX）を守って以下を追記すること:
  ```
  - [ADD] `PeerConnection.Observer` に `onIceCandidateError` のログ出力を追加する
    - `PeerChannelImpl` の `connectionObserver` で `onIceCandidateError` を実装し、ICE candidate のエラー情報（`address` / `port` / `url` / `errorCode` / `errorText`）を debug ログに出力する
    - `address` は libwebrtc が匿名化したローカルアドレスであり、実際の IP アドレスではない
    - プロキシ未使用時、ループバックを経由する ICE サーバーへの試行に由来するエラーは、通常の構成では接続に寄与しないため出力しない（ローカルの ICE サーバーを指定する構成は対象外）
    - @担当者
  ```

## 解決方法
