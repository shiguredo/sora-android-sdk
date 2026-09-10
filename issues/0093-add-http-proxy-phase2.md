# HTTP Proxy 対応 Phase 2（OS 設定の自動参照）

- Created: 2026-09-10
- Completed: {YYYY-MM-DD}
- Branch: feature/add-http-proxy-phase2
- Polished: {YYYY-MM-DD}

## 目的

Sora Android SDK のプロキシ設定が未指定の場合に Android のシステムプロキシ設定を自動参照し、Wi-Fi 設定アプリのプロキシ設定だけでプロキシ経由の接続を可能にする。企業ネットワーク環境での設定コストを削減する。

## 現状

Phase 1 でプロキシを手動指定できるようになっているが、システムプロキシ設定は参照していない。

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/option/SoraProxyOption.kt` が `type` / `agent` / `hostname` / `port` / `username` / `password` を持つ。`type` のデフォルトは `org.webrtc.ProxyType.NONE`
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/signaling/SignalingChannel.kt` は `mediaOption.proxy.type != ProxyType.NONE` のときだけ OkHttp の `Builder.proxy()` と `Builder.proxyAuthenticator()` を設定する
- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/channel/rtc/PeerChannel.kt` の `createPeerConnection` は `mediaOption.proxy.type != ProxyType.NONE` のときだけ `PeerConnectionDependencies.Builder.setProxy()` を呼ぶ
- システムプロキシ設定を読むコードは存在しない

## 設計方針

- `SoraProxyOption.type` が `ProxyType.NONE` の場合にシステムプロキシを参照する分岐を追加する
- システムプロキシは `java.net.ProxySelector.getDefault().select(URI)` で取得する。Android の Wi-Fi 設定の手動プロキシや PAC が `ProxySelector` に反映される
- PAC の解決は `ProxySelector` が担うため、SDK 側で JavaScript を実行しない
- WebSocket (OkHttp) と TURN (libwebrtc) の両方に適用する
  - OkHttp は `Proxy` を明示設定する現状の実装を `ProxySelector` に置き換えられるか確認する
  - libwebrtc の `PeerConnectionDependencies.Builder.setProxy()` は `ProxyType.HTTPS` / `ProxyType.SOCKS5` を受け取るため、`ProxySelector` が返す `java.net.Proxy.Type` を変換する
- `ProxySelector` の結果はネットワーク変更で変わるため、接続のたびに取得する
- 明示設定（`type != ProxyType.NONE`）を優先し、未指定時のみシステム設定にフォールバックする
- 認証付きプロキシは Android のシステム設定から認証情報を取得できないため、従来どおり `SoraProxyOption` の手動設定を使う

## 完了条件

- `SoraProxyOption.type` が `ProxyType.NONE` でも、Android の手動プロキシ設定がある環境で WebSocket と TURN がプロキシ経由で接続できること
- `SoraProxyOption.type` を指定した場合は従来どおり明示設定が優先され、Phase 1 の挙動が変わらないこと
- PAC の自動解決が動作すること（要検証）
- `CHANGES.md` の `## develop` に `[ADD]` エントリを追記すること
