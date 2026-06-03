# TURN-TLS でクライアント証明書を指定できるようにする

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch: feature/add-turn-tls-client-certificate

## 目的

TURN-TLS の接続でクライアント証明書（およびクライアント秘密鍵）を指定できるようにし、相互 TLS 認証を行う TURN サーバーへ接続できるようにする。

## 優先度根拠

- WebSocket（シグナリング）側ではクライアント証明書による相互 TLS 認証に既に対応しているが、TURN-TLS 側は未対応であり、機能が片側にしか揃っていない。
- 相互 TLS 認証を要求する環境での利用に必要だが、CA 証明書指定機能のように緊急性は高くないため Medium とする。

## 現状

クライアント証明書は WebSocket（シグナリング）側にのみ適用されており、TURN-TLS 側には適用されていない。

- `TlsConfigFactory.kt`: `clientCertificate` / `clientPrivateKey` を受け取り `createClientAuthenticationKeyManagers()` で `KeyManager` を生成する仕組みがあり、これは OkHttp（WebSocket）向けの `TlsSocketConfig` 生成で利用されている。
- `SignalingChannel.kt`: コンストラクタ引数に `clientCertificate: X509Certificate? = null` を持ち、WebSocket の TLS 設定に反映している。
- `SoraMediaChannel.kt`: コンストラクタ引数 `clientCertificate: X509Certificate? = null` を持つ。
- `TurnTlsCertificateVerifier.kt`: libwebrtc の `SSLCertificateVerifier` として TURN-TLS の「サーバー証明書チェーンの検証」のみを行っており、クライアント証明書を提示する仕組みは持っていない。
- `RTCComponentFactory.kt`: `createSSLCertificateVerifier()` で `TurnTlsCertificateVerifier` を生成する際、`caCertificate` は渡しているが `clientCertificate` は渡していない。

つまり `clientCertificate` はシグナリング（OkHttp）にのみ流れており、libwebrtc 側の TURN-TLS 接続には反映されていない。

## 設計方針

- 基本方針は CA 証明書指定機能の実装に合わせ、`SoraMediaChannel` で受け取ったクライアント証明書を TURN-TLS 接続まで伝搬させる。
- libwebrtc の TURN-TLS においてクライアント証明書を提示するための API（`PeerConnection.IceServer` の TLS 設定、または `SSLCertificateVerifier` 周辺）を調査し、クライアント証明書を提示できる経路を特定する。
- WebSocket 側と同じ証明書・秘密鍵を TURN-TLS にも適用する構成を基本とする。

## 完了条件

- TURN-TLS 接続でクライアント証明書を提示し、相互 TLS 認証を要求する TURN サーバーへ接続できること。
- TURN-TCP / TURN-TLS の双方で relay candidate が生成され、接続できることを確認すること。
- 後方互換のある追加であれば `CHANGES.md` の `develop` セクションに `[ADD]`、API 変更を伴う場合は `[CHANGE]` エントリを追記すること。

## 解決方法
