# Android 17（API レベル 37）に対応する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/add-android-17-support

## 目的

Android 17（API レベル 37）のリリースに伴う変更点のうち、Sora Android SDK に影響するものへ追従する。特に Background Audio Hardening への対応を検討し、targetSdk 37 で既定有効になる Certificate Transparency（CT）と、targetSdk 37 で必須化されるローカルネットワーク権限（ACCESS_LOCAL_NETWORK）については、アプリ側の対応としてドキュメント・サンプルで補足する。

## 優先度根拠

- `targetSdk` を 37 へ引き上げる際に、Background Audio Hardening まわりで動作が変わる可能性がある。ACCESS_LOCAL_NETWORK 権限の必須化はアプリ側の対応であり、SDK の変更はドキュメント・サンプル整備のみ。
- ただし現状は `targetSdk = 36` であり、API 37 依存の変更は即時には発動しない。`targetSdk` 引き上げのタイミングで対応すればよい。
- Certificate Transparency（CT）は `targetSdk 37` で既定有効になり、WSS シグナリングの証明書検証に影響する。特に `caCertificate` で独自 CA を使う利用者は Network Security Configuration（NSC）の設定が必要になる可能性があり、`targetSdk` 引き上げ前に検証とドキュメント整備を完了させる必要がある。以上から Medium とする。

## 現状

- 現状の SDK バージョン指定（`gradle/libs.versions.toml`）:
  - `compileSdk = "36"`
  - `minSdk = "21"`
  - `targetSdk = "36"`
