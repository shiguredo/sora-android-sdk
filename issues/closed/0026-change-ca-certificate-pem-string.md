# CA 証明書・クライアント証明書・秘密鍵の指定方法を PEM 文字列型に統一する

- Priority: Medium
- Created: 2026-06-03
- Completed: 2026-07-10
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/change-ca-certificate-pem-string

## 目的

TLS 接続で利用する以下の 3 つを、Java の型オブジェクト（`X509Certificate` / `PrivateKey`）ではなく、すべて PEM 文字列（`String?`）型で指定できるように統一する。

- `caCertificate`: サーバー証明書検証に利用する自前 CA 証明書
- `clientCertificate`: mTLS で利用するクライアント証明書（チェーン）
- `clientPrivateKey`: クライアント証明書に対応する秘密鍵

企業内で自前の CA を利用するケースや mTLS のケースに対応するため、WebSocket（シグナリング）と TURN-TLS（libwebrtc）の両方でこれらを指定する機能は既に実装済みである。しかし現状の API は `caCertificate: X509Certificate?`、`clientCertificate: List<X509Certificate>?`、`clientPrivateKey: PrivateKey?` と型オブジェクトを受け取る形になっている。他の Sora SDK と API の一貫性を取るため、3 つとも PEM 文字列で受け取る形へ変更する。

3 つをまとめて PEM 文字列型へ揃えることで、公開 API の型の一貫性を保つ。当初は `caCertificate` のみを対象とする想定だったが、`clientCertificate` / `clientPrivateKey` のみ型オブジェクトのまま残ると API が混在し一貫性が下がるため、本 issue で 3 つ同時に対応する。

## 現状

CA 証明書・クライアント証明書・秘密鍵は、それぞれ型オブジェクトで各クラスに渡されている。

- `SoraMediaChannel.kt`: コンストラクタ引数 `caCertificate: X509Certificate? = null` / `clientCertificate: List<X509Certificate>? = null` / `clientPrivateKey: PrivateKey? = null`、KDoc、`clientCertificate` の非空チェックと `clientCertificate` / `clientPrivateKey` の対指定チェック（`SoraMediaChannel.kt:231-235`）、`PeerChannel` / `SignalingChannel` / `PeerNetworkConfig` への引き渡し。
- `SignalingChannel.kt`（`SignalingChannelImpl` クラスのコンストラクタは `SignalingChannel.kt:107`）: コンストラクタ引数 `caCertificate` / `clientCertificate` / `clientPrivateKey`、`SignalingTlsMode.CUSTOM_CA` の判定、`hasClientAuthentication()` の判定、`TlsConfigFactory` への引き渡し。
- `PeerChannel.kt`: コンストラクタ引数 `caCertificate`、`RTCComponentFactory` / `PeerNetworkConfig` への引き渡し。
- `RTCComponentFactory.kt`: コンストラクタ引数 `caCertificate`、`createSSLCertificateVerifier()` での `TurnTlsCertificateVerifier` への引き渡し。
- `TurnTlsCertificateVerifier.kt`: コンストラクタ引数 `caCertificate`、TrustManager の生成。
- `PeerNetworkConfig.kt`: コンストラクタ引数 `clientCertificate` / `clientPrivateKey`、対指定チェック、TURN-TLS では `TurnTlsClientCertificatePem` で PEM 文字列へ変換して libwebrtc に渡している。
- `TlsConfigFactory.kt`: `createCustomCaTrustManager(caCertificate: X509Certificate)`、`createClientAuthenticationKeyManagers(clientCertificate, clientPrivateKey)` 等。
- `TurnTlsClientCertificatePem.kt`: `X509Certificate` / `PrivateKey` を PEM 文字列に変換する既存ユーティリティ。

これらの `caCertificate` / `clientCertificate` / `clientPrivateKey` はいずれも直前のリリースに含まれておらず、現在の `develop` サイクルで追加された未リリースの機能である。したがって後方互換は考慮しない。

## 設計方針

### 変換箇所

