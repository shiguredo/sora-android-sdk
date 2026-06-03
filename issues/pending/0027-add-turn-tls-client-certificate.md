# TURN-TLS でクライアント証明書を指定できるようにする

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Status: pending
- Model: Opus 4.8
- Branch: feature/add-turn-tls-client-certificate

## pending 理由

libwebrtc の公開 Android API に TURN-TLS 接続時のクライアント証明書提示経路が存在しない。以下の既存 API を調査した結果:

- `SSLCertificateVerifier` (`SSLCertificateVerifier.java`): `verify(byte[] certificate)` のみ。サーバー証明書検証用であり、クライアント証明書提示のインターフェースではない。
- `PeerConnection.IceServer` (`PeerConnection.java:168`): `tlsCertPolicy`（`TlsCertPolicy` 列挙）フィールドと `TlsAlpnProtocols` フィールドは存在するが、クライアント証明書を渡すフィールドはない。
- `PeerConnectionDependencies` (`PeerConnectionDependencies.java`): `SSLCertificateVerifier` の設定のみ。クライアント証明書注入経路はない。

libwebrtc の公開 API が拡張されるまで実現不可能なため、pending とする。

## 目的

TURN-TLS の接続でクライアント証明書（およびクライアント秘密鍵）を指定できるようにし、相互 TLS 認証を行う TURN サーバーへ接続できるようにする。libwebrtc 側の API 拡張待ち。

## 現状

クライアント証明書は WebSocket（シグナリング）側にのみ適用されており、TURN-TLS 側には適用されていない。

- `SoraMediaChannel.kt`: コンストラクタ引数 `clientCertificate: X509Certificate? = null` を持つが、`PeerChannelImpl` 生成時 (`handleInitialOffer`, line 1233-1247) に `clientCertificate` を渡していない。
- `PeerChannel.kt`: コンストラクタに `clientCertificate` / `clientPrivateKey` パラメータが存在しない (line 146-156)。
- `RTCComponentFactory.kt`: 同様に `clientCertificate` パラメータがない (line 17-23)。`createSSLCertificateVerifier()` では `caCertificate` のみ渡している。
- 一方、シグナリング側では `SignalingChannelImpl` に `clientCertificate` / `clientPrivateKey` が渡され、OkHttp の TLS 設定に反映されている。

## 設計方針

- libwebrtc API 調査でクライアント証明書提示経路が確認できた場合のみ以下を実施する。
- 基本方針は CA 証明書指定機能の実装に合わせ、`SoraMediaChannel` → `PeerChannelImpl` → `RTCComponentFactory` → libwebrtc の経路でクライアント証明書を伝搬させる。
- WebSocket 側と同じ証明書・秘密鍵を TURN-TLS にも適用する。

## 完了条件

- TURN-TLS 接続でクライアント証明書を提示し、相互 TLS 認証を要求する TURN サーバーへ接続できること。検証には実 TURN サーバーを使用すること。
- TURN-TCP / TURN-TLS の双方で relay candidate が生成され、接続できることを確認すること。
- 内部実装の追加のみで済む場合は `[ADD]`、API シグネチャ変更を伴う場合は `[CHANGE]` として `CHANGES.md` に追記すること。

## 解決方法