- 影響が想定される変更点:
  1. Background Audio Hardening（影響: 中・本丸はアプリ側）
     - Android 17 では、バックグラウンド時の音声操作（再生・audio focus リクエスト・音量変更 API）に「表示中の Activity または SHORT_SERVICE 以外の FGS」のいずれかが必要になる。
     - `targetSdk 37` のアプリはさらに while-in-use (WIU) 能力を持つ FGS が必要になる。
     - 違反時の失敗は例外・エラーメッセージなしの静かな失敗であり、audio focus は `AUDIOFOCUS_REQUEST_FAILED` を返す。
     - 録音（AudioRecord）は直接の影響 API ではないが、成功させるには再生と同様の要件を満たす必要がある。
     - この変更の本丸はアプリのライフサイクルと FGS/WIU 要件であり、SDK 側で解決できる範囲は限定的。SDK が直接触れるのは `JavaAudioDeviceModule` の生成と、WebRTC の録音・再生エラー通知までである。
     - SDK の検知範囲: `AudioRecordErrorCallback` / `AudioTrackErrorCallback` により、開始失敗を `SoraErrorReason.AUDIO_RECORD_START_ERROR` / `AUDIO_TRACK_START_ERROR` 等として通知できる（`RTCComponentFactory.kt` の `createJavaAudioDevice()`）。
     - アプリの責務: 表示中 Activity の維持・FGS/WIU の起動・audio focus の扱い。SDK はこれらを代替できない。
     - 関連箇所: `SoraAudioOption.kt`、`RTCComponentFactory.kt` の `createJavaAudioDevice()`。
  2. ローカルネットワーク権限の必須化（影響: アプリ側・SDK 実装は対象外）
     - `targetSdk 37` では、LAN 上のデバイスとの通信に `ACCESS_LOCAL_NETWORK` ランタイム権限（NEARBY_DEVICES グループ配下）が必要になる。未許可の場合は通信がブロックされる。
     - 同一 LAN 上のシグナリングサーバ・接続先を使うアプリが影響を受ける。
     - SDK はこの権限に対応できない。理由は以下のとおり:
       - SDK 本体の `AndroidManifest.xml` は空に近く、SDK は権限を宣言しない。
       - SDK は `Activity` を持たず、`Activity.requestPermissions()` による権限要求ができない。
       - シグナリング接続は受け取った URL をそのまま OkHttp に渡しているだけであり（`SignalingChannel.kt`）、SDK が権限状態を制御する箇所がない。
     - したがってこの issue で扱う成果物は「権限の要求手順・影響のドキュメント整備」「権限未許可時の失敗挙動のエラー整理」「サンプルの更新」であり、SDK 内の実装対応は行わない。
  3. Certificate Transparency の既定有効化（影響: WSS シグナリング・要検証 / ドキュメント整備）
     - Android 16（API レベル 36）で CT が導入され、既定は無効だった。Android 17（API レベル 37）では、アプリの `targetSdkVersion` が 37 以上の場合に CT が既定で有効になる。端末が Android 17 でもアプリが `targetSdk 36` 以下なら従来どおり無効であり、Android 16 端末では `targetSdk 37` でも明示的にオプトインしない限り無効である。
     - CT はプラットフォームの TLS スタックを使う接続に適用される。SDK では OkHttp を使う WSS シグナリングが該当する（`SignalingChannel.kt` の `SignalingChannelImpl` が `OkHttpClient` を構築する箇所）。
     - システム CA を使う既定の接続では、公開 CA の証明書が SCT（Signed Certificate Timestamp）の要件を満たす必要がある。満たさない場合、TLS ハンドシェイクは `SSLHandshakeException` で失敗し、SDK では `SoraErrorReason.SIGNALING_FAILURE` として `onError` に通知される（`SignalingChannelImpl` の `onFailure` が `buildOnFailureMessage` で例外情報を message に含める）。
     - `caCertificate` 指定時は `TlsConfigFactory.createCustomCaTrustManager` で独自 KeyStore の `X509TrustManager` を生成する。公式ドキュメントが CT を無効化するとしているのは NSC の `<trust-anchors>` に `"user"` またはインライン CA を指定した場合であり、コード内で生成した TrustManager はこの条件に該当しない。CT の要否は NSC の `<certificateTransparency>` 設定（未指定時は `targetSdk 37` で有効）で判定されるため、独自 CA でも CT 検証が走る可能性が高い。実機での確認が必要である。
     - `insecure = true` はサーバー証明書の検証自体をスキップするため CT の対象外だが、非推奨の設定である。
     - TURN-TLS は `TurnTlsCertificateVerifier` が `PKIXParameters` と `CertPathValidator` で独自に検証しており、プラットフォームの TrustManager を使わないため CT の対象外である。メディアの DTLS / SRTP も対象外である。
     - `signalingEndpointCandidates` で指定した候補ホストや、シグナリングのリダイレクト先も CT の対象になる。NSC のドメイン設定は接続し得るすべてのホストをカバーする必要がある。
     - CT ログリストを取得できない場合や 70 日以上古い場合は fail-open するため、検証時はログリストが利用可能な状態にする必要がある。
     - 公式ドキュメント: <https://developer.android.com/privacy-and-security/security-config#certificateTransparency> / <https://developer.android.com/about/versions/17/behavior-changes-17> / <https://developer.android.com/privacy-and-security/certificate-transparency-policy>
  4. 大画面での向き・リサイズ制約の無効化（影響: アプリ側・サンプル / ドキュメント）
     - Android 16（API レベル 36）以降の大画面（sw>=600dp）では、向き・リサイズ・アスペクト比の制約が無視される。`targetSdk 36` ではオプトアウトできたが、`targetSdk 37` ではオプトアウトできなくなる。
     - SDK は Activity / View を持たないため、SDK 内の実装対応はない。映像表示のサイズ・カメラプレビューの回転・全画面レイアウトはアプリ側の configuration change 対応に依存する。
     - サンプル / クイックスタートで固定向き・全画面を前提にしている箇所を確認し、タブレット / フォルダブルでの表示を確認する必要がある。
     - 公式ドキュメント: <https://developer.android.com/about/versions/17/behavior-changes-17>
- 参考（本 issue のスコープ外・将来対応の検討材料）:
  - Cleartext 通信の扱いの変更（未発動・将来対応）
    - Android 17 では `usesCleartextTraffic` の非推奨化は「将来のリリースで予定」という計画のみであり、**targetSdk 37 でも未発動**。`usesCleartextTraffic="true"` は引き続き有効。
    - 将来の非推奨化が発動するリリースで Network Security Configuration への移行を検討する。SDK はシグナリング URL をそのまま OkHttp の WebSocket に渡しているため（`SignalingChannel.kt`）、`ws://`（平文）運用のアプリが将来影響を受ける可能性がある。
    - 細部は libwebrtc / `shiguredo-webrtc-android` 側の追従が前提になる可能性がある。
  - ECH（Encrypted Client Hello）: `targetSdk 37` の TLS 接続で既定有効化。OkHttp 4.12 は ECH に対応していないため、ECH のネゴシエーションは行われず GREASE のみになる見込み。WSS 接続を API 37 の実機で確認する。DTLS のメディア面には適用されない。
  - Safer Native DCL—C: `targetSdk 37` では `System.load()` で読み込むネイティブファイルを読み取り専用にする必要がある。SDK のコードと E2E テストは `System.loadLibrary` のみを使用しており、APK 同梱の .so を読み込むため影響はない見込み。利用側アプリが writable な領域の .so を `System.load()` する場合の注意点をドキュメントに含めるか判断する。
  - アプリメモリ制限（App memory limits）: RAM 総量に応じたプロセスメモリ上限が導入され、超過時は強制終了する。WebRTC アプリはメモリ使用が多いため要確認だが、影響は未確認。
  - 影響なしと判断した項目:
    - MessageQueue の lock-free 化 / static final の変更禁止: SDK の production code に reflection でこれらを変更する箇所はない。
    - RemoteViews / ContentCapture / Activity Security: SDK はウィジェット・ContentCapture・Activity を使用しない。
    - CP2、OTP、パスワード表示、BluetoothSocket、IME アクセシビリティ: SDK の機能と無関係。