- 型変更するのは `SoraMediaChannel` のコンストラクタの公開 API のみとする。
- コンストラクタ内（1 か所）で PEM 文字列を型オブジェクトへ変換し、変換後の `X509Certificate?` / `List<X509Certificate>?` / `PrivateKey?` を内部の各コンポーネント（`PeerChannel.kt`、`SignalingChannel.kt`、`PeerNetworkConfig.kt` 等）へ渡す。
- 内部コンポーネント（`SignalingChannel.kt`、`PeerChannel.kt`、`RTCComponentFactory.kt`、`TlsConfigFactory.kt`、`PeerNetworkConfig.kt`、`TurnTlsCertificateVerifier.kt`、`TurnTlsClientCertificatePem.kt`）のコンストラクタ引数の型は現状（型オブジェクト）のまま変更しない。PEM 解析と型変換のロジックは `SoraMediaChannel` の 1 か所に集約し、実装コード本体の変更は `SoraMediaChannel.kt` 中心に留める。ただし sdk-doc の更新、単体テストの追加、サンプルコード（samples）の追随修正は別途必要である。
- TURN-TLS 経路では従来どおり `TurnTlsClientCertificatePem` で型オブジェクトから PEM 文字列へ再変換して libwebrtc に渡す。PEM → 型オブジェクト → PEM の往復が発生するが、変更範囲を最小化するためこの経路は維持する。

### 証明書の変換（`caCertificate` / `clientCertificate`）

- PEM 文字列を UTF-8 バイト列へ変換し、`ByteArrayInputStream` にラップして `CertificateFactory.getInstance("X.509")` の以下メソッドに直接渡す。
  - `caCertificate`: `generateCertificate()` で単一の `X509Certificate` を生成する。
  - `clientCertificate`: `generateCertificates()` で `Collection<Certificate>` を取得し、`X509Certificate` のリストへ変換する。単一証明書・証明書チェーン（複数の `CERTIFICATE` ブロックを連結した PEM）の両方に対応する。
- X.509 の `CertificateFactory` は `-----BEGIN CERTIFICATE-----` / `-----END CERTIFICATE-----` を含む PEM をそのままパースできるため、ヘッダ・フッタの手動除去や `android.util.Base64` による Base64 デコードは行わない。不要な独自パーサを持たず、実装をシンプルに保つ。
- 既存の `TurnTlsCertificateVerifier` も同じ `CertificateFactory.generateCertificate()` で証明書を生成しているため、パーサ経路を統一する。

### 秘密鍵の変換（`clientPrivateKey`）

- 対応フォーマットは PKCS#8 PEM（`-----BEGIN PRIVATE KEY-----` / `-----END PRIVATE KEY-----`）のみとする。
  - WebSocket 経路（OkHttp）は JCA の `KeyFactory` + `PKCS8EncodedKeySpec` で秘密鍵を解析する必要があり、標準の JCA は PKCS#8 のみ解釈できる。BouncyCastle を追加すれば PKCS#1 / SEC1 にも対応できるが、依存とアプリサイズの増加を避けるため PKCS#8 のみに限定する。
  - `-----BEGIN RSA PRIVATE KEY-----`（PKCS#1）や `-----BEGIN EC PRIVATE KEY-----`（SEC1）など PKCS#8 以外のヘッダは非対応とし、`IllegalArgumentException` を送出する。
- 変換手順:
  - PEM ヘッダ・フッタと改行・空白を除去し、残りの Base64 文字列を `android.util.Base64` でデコードして PKCS#8 DER バイト列を得る。証明書と異なり、JCA には PEM 秘密鍵を直接読む API が無いため、ここでは Base64 デコードが必要になる。
  - デコードしたバイト列から `PKCS8EncodedKeySpec` を生成し、`KeyFactory.generatePrivate()` で `PrivateKey` を生成する。
  - 鍵アルゴリズムは `KeyFactory.getInstance("RSA")` を試し、失敗した場合に `KeyFactory.getInstance("EC")` を試す。いずれも失敗した場合は `IllegalArgumentException` を送出する。

### 変換失敗時・null 時の挙動

- PEM フォーマット不正、Base64 デコード失敗、証明書・秘密鍵のパース失敗（`CertificateException` / `InvalidKeySpecException` 等）はいずれも `IllegalArgumentException` を送出する。
- 各引数が `null` の場合は変換をスキップする。`caCertificate = null` の場合は従来どおりシステムの CA 証明書を使用し、`clientCertificate` / `clientPrivateKey` が `null` の場合はクライアント証明書認証を行わない。

### バリデーション

- `clientCertificate` と `clientPrivateKey` は対で指定する（両方 `null` か両方非 `null`）ルールを維持する。片方のみ指定された場合は `IllegalArgumentException` を送出する。
- `clientCertificate` の PEM から証明書が 1 つも得られなかった場合も `IllegalArgumentException` を送出する（従来の「空リスト禁止」に相当）。

### その他

- WebSocket と TURN-TLS で同一の CA 証明書・クライアント証明書・秘密鍵を利用する現状の方針は維持する。

## 完了条件

