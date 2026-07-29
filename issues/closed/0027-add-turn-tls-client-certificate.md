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
`clientCertificateChain` / `clientPrivateKey` を伝搬する。
単一証明書と証明書チェーンは単一の引数 `clientCertificateChain: List<X509Certificate>?` に統一し、
単一証明書の場合は要素数 1 のリストとして指定する。

### 変更ファイル

| ファイル | 変更内容 |
|----------|----------|
| `channel/rtc/TurnTlsClientCertificatePem.kt` | 新規。`X509Certificate` / `PrivateKey` を PEM 文字列に変換するユーティリティ (`TurnTlsClientCertificatePem`) と、リフレクションで `setTlsClientCertificate` を呼び出す設定器 (`TurnTlsClientCertificateConfigurer`) を追加 |
| `channel/rtc/PeerNetworkConfig.kt` | `gatherIceServerSetting()` で `turns:` URL に対してクライアント証明書を適用するロジックを追加。`clientCertificateChain` / `clientPrivateKey` パラメータを追加 |
| `channel/rtc/RTCComponentFactory.kt` | `createSSLCertificateVerifier()` で `caCertificate` を `TurnTlsCertificateVerifier` に渡す（クライアント証明書は IceServer 側で設定するため不要） |
| `channel/SoraMediaChannel.kt` | `clientCertificateChain` / `clientPrivateKey` コンストラクタ引数を追加。`handleInitialOffer()` と `requestClientOfferSdp()` で `PeerNetworkConfig` に伝搬。単一証明書は要素数 1 のリストとして指定する |
| `channel/signaling/SignalingChannel.kt` | `clientCertificateChain` / `clientPrivateKey` を WebSocket mTLS 用に `TlsConfigFactory` へ伝搬 |
| `channel/tls/TlsConfigFactory.kt` | クライアント証明書チェーン対応 (`clientAuthenticationKeyManagers`, `createCustomCaWithClientAuthenticationTlsSocketConfig` など) |

### 注意点

- libwebrtc 標準の公開 API には `setTlsClientCertificate` は存在しない。Shiguredo パッチ適用済みの libwebrtc ビルドが必要。
- `setTlsClientCertificateChain` はパッチに存在しないため、SDK 側でも単一メソッドで証明書・チェーン両方に対応している。
- 単一証明書と証明書チェーンは `clientCertificateChain: List<X509Certificate>?` に統一。単一証明書の場合は要素数 1 のリストとして指定する。
- クライアント証明書と秘密鍵は対で指定必須。

## 検証結果

### サーバー構成

- Sora の TURN 機能を利用した
- TURN-TCP を有効化した
- TURN-TLS を有効化した
- NGINX で TURN-TLS を TLS 終端し、内部の TURN-TCP へ転送した

TURN-TLS 用の NGINX 設定では `ssl_client_certificate`、`ssl_verify_client on;`、`ssl_verify_depth` を設定し、クライアント証明書検証を有効化した。

### TURN-TLS サーバーの動作確認

`openssl s_client` で以下を確認した。

- サーバー証明書の SAN / CN が接続先ホスト名と一致している
- `CertificateRequest` が返され、サーバーがクライアント証明書を要求している
- `Acceptable client certificate CA names` が提示される

これにより、TURN-TLS サーバー側でクライアント証明書要求が有効であることを確認した。

### SDK の接続確認

#### 正常系: 単一証明書

- `clientCertificateChain` に要素数 1 のリストを指定し、`clientPrivateKey` とともに接続
- WSS 接続成功
- TURN-TLS 接続成功
- relay candidate の生成を確認

#### 正常系: 証明書チェーン

- `clientCertificateChain` に複数要素のリストを指定し、`clientPrivateKey` とともに接続
- WSS 接続成功
- TURN-TLS 接続成功
- relay candidate の生成を確認

証明書チェーン指定時も SDK 側は `setTlsClientCertificate(privateKeyPem, certificatePem)` に concatenated PEM を渡す実装で動作することを確認した。

#### 異常系: クライアント証明書未指定

- `clientCertificateChain` / `clientPrivateKey` を未指定で接続
- TURN-TLS 接続失敗
- relay candidate は生成されない

#### 異常系: 不正なクライアント証明書

- 正しい CA で信頼されないクライアント証明書、または不正な組み合わせで接続
- TURN-TLS 接続失敗
- relay candidate は生成されない

### 結論

- TURN-TLS でクライアント証明書を提示して接続できることを確認した
- `clientCertificateChain` に要素数 1 のリスト（単一証明書）を指定した場合に動作することを確認した
- `clientCertificateChain` に複数要素のリスト（証明書チェーン）を指定した場合に動作することを確認した
- TURN-TLS サーバー側でクライアント証明書要求が有効であることを確認した
- クライアント証明書未指定または不正時には TURN-TLS 接続に失敗することを確認した
- TURN-TCP / TURN-TLS の双方で relay candidate が生成されることを確認した

## 完了条件

- [x] TURN-TLS 接続でクライアント証明書を提示し、相互 TLS 認証を要求する TURN サーバーへ接続できること。検証には実 TURN サーバーを使用すること。
- [x] TURN-TCP / TURN-TLS の双方で relay candidate が生成され、接続できることを確認すること。
- [x] `[ADD]` / `[FIX]` として `CHANGES.md` に追記すること。