- 補足:
  - SDK 本体の `AndroidManifest.xml` は空に近く、Activity / Manifest 固有の変更（Activity Security など）は SDK 直撃ではなくアプリ側の課題になる見込み。

## 設計方針

- `compileSdk` / `targetSdk` を 37 へ引き上げ、ビルドと既存テストが通ることを確認する。
  - AGP は現行 9.2.1 で API 37 に対応済み（AGP 9.1 以降で対応）のため、AGP の更新は不要の見込み。
  - Robolectric の SDK 37 対応は 4.17-beta 系以降のみで安定版は未対応のため、4.17-beta 系の採用（または安定版リリース待ち）を判断してテスト実行 SDK を制御する。
  - E2E テスト基盤の Gradle Managed Device は現状 API 35（`pixelApi35`）のみのため、API 37 の Managed Device（`pixelApi37` など）を追加してシナリオ試験を行う。
- Background Audio Hardening の本丸（アプリのライフサイクル・FGS/WIU 要件）はアプリ側の責務であり、SDK 内の実装対応は行わない。SDK の責務は以下の範囲に限定する:
  - 録音・再生の開始失敗が既存のエラー通知（`SoraErrorReason.AUDIO_RECORD_START_ERROR` / `AUDIO_TRACK_START_ERROR` など）としてアプリへ届くかを検証する。なお、このコールバックは WebRTC / ADM が明示的に開始失敗を返した場合にしか発火せず、Android 17 の制約による silent failure が必ず通知される保証はない。
  - アプリ側で必要な FGS/WIU 要件と、権限制約下での失敗挙動をドキュメントで補足する。
- ACCESS_LOCAL_NETWORK 権限はアプリ側の責務であり、SDK 内の実装対応は行わない。以下の成果物を整備する:
  - 権限の要求手順と影響範囲のドキュメント整備。
  - 権限未許可時にシグナリング接続・通信が失敗する挙動のエラー整理。
  - サンプルの更新。
- Certificate Transparency はアプリの `targetSdk` と NSC で決まるため、SDK に CT の有効 / 無効を制御する実装は追加しない。以下の成果物を整備する:
  - API 37 の実機またはエミュレーターで、公開 CA の WSS（既定のシステム CA）、`caCertificate` を指定した独自 CA の WSS、クライアント証明書（mTLS）を指定した WSS、localhost（`wss://127.0.0.1` など）の接続を検証する。
  - `caCertificate` 利用時は、WSS の接続先ドメインに `<certificateTransparency enabled="false"/>` を設定する必要がある旨をドキュメント化する。`base-config` による全体無効は推奨せず、ドメイン単位の設定を案内する。
  - NSC のインライン CA に移行する方法は WSS には有効だが、TURN-TLS の検証は NSC のトラストアンカーを参照しないため、`caCertificate` は残す必要がある旨を明記する。
  - 既定のシステム CA を使う接続について、証明書が CT 準拠か確認する手順（埋め込み SCT の数とログ運営者）をドキュメント化する。
  - CT 違反時の `SSLHandshakeException` が `onError` の message に含まれ、利用者が原因を判別できることを確認する。
  - CT を実行時に切り替える API は提供されていないため、静的な NSC 設定を前提とする。
  - `insecure = true` は CT の対象外だが非推奨であることを明記する。
  - サンプル / クイックスタート（別リポジトリ）の `targetSdk 37` 化と、自己署名 CA を使うサンプルへの NSC 追加を行う。