- `SoraMediaChannel` のコンストラクタで `caCertificate` / `clientCertificate` / `clientPrivateKey` を PEM 文字列（`String?`）として指定できること。
- `clientCertificate` が単一証明書・証明書チェーンの両方の PEM を受け付けること。
- `clientPrivateKey` が PKCS#8 PEM を受け付け、PKCS#8 以外のフォーマットでは `IllegalArgumentException` を送出すること。
- 指定した PEM が WebSocket と TURN-TLS の両方の TLS 接続に適用されること。
- 不正な PEM 文字列が指定された場合に `IllegalArgumentException` が送出されること。
- `clientCertificate` / `clientPrivateKey` の対指定チェックが維持されていること。
- `SoraMediaChannel` のコンストラクタ KDoc（`@param caCertificate` / `@param clientCertificate` / `@param clientPrivateKey`）が PEM 文字列の説明に更新されていること。
- PEM 変換処理（証明書・証明書チェーン・秘密鍵の正常系、および不正 PEM での `IllegalArgumentException`、対指定チェック）のユニットテストが追加されていること。テストはモック・スタブを使わず、テスト用に生成した実際の PEM を用いること。
- `CHANGES.md` の `develop` セクションについて、`caCertificate` / `clientCertificate` / `clientPrivateKey` を追加した既存の `[ADD]` エントリ（それぞれ CA 証明書・クライアント証明書のエントリ）を、PEM 文字列で指定する旨に更新すること。これらは同じ未リリースの `develop` サイクルで追加された機能であり、変更履歴は派生元ブランチとの最終的な差分のみを記載するため、新規 `[CHANGE]` エントリは追記しない。

## 解決方法

- PEM 文字列を型オブジェクトへ変換するユーティリティ `PemDecoder`（`channel/tls/PemDecoder.kt`）を新規追加した。
  - `decodeCertificate()`: `CertificateFactory.generateCertificate()` に PEM を直接渡して単一の `X509Certificate` を生成する。
  - `decodeCertificateChain()`: `CertificateFactory.generateCertificates()` で単一証明書・証明書チェーンの両方に対応し、証明書が 0 件の場合は `IllegalArgumentException` を送出する。
  - `decodePkcs8PrivateKey()`: PKCS#8 PEM のみ対応。ヘッダ・フッタを除去して `android.util.Base64` でデコードし、`RSA` → `EC` の順に `KeyFactory` で `PrivateKey` を生成する。PKCS#8 以外のヘッダや解析失敗は `IllegalArgumentException` を送出する。
- `SoraMediaChannel` のコンストラクタ公開引数 `caCertificate` / `clientCertificate` / `clientPrivateKey` の型を `String?`（PEM 文字列）へ変更した。
  - `init` ブロックで対指定チェックを行った後、`PemDecoder` で型オブジェクト（`X509Certificate?` / `List<X509Certificate>?` / `PrivateKey?`）へ変換し、内部プロパティ `caCertificateX509` / `clientCertificateChain` / `clientPrivateKeyObject` に保持する。
  - 内部コンポーネント（`SignalingChannelImpl` / `PeerChannelImpl` / `PeerNetworkConfig`）へは変換後の型オブジェクトを渡すため、内部コンポーネントのシグネチャは変更していない。
  - 従来の「`clientCertificate` の空リスト禁止」チェックは `decodeCertificateChain()` の 0 件チェックに置き換えた。
  - KDoc（`@param caCertificate` / `@param clientCertificate` / `@param clientPrivateKey`）を PEM 文字列の説明へ更新した。
- テストを追加した（モック・スタブ不使用、openssl で生成した実 PEM を利用）。
  - `TestPemFixtures`（`src/test/.../TestPemFixtures.kt`）に証明書・証明書チェーン・PKCS#8 RSA/EC 秘密鍵・PKCS#1 秘密鍵の実 PEM を埋め込んだ。
  - `PemDecoderTest`（10 ケース）: 証明書・チェーン・PKCS#8 秘密鍵の正常系、不正 PEM・PKCS#1・壊れた Base64・鍵として不正なバイト列での `IllegalArgumentException` を検証。
  - `SoraMediaChannelCertificateTest`（6 ケース）: 有効 PEM でのインスタンス生成、対指定チェック、不正 PEM・PKCS#1 での `IllegalArgumentException` を検証。
- `CHANGES.md` の `develop` セクションの既存 `[ADD]` エントリ（CA 証明書・クライアント証明書）を PEM 文字列型で指定する旨へ更新した（未リリースのため新規 `[CHANGE]` は追記していない）。
- `./gradlew :sora-android-sdk:ktlintCheck` と `:sora-android-sdk:testDebugUnitTest` が成功することを確認した。
