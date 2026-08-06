# Android 17（API レベル 37）に対応する

- Priority: Medium
- Created: 2026-06-03
- Completed:
- Polished: 2026-06-03
- Model: Opus 4.8
- Branch: feature/add-android-17-support

## 目的

Android 17（API レベル 37）のリリースに伴う変更点のうち、Sora Android SDK に影響するものへ追従する。特に Background Audio Hardening への対応を検討し、targetSdk 37 で必須化されるローカルネットワーク権限（ACCESS_LOCAL_NETWORK）についてはアプリ側の対応としてドキュメント・サンプルで補足する。

## 優先度根拠

- `targetSdk` を 37 へ引き上げる際に、Background Audio Hardening まわりで動作が変わる可能性がある。ACCESS_LOCAL_NETWORK 権限の必須化はアプリ側の対応であり、SDK の変更はドキュメント・サンプル整備のみ。
- ただし現状は `targetSdk = 36` であり、API 37 依存の変更は即時には発動しない。`targetSdk` 引き上げのタイミングで対応すればよいため Medium とする。

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
- 参考（本 issue のスコープ外・将来対応の検討材料）:
  - Cleartext 通信の扱いの変更（未発動・将来対応）
    - Android 17 では `usesCleartextTraffic` の非推奨化は「将来のリリースで予定」という計画のみであり、**targetSdk 37 でも未発動**。`usesCleartextTraffic="true"` は引き続き有効。
    - 将来の非推奨化が発動するリリースで Network Security Configuration への移行を検討する。SDK はシグナリング URL をそのまま OkHttp の WebSocket に渡しているため（`SignalingChannel.kt`）、`ws://`（平文）運用のアプリが将来影響を受ける可能性がある。
    - 細部は libwebrtc / `shiguredo-webrtc-android` 側の追従が前提になる可能性がある。
  - ECH（Encrypted Client Hello）: `targetSdk 37` の TLS 接続でデフォルト有効化。HTTPS シグナリングに影響する可能性があるが、プラットフォーム / HTTP スタック寄りの話題であり SDK 固有の対応は見込まれない。DTLS のメディア面には適用されない。
  - アプリメモリ制限（App memory limits）: RAM 総量に応じたプロセスメモリ上限が導入され、超過時は強制終了する。WebRTC アプリはメモリ使用が多いため要確認だが、影響は未確認。
- 補足:
  - SDK 本体の `AndroidManifest.xml` は空に近く、Activity / Manifest 固有の変更（画面回転・リサイズ制約など）は SDK 直撃ではなくアプリ側の課題になる見込み。

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
- 後方互換に影響する変更がある場合は `CHANGES.md` の `develop` セクションに該当エントリを追記すること。

## 解決方法
