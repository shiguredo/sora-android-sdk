# CA 証明書の指定方法を X509Certificate 型から PEM 文字列型に変更する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/change-ca-certificate-pem-string

## 目的

クライアント側でサーバー証明書を検証するための自前 CA 証明書を、`X509Certificate?` 型ではなく PEM 文字列（`String?`）型で指定できるようにする。

企業内で自前の CA を利用しているケースに対応するため、WebSocket（シグナリング）と TURN-TLS（libwebrtc）の両方で自前 CA 証明書を指定する機能は既に実装済みである。しかし現状の API は `X509Certificate?` 型を受け取る形になっており、他の Sora SDK と API の一貫性を取るために PEM 文字列で受け取る形へ変更する。

本 issue は `caCertificate` のみを対象とし、`clientCertificate` の PEM 文字列化は別途対応する。

## 現状

CA 証明書は `X509Certificate?` 型で各クラスに渡されている。後方互換のない型変更となる。

- `SoraMediaChannel.kt`: コンストラクタ引数 `caCertificate: X509Certificate? = null`、KDoc、`PeerChannel` / `SignalingChannel` への引き渡し。
- `SignalingChannelImpl.kt`: コンストラクタ引数 `caCertificate: X509Certificate? = null`、`SignalingTlsMode.CUSTOM_CA` の判定、`TlsConfigFactory` への引き渡し。
- `PeerChannel.kt`: コンストラクタ引数 `caCertificate: X509Certificate? = null`、`RTCComponentFactory` への引き渡し。
- `RTCComponentFactory.kt`: コンストラクタ引数 `caCertificate: X509Certificate?`、`createSSLCertificateVerifier()` での `TurnTlsCertificateVerifier` への引き渡し。
- `TurnTlsCertificateVerifier.kt`: コンストラクタ引数 `caCertificate: X509Certificate? = null`、TrustManager の生成。
- `TlsConfigFactory.kt`: `createCustomCaTrustManager(caCertificate: X509Certificate)` 等。

## 設計方針

- `SoraMediaChannel` のコンストラクタが受け取る型を `X509Certificate?` から PEM 文字列（`String?`）へ変更する。
- コンストラクタ内で `CertificateFactory.getInstance("X.509")` により PEM 文字列を `X509Certificate` に変換する。変換は 1 か所（`SoraMediaChannel` のコンストラクタ）でのみ行い、変換後の `X509Certificate` を内部の各コンポーネント（`PeerChannel`、`SignalingChannelImpl` 等）に渡す。
- PEM 文字列の変換処理:
  - PEM ヘッダ（`-----BEGIN CERTIFICATE-----`）およびフッタ（`-----END CERTIFICATE-----`）を除去し、残りの Base64 文字列をデコードする。
  - Base64 デコードには `android.util.Base64`（API 1 互換）を使用する。
  - デコード後のバイト列を `ByteArrayInputStream` にラップし、`CertificateFactory.generateCertificate()` で `X509Certificate` を生成する。
- 変換失敗時の挙動:
  - PEM フォーマット不正、Base64 デコード失敗、証明書パース失敗のいずれの場合も `IllegalArgumentException` を送出する。
  - `caCertificate` が `null` の場合は変換をスキップし、従来どおりシステムの CA 証明書を使用する。
- WebSocket と TURN-TLS で同一の CA 証明書を利用する現状の方針は維持する。

## 完了条件

- `SoraMediaChannel` のコンストラクタで CA 証明書を PEM 文字列（`String?`）として指定できること。
- 指定した PEM 文字列の CA 証明書が WebSocket と TURN-TLS の両方のサーバー証明書検証に適用されること。
- 不正な PEM 文字列が指定された場合に `IllegalArgumentException` が送出されること。
- `SoraMediaChannel` のコンストラクタ KDoc（`@param caCertificate`）が PEM 文字列の説明に更新されていること。
- `CHANGES.md` の `develop` セクションに `[CHANGE]` エントリと担当者行を追記すること。

## 解決方法
