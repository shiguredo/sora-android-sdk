# CA 証明書の指定方法を X509Certificate 型から PEM 文字列型に変更する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Model: Opus 4.8
- Branch: feature/change-ca-certificate-pem-string

## 目的

クライアント側でサーバー証明書を検証するための自前 CA 証明書を、`X509Certificate?` 型ではなく PEM 文字列（`String?`）型で指定できるようにする。

企業内で自前の CA を利用しているケースに対応するため、WebSocket（シグナリング）と TURN-TLS（libwebrtc）の両方で自前 CA 証明書を指定する機能は既に実装済みである。しかし現状の API は `X509Certificate?` 型を受け取る形になっており、他の Sora SDK と API の一貫性を取るために PEM 文字列で受け取る形へ変更する。

## 優先度根拠

- CA 証明書を指定する機能自体は既に動作しており、緊急のバグではない。
- ただし他の Sora SDK では PEM 文字列で CA 証明書を指定する設計であり、SDK 間で API が揃っていないと利用者が混乱する。一貫性確保のため早めに対応したいが緊急ではないため Medium とする。

## 現状

CA 証明書は `X509Certificate?` 型で各クラスに渡されている。後方互換のない型変更となる。

- `SoraMediaChannel.kt`: コンストラクタ引数 `caCertificate: X509Certificate? = null`、KDoc、`PeerChannel` / `SignalingChannel` への引き渡し。
- `SignalingChannel.kt`: コンストラクタ引数 `caCertificate: X509Certificate? = null`、`SignalingTlsMode.CUSTOM_CA` の判定、`TlsConfigFactory` への引き渡し。
- `PeerChannel.kt`: コンストラクタ引数 `caCertificate: X509Certificate? = null`、`RTCComponentFactory` への引き渡し。
- `RTCComponentFactory.kt`: コンストラクタ引数 `caCertificate: X509Certificate?`、`createSSLCertificateVerifier()` での `TurnTlsCertificateVerifier` への引き渡し。
- `TurnTlsCertificateVerifier.kt`: コンストラクタ引数 `caCertificate: X509Certificate? = null`、TrustManager の生成。
- `TlsConfigFactory.kt`: `createCustomCaTrustManager(caCertificate: X509Certificate)` 等、`X509Certificate` を直接受け取るメソッド群。

## 設計方針

- 公開 API の入口（`SoraMediaChannel` のコンストラクタ）が受け取る型を `X509Certificate?` から PEM 文字列（`String?`）へ変更する。
- SDK 内部で PEM 文字列を `CertificateFactory` で `X509Certificate` に変換する変換層を 1 か所に設け、そこから先の内部実装（`TlsConfigFactory` / `TurnTlsCertificateVerifier` 等）は従来どおり `X509Certificate` を扱う構成を検討する。
- 不正な PEM 文字列が指定された場合の変換失敗時の挙動を明確にする。なお、壊れた証明書はアプリケーション側で `X509Certificate` を生成する時点で例外となる前提だったが、PEM 文字列を受け取る場合は SDK 内部で変換するため、変換失敗時の例外伝搬方針を決める必要がある。
- WebSocket と TURN-TLS で同一の CA 証明書を利用する現状の方針は維持する。

## 完了条件

- `SoraMediaChannel` のコンストラクタで CA 証明書を PEM 文字列（`String?`）として指定できること。
- 指定した PEM 文字列の CA 証明書が WebSocket と TURN-TLS の両方のサーバー証明書検証に適用されること。
- 不正な PEM 文字列が指定された場合の挙動が定義され、ログ・エラーで判別できること。
- 後方互換のない変更のため、`CHANGES.md` の `develop` セクションに `[CHANGE]` エントリを追記すること。

## 解決方法
