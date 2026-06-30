# TURN-TLS でクライアント証明書を指定できるようにする

- Priority: Medium
- Created: 2026-06-03
- Completed: 2026-06-30
- Polished: 2026-06-03
- Status: closed
- Model: Opus 4.8
- Branch: feature/add-turn-tls-client-certificate

## 解決方法

### libwebrtc パッチ

Shiguredo 管理の libwebrtc ビルドに `android_turn_tls_client_certificate.patch` を追加した。
`PeerConnection.IceServer.Builder` に `setTlsClientCertificate(String privateKeyPem, String certificatePem)` を追加し、
`certificatePem` には単体の証明書でも証明書チェーン（concatenated PEM）でも指定できる。
内部では常に `SSLIdentity::CreateFromPEMChainStrings()` を使用する。

- PR: https://github.com/shiguredo-webrtc-build/webrtc-build/pull/149
- コミット `7bc6611`: 内部でチェーンかどうかを判定しないようにする
- コミット `024e489`: `setTlsClientCertificateChain` は不要なので削除する

### SDK 実装概要

SDK 側では Java リフレクションで `setTlsClientCertificate` を呼び出し、
`SoraMediaChannel` → `PeerNetworkConfig` の経路で
`clientCertificate` / `clientCertificateChain` / `clientPrivateKey` を伝搬する。

### 変更ファイル

| ファイル | 変更内容 |
|----------|----------|
| `channel/rtc/TurnTlsClientCertificatePem.kt` | 新規。`X509Certificate` / `PrivateKey` を PEM 文字列に変換するユーティリティ (`TurnTlsClientCertificatePem`) と、リフレクションで `setTlsClientCertificate` を呼び出す設定器 (`TurnTlsClientCertificateConfigurer`) を追加 |
| `channel/rtc/PeerNetworkConfig.kt` | `gatherIceServerSetting()` で `turns:` URL に対してクライアント証明書を適用するロジックを追加。`clientCertificate` / `clientCertificateChain` / `clientPrivateKey` パラメータを追加 |
| `channel/rtc/RTCComponentFactory.kt` | `createSSLCertificateVerifier()` で `caCertificate` を `TurnTlsCertificateVerifier` に渡す（クライアント証明書は IceServer 側で設定するため不要） |
| `channel/SoraMediaChannel.kt` | `clientCertificate` / `clientCertificateChain` / `clientPrivateKey` コンストラクタ引数を追加。`handleInitialOffer()` と `requestClientOfferSdp()` で `PeerNetworkConfig` に伝搬。`clientCertificate` と `clientCertificateChain` の排他チェックを追加 |
| `channel/signaling/SignalingChannel.kt` | `clientCertificate` / `clientCertificateChain` / `clientPrivateKey` を WebSocket mTLS 用に `TlsConfigFactory` へ伝搬 |
| `channel/tls/TlsConfigFactory.kt` | クライアント証明書チェーン対応 (`clientAuthenticationKeyManagers`, `createCustomCaWithClientAuthenticationTlsSocketConfig` など) |

### 注意点

- libwebrtc 標準の公開 API には `setTlsClientCertificate` は存在しない。Shiguredo パッチ適用済みの libwebrtc ビルドが必要。
- `setTlsClientCertificateChain` はパッチに存在しないため、SDK 側でも単一メソッドで証明書・チェーン両方に対応している。
- `clientCertificate` と `clientCertificateChain` は排他（同時指定不可）。
- クライアント証明書と秘密鍵は対で指定必須。

## 目的

TURN-TLS の接続でクライアント証明書（およびクライアント秘密鍵）を指定できるようにし、相互 TLS 認証を行う TURN サーバーへ接続できるようにする。

## 完了条件

- [x] TURN-TLS 接続でクライアント証明書を提示し、相互 TLS 認証を要求する TURN サーバーへ接続できること。検証には実 TURN サーバーを使用すること。
- [x] TURN-TCP / TURN-TLS の双方で relay candidate が生成され、接続できることを確認すること。
- [x] `[ADD]` / `[FIX]` として `CHANGES.md` に追記すること。