- 大画面の向き・リサイズ制約はアプリ側の責務であり、SDK 内の実装対応は行わない。サンプル / クイックスタートで固定向き・全画面を前提にしている箇所を確認し、タブレット / フォルダブルでの表示を確認する。映像表示のアスペクト比やカメラプレビューの回転はアプリ側の configuration change 対応に依存する旨をドキュメントで補足する。
- Safer Native DCL—C は SDK のコードと E2E テストが `System.loadLibrary` のみを使用していることを確認する。利用側アプリが writable 領域の .so を `System.load()` する場合の注意点をドキュメントに含めるか判断する。
- Robolectric などテスト依存のバージョン追従が必要であれば併せて更新する。

## 完了条件

- `compileSdk` / `targetSdk` を 37 へ引き上げた状態でビルドと既存テストが通ること。
- API 37 の実機またはエミュレーター（Gradle Managed Device の `pixelApi37` など）で以下のシナリオ試験を行い、実挙動を検証すること:
  - Background Audio Hardening: バックグラウンドで音声通話を行ったときの録音・再生の挙動（静かな失敗・`AUDIOFOCUS_REQUEST_FAILED`）と、その失敗が SDK の `onError`（`SoraErrorReason.AUDIO_RECORD_START_ERROR` / `AUDIO_TRACK_START_ERROR` など）として通知されるかを検証する。
  - ACCESS_LOCAL_NETWORK: サンプルアプリまたは検証アプリで権限未許可時の LAN 上のシグナリングサーバ・接続先への通信ブロックを再現し、そのとき SDK 利用者にどう見えるか（SDK のコールバック・ログに現れる事象）を確認する。
- なお、現行のテスト基盤（Robolectric 4.15.1 と API 35 の Managed Device のみ）では targetSdk 37 の実挙動を検証できないため、上記シナリオ試験が「Android 17 に対応した」ことの主たる検証となる。
- Background Audio Hardening について、SDK が検知して通知できる失敗と、アプリ側の責務の境界を以下で検証し整理すること:
  - SDK の検知範囲: バックグラウンド制限下での録音・再生開始失敗が、シナリオ試験において `onError`（`SoraErrorReason.AUDIO_RECORD_START_ERROR` / `AUDIO_TRACK_START_ERROR` など）としてアプリに通知されるかを検証する。ADM コールバックは WebRTC が明示的に開始失敗を返した場合しか拾えないため、silent failure が通知されない場合はその事実をドキュメント化し、追加の検知方針が必要なら別 issue として切り出す。
  - アプリの責務: FGS/WIU の設定手順とライフサイクル要件をドキュメントで補足し、アプリ側で解決すべきことを明示する。
- ACCESS_LOCAL_NETWORK 権限について、サンプルアプリまたは検証アプリで権限未許可時の失敗を再現し、そのときの SDK 利用者から見える挙動（`onDisconnect` / `onError` などのコールバック、ログ）をドキュメント化し、要求手順のドキュメント整備・失敗挙動のエラー整理・サンプル更新が完了していること。
- Certificate Transparency について、API 37 の実機またはエミュレーターで以下を検証し、結果をドキュメントに反映すること:
  - 公開 CA の WSS に既定設定で接続できること。
  - `caCertificate` を指定した独自 CA の WSS について、NSC の設定なしでの挙動と、`<certificateTransparency enabled="false"/>` を設定した場合の挙動を確認すること。
  - クライアント証明書（mTLS）を使った WSS に接続できること。
  - localhost の WSS に接続できること。
  - CT 違反時に `SSLHandshakeException` が `onError` の message に含まれること。
- `caCertificate` を使うアプリ向けに、NSC の設定例と注意点（ドメイン単位の設定、TURN-TLS との関係、`insecure` の非推奨）がドキュメントに記載されていること。
- サンプル / クイックスタートが `targetSdk 37` でビルドでき、必要な NSC 設定が追加されていること。
- E2E テストに `pixelApi37` を追加し、CT が有効な環境でシグナリング接続のシナリオ試験が通ること。
- 大画面について、サンプル / クイックスタートを sw>=600dp のエミュレーター（タブレット / フォルダブル）で回転・リサイズし、レイアウトと映像表示が破綻しないことを確認し、必要な修正とドキュメント更新を行うこと。
- Safer Native DCL—C について、SDK と E2E テストが `System.load()` を使用していないことを確認し、利用側アプリ向けの注意点をドキュメントに含めるか判断すること。
- ECH について、API 37 の実機で WSS 接続を確認し、問題があれば別 issue として切り出すこと。
- 後方互換に影響する変更がある場合は `CHANGES.md` の `develop` セクションに該当エントリを追記すること。

## 解決方法
