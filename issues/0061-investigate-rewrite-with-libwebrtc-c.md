# libwebrtc-c ベースへの全面リライトの実現可能性を調査し方針を確定する

- Priority: High
- Created: 2026-06-16
- Completed:
- Model: Opus 4.7
- Branch: feature/change-rewrite-with-libwebrtc-c
- Polished: 2026-06-16

本 issue 本文中の数値・バージョン・パス・引用は特記なき限り `Created: 2026-06-16` 時点のもの。

## 本 issue の性質と進行方針

本リライトは工事規模が極端に大きく（影響範囲 20 ファイル、`org.webrtc.*` import 89 件、コア 4 ファイル合計 4,095 行）、`shiguredo-git` の 1 issue = 1 branch = 1 PR 規約には収まらない。そのため本 issue は **移行全体の方針を保持する親 issue** として扱う。本 issue の `Branch:` で実施するのは **Phase 0（PoC）のみ**。Phase 1〜5 は Phase 0 完了後に個別 issue として新規起票し、それぞれを独立した 1 issue = 1 branch = 1 PR の単位で `shiguredo-issues` / `shiguredo-git` 規約に従って進める。

「親 issue」は時雨堂 issue 規約には明示されていない概念だが、本 issue の規模に応じた特例として本セクションで明文化する。本 issue は通常の単一カテゴリ規約から逸脱し、`investigate`（rewrite 全体の方針確定）、`add`（Phase 0 PoC で develop にマージする最小 JNI 実装・ビルド構成）、`change`（後述する AAR 依存の `compileOnly` 降格と段階的撤去）の **3 カテゴリにまたがる親 issue 特例** として扱う。ファイル名 prefix は方針確定の側面を強調して `investigate` を維持するが、Branch prefix は develop マージ内容に破壊変更を含む側面を強調して `change` を採用する。`shiguredo/sora-ios-sdk` の issue 0070（後述）は file prefix・branch prefix とも `change` で揃えているが、Android 側は file prefix で「Phase 0 で Go/No-Go 判定する余地」を表に出すため `investigate` を選んだ。Phase 1〜5 の個別 issue は単一カテゴリ規約に従う。

本 issue の close 条件は Phase 0 完了基準を満たし、PoC 結果から Phase 1〜5 の起票が可能と判断できる状態（または「移行は得策ではない」という結論）に到達すること。Phase 1〜5 の進捗は本 issue ではなく個別 issue で管理する。

本 issue は `issues/` 直下（Active）で扱うが、設計判断と外部リポジトリ合意（webrtc-rs / iOS SDK との同期）が多数残るため `/auto-resolve` の対象外とする。Phase 0 着手はユーザー指示で `Branch:` のブランチを手動で切って進める。

### Phase 0 着手前の確認事項

着手前に以下を確認する。確認できない場合は `issues/pending/` に移動し、合意成立後に再 active 化する。

- `shiguredo/webrtc-rs` メンテナ（`@melpon` または `@voluntas`）と「libwebrtc-c に追加が必要な C API」リストの初期合意。
- `shiguredo/sora-ios-sdk` の issue 0070（タイトル「WebRTC.xcframework から libwebrtc_c.xcframework (webrtc_c) への完全移行」、ファイル名 `0070-change-migrate-to-webrtc-c-xcframework.md`）の Phase 0 進捗との同期方針（libwebrtc バージョン整合・追加要請 API 集約・起票責任の分担）。iOS SDK 0070 は投稿時点で `Polished: 2026-06-15` だが、本 issue 0061 への明示的参照は **未追加**。本 issue 着手前に iOS SDK 0070 担当者に「Android 0061 との同期方針セクション追加」を依頼する。依頼が拒否または応答が無い場合のフォールバック規則を後述「### Phase 0 着手前のフォールバック規則」に明記する。
- minSdk 21 → 24 引き上げのユーザー影響の暫定確認（Google Play 統計から API 21〜23 のシェア概算）。

### Phase 0 着手前のフォールバック規則

iOS SDK 0070 担当者からの応答が **着手依頼から 4 週間以内** に得られない場合（あるいは「同期しない」と明示的に拒否された場合）、Android 0061 は単独で先行する。具体的には:

- libwebrtc バージョン: 投稿時点で sora-flutter-sdk Android が利用している webrtc-rs `0.150.0` 系列で暫定確定する。
- webrtc-rs への追加要請の起票責任: Android 側（本 issue 担当者）が全件を起票し、後から iOS SDK 0070 が参加した時点で起票内容を共有する。
- 同時 major bump タイミング: Phase 5 完了時点で iOS SDK 0070 の進捗を再確認し、合意できない場合は片方先行で major bump リリースする。

### Branch の Go/No-Go 時運用

- Go 判定時: 後述「### Phase 0 PoC の develop マージ範囲 / Go 判定時」のとおり最小 change を `develop` にマージする。本ブランチをそのまま `develop` にマージする PR を作成する。
- No-Go 判定時: 本ブランチを破棄して `develop` には変更を入れない（`feature/debug-` 相当の運用）。本 issue の解決方法に No-Go の根拠を追記してから `issues/closed/` に移動する。

## 目的

sora-android-sdk を、現在の `org.webrtc.*` Java/Kotlin API（`com.github.shiguredo:shiguredo-webrtc-android` AAR 経由）への依存から、libwebrtc-c（C ラッパー、`shiguredo/webrtc-rs` で開発・配布）ベースへ全面的に書き直す。`develop` で完全新規実装に置き換え、現状コードは `support/<version>` ブランチへ退避する。

時雨堂のスタックでは libwebrtc を `webrtc-build` で自前ビルドし、薄い C ラッパー libwebrtc-c を `shiguredo/webrtc-rs` で開発して各プラットフォーム向けに配布している。sora-cpp-sdk と sora-flutter-sdk は既に libwebrtc-c 経由で libwebrtc を利用しており、sora-flutter-sdk は Android 統合実績がある。sora-android-sdk は `shiguredo-webrtc-android` AAR（時雨堂自前ビルドだが旧構成）、sora-ios-sdk は Google ObjC SDK（`WebRTC.xcframework`）に依存しており、両 SDK が時雨堂スタックの中で最もメンテ停滞しているコンポーネントへの構造的依存になっている。本 issue ではこの依存を完全に解消し、libwebrtc-c 経由で libwebrtc を直接利用する形に移行する。これにより sora-cpp-sdk / sora-flutter-sdk / sora-ios-sdk / sora-android-sdk が同じ C API 基盤を共有する状態になる。

## 優先度根拠

High。以下の 4 点による。

1. **時雨堂 SDK スタック統一の最後のピース**：sora-cpp-sdk と sora-flutter-sdk は既に libwebrtc-c で本番稼働、sora-ios-sdk でも同種の rewrite が `shiguredo/sora-ios-sdk` の issue 0070 として Phase 0 進行中（着手前確認事項参照）。Android だけが取り残されると、4 SDK 共通の C API レイヤーで保守を集約する戦略が成立しない。
2. **`shiguredo-webrtc-android` AAR 維持コストの削減**：Android (`148.7778.7.0`) と sora-cpp-sdk / sora-flutter-sdk（`m150.7871.0.0`）、sora-ios-sdk（`m148.7778.7.0`）でバージョンが乖離している。libwebrtc-c に集約すれば webrtc-rs のリリースサイクル（最新 `0.150.1` を 2026-06-12 にリリース）に追従するだけで済む。
3. **新機能追加の高速化**：2025〜2026 年に追加された機能（AudioTrackSink: 2025.3.0、ハードミュート関連 API: 2026.1.0、CA 証明書 / client cert / insecure / TURN-TLS 検証 / onSignalingMessage / 映像 H.265 params: develop（2026.2.0-canary.0））は、libwebrtc-c では sora-cpp-sdk / sora-flutter-sdk と共通の C API で実装できる。
4. **closed bug 16 件中 12 件がスレッディング / lifecycle / dispose 起因**（`issues/closed/0001` 〜 `0012` の bug-fix 群）。Coroutine + Flow ベースで設計し直すことで、同種の罠を構造的に防げる。残り 4 件は `0013` / `0014` / `0058` / `0060` で別カテゴリ。

## 現状

### 依存と公開 API の規模

- 依存先: `com.github.shiguredo:shiguredo-webrtc-android:148.7778.7.0`（JitPack 経由 AAR）。`gradle/libs.versions.toml:20, 48` および `sora-android-sdk/build.gradle.kts:176`。
- `org.webrtc.*` import 数: 89 件 / 20 ファイル（`grep -rn '^import org.webrtc' sora-android-sdk/src/main` で計測）。
- 公開クラス: `SoraMediaChannel`, `SoraMediaOption`, `SoraAudioOption`, `SoraVideoOption`, `SoraProxyOption`, `SoraSpotlightOption`, `SoraForwardingFilterOption`, `PeerConnectionOption`, `SoraChannelRole`, `CameraCapturerFactory`、内部 RTC 層として公開状態にある `RTCComponentFactory`、`PeerChannel`（interface）+ `PeerChannelImpl`、`SignalingChannel`（interface）+ `SignalingChannelImpl` も実体としては public class / public interface。本 SDK の公開契約上は内部利用を想定するが、Kotlin 可視性修飾子上は public のため、Phase 0 完了基準で「公開 API として維持」「`internal` 化する破壊変更を入れる」のどちらかを確定する。
- `SoraMediaChannel.Listener` の公開メソッド: 18 メソッド（うち `@Deprecated` 1 件: `onClose(SoraMediaChannel)`、オーバーロード 2 件: `onWarning(reason)` / `onWarning(reason, message)` を含む）。`org.webrtc.*` 型を引数または戻り値に持つもの: `onAddLocalStream(MediaStream)`, `onAddRemoteStream(MediaStream)`, `onPeerConnectionStatsReady(RTCStatsReport)`, `onSenderEncodings(List<RtpParameters.Encoding>)`。`onRemoveRemoteStream` は `(SoraMediaChannel, String label)` で `org.webrtc.*` 型は持たない。
- 公開フィールドで `org.webrtc.*` 型が露出している箇所: `SoraMediaOption.videoEncoderFactory: VideoEncoderFactory?`, `videoDecoderFactory: VideoDecoderFactory?`, `tcpCandidatePolicy: PeerConnection.TcpCandidatePolicy`, `SoraAudioOption.audioDeviceModule: AudioDeviceModule?`, `mediaConstraints: MediaConstraints?`, `SoraProxyOption.type: ProxyType`, `SoraVideoOption.DegradationPreference.nativeValue: RtpParameters.DegradationPreference`、`Catalog.kt` の公開 data class `Encoding.scaleResolutionDownTo: RtpParameters.ResolutionRestriction?`（`OfferMessage.encodings` 経由で `SoraMediaChannel.Listener.onOfferMessage` に流れる）。
- 公開メソッド引数: `enableVideoDownstream(EglBase.Context?)`, `enableVideoUpstream(VideoCapturer, EglBase.Context?, SoraCameraConfig?)`, `enableVideoUpstream(EglBase.Context?, SoraCameraConfig)`, `switchCamera(CameraVideoCapturer.CameraSwitchHandler?)`, `CameraCapturerFactory.create(...)` の戻り値 `CameraVideoCapturer?`。
- `SoraMediaChannel` のコンストラクタ引数 21 個（`SoraMediaChannel.kt:103-132`）の公開状態: `context`, `signalingEndpoint?`, `signalingEndpointCandidates`, `channelId`, `signalingMetadata?`, `mediaOption`, `timeoutSeconds`, `listener?`, `clientId?`, `signalingNotifyMetadata?`, `peerConnectionOption`, `dataChannelSignaling?`, `ignoreDisconnectWebSocket?`, `dataChannels?`, `bundleId?`, `forwardingFilterOption?`（`@Deprecated`）, `forwardingFiltersOption?`, `insecure`, `caCertificate?`, `clientCertificate?`, `clientPrivateKey?`。`@JvmOverloads` で Java から binary 互換のオーバーロードが生成されるため、引数順と型は major bump 時にも維持する。

### 機能セットの規模

CHANGES.md と公開 API から逆引きした主要カテゴリ:

- Sora シグナリングメッセージ（WebSocket / DataChannel）17 種類、`MessageConverter.kt` の build / parse 関数 19 関数で実装（`parseUpdateMessage` / `UpdateMessage` は Sora 2022.1.0 で廃止された `update` 型のものを残置している）。`MessageConverter.companion` の `gson: Gson`、`TAG` は public、`gsonSerializeNulls` は private。
- `Catalog.kt` の `ConnectMessage` は 28 フィールド（うち SDK 自動設定の `type` / `soraClient` / `libwebrtc` / `environment` 4 件を除いた利用者向けは 24 フィールド）。`OfferMessage` は 23 フィールド（type, sdp, version?, simulcast, simulcastMulticodec?, spotlight?, channelId?, clientId, bundleId?, connectionId, sessionId?, metadata, config?, mid?, encodings?, dataChannels?, rpcMethods?, audio?, audioCodecType?, audioBitRate?, video?, videoCodecType?, videoBitRate?。Kotlin 上 default 値なし（必須位置）のフィールド: `sdp` / `clientId` / `connectionId` / `metadata`）。`NotificationMessage` は 33 フィールド + Sora サーバー仕様上の `event_type` 13 種（SDK 側は `connection.created` / `connection.destroyed` の 2 種だけを `SoraMediaChannel` 内で識別し、他はそのまま `onNotificationMessage` に流す）。
- メディア送受信、Audio Codec（Opus + `OpusParams` 8 フィールド: channels / clock_rate / maxplaybackrate / stereo / sprop_stereo / minptime / useinbandfec / usedtx の `@SerializedName` 経由 JSON 名）、Video Codec（VP8 / VP9 / AV1 / H.264 / H.265 + 各 params）。
- Simulcast（rid / scalabilityMode / maxBitrate / scaleResolutionDownBy / scaleResolutionDownTo / networkPriority）、Spotlight、SVC。
- DataChannel（圧縮、signaling / stats / notify / push / rpc 予約ラベル）、RPC（JSON-RPC 2.0、`SoraRpcParser`）、Forwarding Filter、Stats、Notify、Role 管理、Bundle ID、Metadata、Timeout。
- TLS / CA 証明書 / client cert / insecure / TURN-TLS（`TurnTlsCertificateVerifier`, `TlsConfigFactory` は `internal object` で公開 6 関数 + private 2 関数の計 8 関数）、Proxy、AES-GCM、DTLS ECDSA。
- HW encoder ラップ 3 段（`SoraDefaultVideoEncoderFactory` / `SimulcastVideoEncoderFactoryWrapper` / `HardwareVideoEncoderWrapperFactory`）、`CropSizeCalculator`、`StreamEncoderWrapper`。
- AudioDeviceModule ラップ（`AudioDeviceModuleWrapper`）、ハードミュート / ソフトミュート、Bluetooth SCO routing。
- カメラ制御（`switchCamera` / `changeCaptureFormat`、内部実装は `RTCLocalVideoManager` 経由）、AudioTrackSink（2025.3.0 追加。SDK 内には新規型は無く、`org.webrtc.AudioTrackSink` インターフェースを `org.webrtc.AudioTrack` クラスの `addSink` / `removeSink` メソッド経由で添付する実装）。

### コア 4 ファイルの規模

`wc -l` 実測:

| ファイル | 行数 | 役割 |
|---|---|---|
| `SoraMediaChannel.kt` | 1917 | Sora SDK 主要 API、ライフサイクル管理、Listener 配分、RPC、メッセージング |
| `PeerChannel.kt` | 1150 | PeerConnection ラッパー、SDP 処理、track / DataChannel 管理、メディア制御、DataChannel 圧縮 |
| `SignalingChannel.kt` | 795 | WebSocket シグナリング、複数 URL フェイルオーバー、TLS |
| `MessageConverter.kt` | 233 | Sora シグナリングメッセージのシリアライズ / デシリアライズ |

加えて `RTCComponentFactory.kt`、`PeerNetworkConfig.kt`、`RTCLocalAudioManager.kt`、`RTCLocalVideoManager.kt`、`AudioDeviceModuleWrapper.kt`、`TurnTlsCertificateVerifier.kt`、`TlsConfigFactory.kt`、`CameraCapturerFactory.kt` で内部 RTC 層を構成（`RTCComponentFactory` / `PeerChannel` / `SignalingChannel` は Kotlin 可視性上は public）。

### バージョン体系の現状

- SDK バージョン: 直近正式版 `2026.1.0`（2026-03-02）、develop は `2026.2.0-canary.0`。
- libwebrtc: `148.7778.7.0`（m148 系）。
- リリース: tag push に JitPack が追従。
- canary: `canary.py` が `SDKInfo.kt:9` の `VERSION` を更新してタグ push する。

### 既存の負債（closed issues から）

`issues/closed/0001` 〜 `0012` の 12 件は連続した bug fix 群で、いずれもライフサイクル / threading / dispose 関連。並行制御プリミティブは RxJava 2 + kotlinx.coroutines 1.9.0 + Java Concurrency（`Handler/Looper`, `ExecutorService`, `AtomicXxx`, `synchronized`, `@Volatile`）の混在。

## 設計方針

### 採用する基盤

- libwebrtc-c（`shiguredo/webrtc-rs` で開発、Android arm64 prebuilt を `0.146.2` 以降配布、最新 `0.150.1`）を採用する。Android 向けアーティファクトには `libwebrtc_c.a` と `webrtc.jar` を同梱する想定。sora-flutter-sdk Android が webrtc-rs `0.150.0` で稼働している。
- 別途 webrtc-build から `webrtc.android.tar.gz`（`libwebrtc.a` を含む）を取得し、libwebrtc-c.a と一体ビルドする。sora-flutter-sdk と同じパターン。
- AAR `com.github.shiguredo:shiguredo-webrtc-android` への依存は段階的に撤去する（後述「### AAR 撤去の段階的戦略」）。`libjingle_peerconnection_so.so` と libwebrtc-c が同居するとシンボル衝突する技術的根拠は本 issue の「## 実現可能性 / 既存 AAR との同居が困難な理由」セクションに記載。
- パッケージ `org.webrtc.*` の Java/Kotlin クラス（`EglBase`, `SurfaceViewRenderer`, `SurfaceTextureHelper`, `CameraVideoCapturer`, `Camera1/2Enumerator`, `JavaAudioDeviceModule`, `HardwareVideoEncoderFactory`, `DefaultVideoDecoderFactory`、`WebrtcBuildVersion` 等）は、libwebrtc-c が同梱する `webrtc.jar` に含まれる前提でそのまま利用する。`webrtc.jar` の同梱と必要シンボルの存在は Phase 0 完了基準 #2 で実物確認する。

### AAR 撤去の段階的戦略

Phase 0 マージ時点で AAR を完全撤去すると、`SDKInfo.kt:5` の `import org.webrtc.WebrtcBuildVersion` を含む SDK 本体 20 ファイル分の参照がコンパイル不能になり、`develop` が壊れる（broken windows 違反）。一方、AAR を `compileOnly` に降格すると AAR の native ライブラリは APK / AAR に同梱されなくなるが、`WebrtcBuildVersion.webrtc_branch` / `webrtc_revision` を直接参照している `SDKInfo.libwebrtcInfo()`（`SDKInfo.kt:13-16`）は runtime に `NoClassDefFoundError` でクラッシュする。`libwebrtcInfo()` は `ConnectMessage` の `libwebrtc` フィールドに埋め込まれる必須経路のため、既存ユーザーの接続を壊す。これを避けるため、AAR 撤去を以下の 2 段階に分ける。

- **Phase 0 マージ（本 issue Go 判定時）**: 以下を同一 PR でアトミックに行う。
  - `gradle/libs.versions.toml` の `shiguredo-webrtc-android` 依存を `api(libs.shiguredo.webrtc.android)` から `compileOnly(libs.shiguredo.webrtc.android)` に降格する。これにより AAR のクラスはコンパイル時参照できるが、AAR の native ライブラリ `libjingle_peerconnection_so.so` は最終 APK / AAR には同梱されない。
  - `SDKInfo.libwebrtcInfo()` を `try { Class.forName("org.webrtc.WebrtcBuildVersion") ... } catch (NoClassDefFoundError | ClassNotFoundException) { fallback }` の reflection + フォールバック実装に書き換える（`SoraMediaChannel.kt:1059` で既に同様のパターンを採用済み）。fallback 値は `BuildConfig.LIBWEBRTC_VERSION` を含む暫定文字列とし、Phase 1 の AAR 完全撤去時に `webrtc.jar` 同梱版または完了基準 #14 の代替経路に切り替える。
  - PoC テスト（`PoCSmokeTest.kt`）が libwebrtc-c 由来の `libsora_android_sdk.so` だけをロードする状況を成立させる。`SoraE2ETest` 等は `@Ignore` する。
- **Phase 1 の最初の子 issue**: AAR 依存を完全撤去（`compileOnly` も削除）。同 PR 内で `SDKInfo.kt` の reflection フォールバックを libwebrtc-c 同梱 `webrtc.jar` 経由（または完了基準 #14 の代替経路）に置き換え、SDK 本体 20 ファイル分の `import org.webrtc.*` を Java 側に残す層が利用する `webrtc.jar` 経由に切り替える。`@Ignore` していた既存 E2E は新基盤前提に書き直すか削除する。

### 公開インターフェース維持の方針

- パッケージ名 `jp.shiguredo.sora.sdk.*` は維持する。
- 互換維持の **シンボル全リスト** は「## 互換維持シンボル一覧」セクション参照。
- 互換のない変更（破壊変更）の **全リスト** は「## 破壊変更リスト」セクション参照。Phase 0 完了時点で「暫定確定」とし、Phase 2 以降の実装中に判明する細部は適宜追加する。「暫定」の範囲: Phase 0 完了時点で公開シンボル全件の分類は完了している前提とし、Phase 2 以降の細部追加は「内部実装に絡む新規発見シンボル / 表記揺れの吸収 / 細かい型整形」に限定する。
- 設計の大方針: クラス名・メソッド名・コールバック名・enum / data class の値を維持する。`org.webrtc.*` 型が露出している箇所は Sora 独自型に置換するソース非互換変更を行うが、移行ガイドで `import` 文と型名の機械的置換だけで対応できる範囲を目標とする。
- 個別シンボルの維持 / 削除判断は「## 互換維持シンボル一覧」「## 破壊変更リスト」を参照。
- `SDKInfo.sdkInfo()` / `libwebrtcInfo()` / `deviceInfo()` の文字列フォーマットは厳格に保持する（Sora サーバーが `sora_client` / `libwebrtc` / `environment` 値を期待しているため）。`libwebrtcInfo()` の `<LIBWEBRTC_VERSION>` 供給経路の変更要否は完了基準 #14 で確定する。

### Java/Kotlin 側に残す層

- HW encoder ラップ 3 段（`SoraDefaultVideoEncoderFactory` / `SimulcastVideoEncoderFactoryWrapper` / `HardwareVideoEncoderWrapperFactory`）。Chromium issue 1084702（HW encoder 初期化時の解像度 16 倍数チェック）、Xperia 5 II + Android 11 + VGA H.264 Simulcast 初期化失敗、MediaCodec 例外時の fallback retry など、端末互換性のための既知 bug 回避を Java 側で維持する。`SoraVideoOption.ResolutionAdjustment` の `MULTIPLE_OF_2` / `_4` / `_8` / `_16` / `NONE` も継続提供する。Java factory を libwebrtc-c の `webrtc_JavaToNativeVideoEncoderFactory()`（`sdk/android/native_api/codecs/wrapper.h` 相当）でラップして native 化する想定。
- `JavaAudioDeviceModule` も Java 側で生成し、`webrtc_CreateJavaAudioDeviceModule()` 相当で native 化する。`SoraAudioOption` の `useHardwareAcousticEchoCanceler` / `useHardwareNoiseSuppressor` / `audioSource` / `useStereoInput/Output` / `audioProcessingEchoCancellation` / `audioProcessingAutoGainControl` / `audioProcessingHighpassFilter` / `audioProcessingNoiseSuppression` の Java 側設定はそのまま機能する想定。
- TLS 関連: WSS は OkHttpClient + Java SSLContext / X509TrustManager / KeyStore / KeyManager のスタックで現状と同じく実装。TURN-TLS は `TurnTlsCertificateVerifier` を libwebrtc-c の `SSLCertificateVerifier` の `verifyChain` コールバック経由で接続。クライアント証明書は `SSLIdentity_CreateFromPEMStrings` 相当を使い `IceServer` の TLS client identity 設定にセット。WSS と TURN-TLS で同一の CA 証明書ストアを共有する設計パターン（OkHttpClient 用 KeyStore と `SSLCertificateVerifier` 用 PEM の整合）は Phase 0 PoC で確定する（完了基準 #13）。
- カメラ: `Camera2Enumerator` / `CameraVideoCapturer.initialize()` / `startCapture` / `switchCamera` / `changeCaptureFormat` は Java 側でそのまま利用する。
- リモート映像描画: `Surface` → `ANativeWindow_fromSurface()` で C 側に渡し、`VideoSinkInterface` の `OnFrame` で I420 → ARGB を libyuv 変換して `ANativeWindow_lock/unlockAndPost` で描画。sora-flutter-sdk の JNI 統合と同じパターン。`SurfaceViewRenderer` 相当の API を Compose / View System の両方から利用できるかは Phase 4 で確定する。
- `ZipHelper`（zlib 圧縮）、`SoraRpcParser`（JSON-RPC 2.0）、`SoraRpc*` 例外体系、`SoraLogger`、`SoraErrorReason` / `SoraDisconnectReason` / `SoraCloseEvent` / `SDKInfo` はそのまま流用する。
- `MessageConverter` の `metadata` / `signalingNotifyMetadata` の `serializeNulls` 特殊シリアライズ処理（null フィールドを含めて JSON 化する Gson 二段処理）は Phase 3 で正確に維持する。`dataToString(data: ByteBuffer)` の `@Synchronized` セマンティクスも維持する。

### スレッディングモデル

- 公開 API は新規メソッドを suspend 関数 + `Flow<SoraEvent>` ベースで追加する。既存の `SoraMediaChannel.connect()` / `disconnect()` などはシグネチャ互換のため非 suspend のまま残す。`SoraMediaChannel.getStats()` / `setAudioHardMute()` / `setAudioRecordingPaused()` / `rpc()` は既に `suspend fun` であり、互換維持シンボル一覧でも `suspend` として維持する。
- 内部の RxJava 2（`Single`, `Schedulers.from(executor)`）はすべて Coroutine + `CoroutineDispatcher` に置き換える。
- libwebrtc-c が起動する 3 スレッド（network / worker / signaling）と Kotlin Coroutine の `Dispatchers.Default` / `Dispatchers.IO` / `Dispatchers.Main` の整合性は Phase 0 PoC で確認する。WebRTC native callback は heap コピーした上で `MutableSharedFlow` に emit し、メインスレッドで配信する。
- mutable フィールドへの参照は `val snapshot = field ?: return` の snapshot pattern を強制する（closed `0005` / `0006` / `0007` 教訓）。
- `JavaAudioDeviceModule.pauseRecording` / `resumeRecording` を扱う `AudioDeviceModuleWrapper` の `HandlerThread + asCoroutineDispatcher()` パターンは維持する。
- `DummyVideoCapturer` 系の I420 生成ロジックは流用可能。テスト基盤の流用率は Phase 0 で実測する（既存テストは `src/test/` 6 ファイル、`src/androidTest/` 2 ファイル）。

### libwebrtc バージョン確定方針

libwebrtc バージョン（m148 / m150 系列）の確定は `shiguredo/sora-ios-sdk` の issue 0070 と同期して Phase 0 完了基準 #8 で行う。iOS SDK 0070 が「Phase 0 着手前のフォールバック規則」に該当する場合は Android 単独で webrtc-rs `0.150.0` を採用する。

SDK バージョンの主系列（`YYYY.M.PATCH`）の維持と major bump 番号の確定方針は「## ブランチ・リリース戦略 / バージョン番号体系」を参照。

## 実現可能性

### libwebrtc-c の Android arm64 リリース実績

- webrtc-rs のリリースワークフローで `aarch64-linux-android` 向け prebuilt が毎リリース生成されている。
- 最新 `0.150.1`（2026-06-12）まで途切れなくリリース。
- ターゲット ABI は arm64-v8a のみ。他 ABI（armeabi-v7a / x86_64）は未対応。

### コア API カバレッジ

- PeerConnectionFactory / PeerConnection / PeerConnectionObserver / CreateSessionDescriptionObserver / SetLocal/RemoteDescriptionObserver / SessionDescriptionInterface / IceCandidateInterface / SdpType: 完備。
- MediaStream / MediaStreamTrack / VideoTrack / AudioTrack / VideoTrackSourceInterface / AdaptedVideoTrackSource: 完備。
- RtpSenderInterface / RtpReceiverInterface / RtpTransceiverInterface / RtpParameters / RtpEncodingParameters（rid / active / maxBitrate / maxFramerate / scaleResolutionDownBy / scaleResolutionDownTo / scalabilityMode / networkPriority）: 完備。
- DataChannelInterface / DataChannelObserver: 基本は完備。
- Stats: `RTCStatsCollectorCallback` / `RTCStatsReport` / `ToJson()` 完備。エントリー単位の列挙 API は「## webrtc-rs への追加要請」セクション参照。
- VideoFrame / I420Buffer / NV12Buffer / VideoFrameBuffer / VideoFrameBuilder / EncodedImage: 完備。
- SimulcastStream / SimulcastEncoderAdapter: 完備。
- Built-in AudioEncoderFactory / AudioDecoderFactory: 完備。Opus パラメータ設定可。

### Android 統合 API カバレッジ

- `webrtc_CreateJavaAudioDeviceModule(JNIEnv*, Environment*, jobject application_context)`: 完備。
- `webrtc_JavaToNativeVideoEncoderFactory(JNIEnv*, jobject)` / `webrtc_JavaToNativeVideoDecoderFactory(JNIEnv*, jobject)`: 完備。
- `webrtc_InitClassLoader(JNIEnv*)` / `webrtc_GetClass(...)` / `webrtc_jni_InitGlobalJniVariables(JavaVM*)` / `webrtc_jni_AttachCurrentThreadIfNeeded()`: 完備。
- EglBase / SurfaceTextureHelper / SurfaceViewRenderer の C ラッパーは無いが、Java 側で `webrtc.jar` 経由で利用する想定。

### TLS / CA / Proxy

- `SSLCertificateVerifier_new(cbs, user_data)` + `SSLCertChain_GetSize/Get` + `SSLCertificate_*`: 完備。
- `SSLIdentity_CreateFromPEMStrings(key_pem, cert_pem)` / `CreateFromPEMChainStrings(...)`: 完備。
- `PeerConnectionInterface_IceServer_set_tls_cert_policy(...)` / `set_tls_client_identity(...)`: 完備。
- `PeerConnectionDependencies_set_proxy(...)` / `set_tls_cert_verifier(...)`: 完備。

### 既存 AAR との同居が困難な理由

`shiguredo-webrtc-android` AAR は `libjingle_peerconnection_so.so` を内包し、これに libwebrtc の C++ 実装と JNI シンボルが含まれる。一方 libwebrtc-c は `libwebrtc.a` を静的リンクして `libsora_android_sdk.so` を生成する設計。同一プロセスに両方が同居すると以下の問題が起きる（webrtc-rs `0.150.1` のビルド設定で確認）:

- libwebrtc のコア C++ シンボル（例: `webrtc::CreatePeerConnectionFactory`）が二重定義となる。
- libwebrtc-c の Android ビルドは `CXX_VISIBILITY_PRESET hidden` を設定しておらず、シンボルの export 制御はリンカスクリプト（`webrtc.ldflags` / `libwebrtc_c_api.ldflags`）に完全依存する。
- `_LIBCPP_ABI_NAMESPACE=Cr` は libc++ にしか効かないため、WebRTC 自体の C++ シンボル衝突は防げない。

この同居を避けるため、AAR は段階的に撤去する（「## 設計方針 / AAR 撤去の段階的戦略」参照）。PoC 期間中の AAR `compileOnly` 降格は、APK / AAR に AAR 由来 `.so` を同梱しない（=プロセスに loaded されない）状態を作るための措置。`@Ignore` 単独では既存 `SoraE2ETest.kt:74` の `System.loadLibrary("jingle_peerconnection_so")` 呼び出しを止められても、Test APK の `nativeLibraryDir` 配下に配置された AAR 由来 `.so` を `dlopen` で参照可能な状態が残るため、`compileOnly` 降格と `@Ignore` の両方を組み合わせる。

## webrtc-rs への追加要請

本 issue の作業範囲は「(a) 追加要請 API リストの確定、(b) `shiguredo/webrtc-rs` リポジトリでの追加検討 issue 起票、(c) メンテナとの初期合意取得、(d) 起票 issue リンクを本 issue に転記」までである。各 API の実装そのものは webrtc-rs 側の別 issue として進める。

優先度タグ: **[Blocker]** = Phase 1 着手のブロッカー、**[Workaroundable]** = sora-android-sdk 側で代替実装可能、**[Optional]** = 削除可。

### [Blocker]（iOS と共通、`shiguredo/sora-ios-sdk` の issue 0070 の Blocker と同一の要件）

各項目に「未起票 / 起票済み (`shiguredo/webrtc-rs#XXX`) / 初期合意済み / 実装済み」のステータス列を Phase 0 で付与する。

- Stats エントリー単位列挙: `RTCStatsReport_get_entries` / `RTCStatsEntry_get_id` / `get_type` / `get_timestamp_us` / `get_values` 相当。
- PeerConnection: `GetTransceivers` / `GetSenders` / `GetReceivers`、`PeerConnectionObserver.OnSignalingChange` の有効化。
- RtpTransceiver: `mid()` / `sender()` / `direction()` / `current_direction()` / `SetDirection()` / `Stop()`。
- RtpSender: `track()` / `SetStreamIds()`。
- AudioDeviceModule: `pauseRecording()` / `resumeRecording()`（`setAudioHardMute` の Android 実装で必要）。
- VideoEncoderFactory: `RTCVideoEncoderFactorySimulcast` 相当の Simulcast Factory 生成 C API。
- SSLCertificateVerifier: `verifyChain` 相当のコールバック登録 API（Android では既に提供されている想定だが iOS で必要、両 SDK で同一仕様を共有）。

### Android 固有の追加要請

各項目は webrtc-rs `0.150.1` ヘッダを確認したうえで「現状は提供されていない」と判定したもの。Phase 0 着手時に最新 webrtc-rs リリースで再確認する。Android 固有の追加要請は Phase 0 PoC で実物確認した結果に応じて増える可能性がある。

- `DataChannelInit_set_max_retransmits()` / `DataChannelInit_set_max_packet_life_time()` 相当（現状は `ordered` / `protocol` のみ）。
- `PeerConnectionInterface_RTCConfiguration_set_bundle_policy` / `set_rtcp_mux_policy` / `set_key_type` / `set_tcp_candidate_policy` / `set_enable_cpu_overuse_detection`（個別セッター）。
- `CryptoOptions` 構造体と `set_enable_gcm_crypto_suites()` 等（現状は構造体未定義、`set_crypto_options()` 経由）。
- `WebrtcBuildVersion` 相当の libwebrtc バージョン情報シンボルが `webrtc.jar` に含まれない場合、`webrtc.properties` 等の代替供給。具体的な代替は完了基準 #14 で確定する。

### [Workaroundable] / [Optional]

`shiguredo/sora-ios-sdk` の issue 0070 で列挙されている代替策（`OnIceCandidatesRemoved`、`OnRenegotiationNeeded`、レガシー Plan B、`AudioSession` 直接利用、`DataChannel.OnBufferedAmountChange`、Logger コールバック廃止、`AudioSource.volume` など）は、Android では現時点で必要性が薄いか別経路で吸収可能と推定する。Phase 0 で 1 項目ずつ再評価し、本 issue に Android 視点の代替戦略を 1 行ずつ追記する。Android 固有の [Optional] 項目は現時点で確定なし。

## 完了条件

本 issue の完了条件は **Phase 0 完了基準を満たすこと**。Phase 1 以降の完了条件は各 Phase の個別 issue で定義する。

### Phase 0 完了基準（必須項目）

ビルド構成と最小 JNI 実装の確認:

1. `gradle/libs.versions.toml` の `shiguredo-webrtc-android` 依存を `compileOnly` に降格する方針を確定する（撤去戦略は「## 設計方針 / AAR 撤去の段階的戦略」参照）。`scripts/native_deps.json`（新規作成）+ `scripts/fetch_native_deps.*`（新規作成、言語選定候補は Kotlin Script / Gradle Init Script / Dart / Python から選定。判断軸は (i) CI runner 追加インストール不要、(ii) `canary.py` 等既存スクリプトとの言語整合、(iii) ローカル開発環境でのセットアップ容易性、(iv) Gradle 統合度、の順で評価。第一候補は `canary.py` との整合性から Python、次点が Gradle Init Script。Dart は sora-flutter-sdk 前例があるが追加ランタイム負担となるため第三候補）でビルド構成を確立する。インストール先パスは sora-flutter-sdk と整合させ `<repo-root>/third_party/libwebrtc-c/` を第一候補とする。`.gitignore` 方針も含め Phase 0 着手時に確定する。
2. `sora-android-sdk/src/main/cpp/CMakeLists.txt`（新規作成）を作成し、`libwebrtc-c.a` + `libwebrtc.a` を静的リンクした `libsora_android_sdk.so` を arm64-v8a 向けにビルド成功させる。`jni_onload.c`（JNI_OnLoad で `webrtc_jni_InitGlobalJniVariables(vm)` / `webrtc_InitClassLoader(env)` を呼ぶ）を同時に新規作成する。`webrtc.ldflags` / `libwebrtc_c_api.ldflags` の入手元（webrtc-rs リリース同梱か、SDK 側コピーか）を確定する。同時に `webrtc.jar` の同梱と `WebrtcBuildVersion` 等の必要シンボル（`org.webrtc.SurfaceViewRenderer` / `EglBase` / `JavaAudioDeviceModule` 等）の存在を `unzip -l libwebrtc_c-android_arm64/webrtc.jar` 等で実物確認する。
3. JNI 統合の骨格を実装し、application context のグローバル参照保持を行う `nativeInitializeAndroid(Context)` を暫定パッケージ（候補: `jp.shiguredo.sora.sdk.internal.NativeBridge`）から Kotlin で呼べる状態にする。
4. `webrtc_CreateModularPeerConnectionFactory` で PeerConnectionFactory を生成し、`webrtc_CreateJavaAudioDeviceModule` で ADM を接続、`webrtc_CreatePeerConnectionOrError` で PeerConnection を生成し、CreateOffer まで通す最小経路を Kotlin から実行可能にする。検証は新規 PoC テストとして `sora-android-sdk/src/androidTest/kotlin/jp/shiguredo/sora/sdk/PoCSmokeTest.kt` を新設し、`./gradlew :sora-android-sdk:assembleDebug` でビルドし、Gradle Managed Device 経由で動作確認する。PoC 専用の Gradle task `pixelApi35PoCSmokeTest` を `build.gradle.kts` に新設し、`testInstrumentationRunnerArguments` を build script 内で `class = "jp.shiguredo.sora.sdk.PoCSmokeTest"` 指定する（既存の `pixelApi35AndroidE2ETest` は AAR 同居前提のため流用しない）。PoC テストと PoC 専用 task は Phase 0 完了時に削除する。AAR 同居問題は「## 設計方針 / AAR 撤去の段階的戦略」の `compileOnly` 降格と `SoraE2ETest.kt` 等の `@Ignore` を両方適用して回避する。

外部リポジトリ合意:

5. 「## webrtc-rs への追加要請」セクションの API リストを最終化し、各項目に「未起票 / 起票済み / 初期合意済み / 実装済み」のステータス列を付与する。`shiguredo/sora-ios-sdk` の issue 0070 担当者と起票責任を分担する（着手前のフォールバック規則該当時は Android 単独で全件起票）。`shiguredo/webrtc-rs` リポジトリで該当 issue にメンテナ（`@melpon` または `@voluntas`）の **明示的賛成コメントが付与された状態** を「合意」と定義する。Phase 0 着手から **2 週間以内** に「初期合意」（API 名と responsibility の方向性で OK）、**4 週間以内** に「実装合意」（シグネチャの細部まで詰める）を目指す。4 週間時点で初期合意未取得の [Blocker] が `1 件以下` ならば Conditional Go（Phase 1 着手前に再合意取得）、`2 件以上` ならば No-Go。

シンボルの分類確定:

6. 「## 互換維持シンボル一覧」と「## 破壊変更リスト」を本 issue 内で暫定確定する。`SoraMediaChannel` のコンストラクタ引数 21 個、`SoraMediaOption` / `SoraAudioOption` / `SoraSpotlightOption` / `SoraProxyOption` / `PeerConnectionOption` の全公開フィールド・メソッドを「互換維持」「破壊変更」「削除」のいずれかに分類する。`RTCComponentFactory` / `PeerChannel` / `SignalingChannel` interface の公開性方針（`internal` 化破壊変更か、`org.webrtc.*` 型置換破壊変更か）を確定する。

ビルド要件:

7. ABI ターゲットを arm64-v8a 単独で開始することを確定する。armeabi-v7a / x86_64 の現状サポート状況と削減のユーザー影響（特に Wear OS の armeabi-v7a、x86_64 エミュレータ環境影響）を CHANGES.md `[CHANGE]` 候補として明記する。CHANGES.md への実エントリ追加は Phase 5 の最終リリース子 issue で行う。
8. libwebrtc バージョンを m148 系列維持か m150 系列以降に上げるかを `shiguredo/sora-ios-sdk` の issue 0070 と同期して確定する（着手前のフォールバック規則該当時は webrtc-rs `0.150.0`）。
9. minSdk を 21 から 24 / 26 / 29 のいずれかに引き上げる方針を確定する（libwebrtc-c の `android-platform = "android-24"` 要件、sora-flutter-sdk Android が `minSdkVersion 29` を採用している理由を調査）。Google Play 統計などから採用バージョン未満のユーザーシェアを把握し、CHANGES.md `[CHANGE]` 候補として明記。`pending/0056` AAudio 移行（API 26 必要）の可否判定もここで併せて行う。
10. NDK バージョン（webrtc-rs `Cargo.toml` 指定の 27.2.12479018 か、sora-flutter-sdk Android 採用の 28.2.13676358 か）、CMake 4.2+、Chromium 専用 clang の利用方針を確定する。CI セットアップ手順（`sdkmanager "ndk;<version>"` 実行、CMake 入手方法、Chromium clang 入手経路、`gradle/libs.versions.toml` での NDK バージョン宣言）を本 issue に追記する。

技術検証:

11. libwebrtc-c の m148 / m150 系列で `shiguredo-webrtc-build` の HW encoder 解像度チェック無効化パッチが当たっているかを確認する。(a) 第一段階: webrtc-build / webrtc-rs リポジトリの patch ディレクトリを grep してコードベース確認、(b) 第二段階: 実機が利用可能なら Xperia 5 II（Android 11 + VGA H.264）で MediaCodec 例外発生有無を確認、実機が利用不可なら CI の Pixel 7 + API 35 で H.264 を試し例外発生が無いことを最低限の根拠とし最終確認は Phase 4 機種互換 issue でカバー。Go/No-Go への影響: パッチ未適用が確定した場合でも `MULTIPLE_OF_16` 調整は Phase 4 で維持するため Go 阻害要因にはならない（情報収集に降格）。
12. `SimulcastVideoEncoderFactoryWrapper` 相当の Simulcast Factory を `JavaToNativeVideoEncoderFactory` ラップで動作させられるかを確認する。検証手段: PoC テスト内で **VP8 Simulcast** で `RtpSender.parameters.encodings.size == 3` を assert する。H.264 Simulcast は機種互換性の問題が Phase 4 で扱うため Phase 0 では VP8 で動作確認。
13. WSS と TURN-TLS の同一 CA 証明書ストア共有構成を確定する。検証方法: (i) テスト用自己署名 CA を PEM で用意し、Java KeyStore（PKCS12）と PEM 文字列の両方を保持する設計確定、(ii) WSS 側で OkHttpClient + X509TrustManager（KeyStore から構築）でテスト用 TURN-TLS サーバーへ接続成功、(iii) TURN-TLS 側で libwebrtc-c の SSLCertificateVerifier verifyChain callback 内で同じ KeyStore を参照し、PeerConnection IceServer 経由で TURN-TLS 接続成功、(iv) 同一の証明書セット参照を Logger で記録し PoC 結果に追記。

周辺ツール:

14. `SDKInfo.libwebrtcInfo()` の `<LIBWEBRTC_VERSION>` 供給経路と `WebrtcBuildVersion` 代替経路を確定する。候補: (A) webrtc-rs リリース時に同梱される properties ファイルから抽出（webrtc-rs 側追加要請）、(B) sora-android-sdk 側で `canary.py` / 手動更新で `scripts/native_deps.json` に `webrtc.branch` / `webrtc.revision` を追記して `build.gradle.kts` から Gson 等で読み取り `BuildConfig.LIBWEBRTC_VERSION` / `BuildConfig.LIBWEBRTC_BRANCH` / `BuildConfig.LIBWEBRTC_REVISION` に注入、(C) hardcode。第一候補は方式 B（CI 環境依存最小、`canary.py` 拡張で対応可能）。`canary.py` 側の変更要否も同時に確定する。

周辺リポジトリ:

15. `sora-android-sdk-samples` 側（`shiguredo/sora-android-sdk-samples` の `develop`）で `org.webrtc.*` を直接使っている箇所の有無を `grep -rn "^import org.webrtc" src/` で調査し、本 issue の「## 既存 issue との関係 / sora-android-sdk-samples 影響調査」サブセクションに以下を追記: import を含むファイル一覧 / 利用クラス・メソッド / Phase 2 並行起票する対応 PR の方針（書き換え対象ファイル数、想定 Branch 名）。samples 対応 PR は Phase 2 着手時に並行起票し、Phase 2〜4 期間中も samples ビルドが通り続けるよう調整する。
16. NOTICE / LICENSE の更新方針を確定する。依存先が `shiguredo-webrtc-build` から `shiguredo/webrtc-rs` の C ラッパー部に変わる。

残務:

17. ProGuard / R8 ルール、および `consumer-rules.pro` 新設の方針を確定する（sora-flutter-sdk の `consumer-rules.pro` を雛形に `-keep class org.jni_zero.** { *; }` / `-keep class org.webrtc.** { *; }` 等を含める。実装は Phase 1 以降の子 issue で行う）。
18. Phase 1〜5 の個別 issue の分割粒度・順序・依存関係を確定する。各子 issue の Branch 命名規則例（例: Phase 1 → `feature/add-jni-webrtc-c-bridge`）を本 issue に追記する。SEQUENCE 採番は Phase 0 完了時点の `issues/SEQUENCE` 値から起票時に順番に取得し、子 issue 間で番号の連続性は要求しない（途中で他 issue が SEQUENCE を消費しても順番ずれを許容）。各 Phase の子 issue は本 issue の「### Phase 1〜5（Phase 0 完了後に個別 issue として起票）」セクションに「起票後の issue 番号」を追記してトレース可能にする。
19. Compose UI / View System 双方からのユーザー利用に対する `SurfaceViewRenderer` 相当のレンダラ提供方針を確定する。調査対象: (a) `webrtc.jar` 同梱の `SurfaceViewRenderer` が AAR 撤去後もそのまま利用可能か（`unzip + javap` で `webrtc.jar` のクラス一覧確認）、(b) `sora-android-sdk-samples` の `develop` を `grep -rn 'SurfaceViewRenderer\|VideoSink\|EglBase' src/` し、利用箇所と推奨書き換え方針を本 issue に追記、(c) Compose 統合: `AndroidView` wrapper 経由で `SurfaceViewRenderer` を Compose に組み込むパターンの雛形を本 issue に追記。
20. 既存 26 active issue + 17 pending issue の rewrite 関連性を再評価する。「## 既存 issue との関係」セクションに本 issue の `Created: 2026-06-16` 時点の暫定分類が示されているが、Phase 0 完了時にこれを最終確定する。各 issue を「rewrite で吸収（→ どの Phase の子 issue で対応）」「独立対応（→ rewrite と並行に既存方針で対応）」「rewrite 完了後に再起票検討」「pending 維持」のいずれかに分類し、本 issue の「## 既存 issue との関係」セクションを最終形に書き換える。
21. 「## リスクと留意点 / Android 固有リスク」セクションのうち Phase 0 で評価しないリスク項目（R8 / ProGuard、Android Auto / TV / Wear OS、Google Play AAB Split APK 影響）の評価責任 Phase を確定する（Phase 0 で評価する 16K page size と JNI 参照テーブル枯渇は既に Phase 0 評価が明示済み）。

### Phase 0 で情報収集する項目（Go/No-Go 判定の参考値）

- `.aar` サイズ delta と起動時間 delta の計測（両アーティファクト併存中の値であり最終形を代表しないため、Phase 4 で再計測する前提）。
- スレッディングモデルの摩擦点（libwebrtc-c の 3 スレッドと Kotlin Coroutine の競合）の実測。
- 既存テスト流用率の実測（ファイル数 / テスト関数数 / 検証ロジック行数のいずれで測るかを Phase 0 着手時に確定）。
- Crashlytics 等での C ブリッジ由来スタックトレースのシンボリック解決確認。

### Go/No-Go 基準

`shiguredo/sora-cpp-sdk` / `shiguredo/sora-flutter-sdk` の libwebrtc-c 移行実績を Phase 0 着手時に調査して数値ターゲットを再校正する。校正前の暫定ターゲット:

- PoC で書き換えた範囲の Kotlin / Java コード行数が **元コードの 2 倍以内** であること。`shiguredo/sora-ios-sdk` の issue 0070 と同じ閾値。
- 「webrtc-rs に追加が必要な API」の [Blocker] のうち、webrtc-rs 側で「追加 NG」と判定された項目が **0 件** であること。
- [Workaroundable] が **3 件以内** で、sora-android-sdk 側の代替戦略が全項目で立つこと。

PoC の期限: 着手から **4 週間以内** に Go/No-Go を判断する。期限内に終わらない項目は「情報収集」へ降格させて記録する。リソース投入計画（人数 × 期間、想定 1〜2 人月）は Phase 0 着手時に `@voluntas` または本 issue 担当者がユーザーと合意のうえ確定する。クリティカルパスは完了基準 #1 → #2 → #3 → #4 → #11/#12 で、これを 3 週間で完遂する必要があり最低 1.5 人月相当。

### Phase 0 PoC 結果テンプレート

Phase 0 PoC 結果は本 issue 末尾に「## Phase 0 PoC 結果」セクションとして追記する。完了基準項目の説明文自体は二重に書かず、項目番号での参照に留める。フォーマット例:

```
## Phase 0 PoC 結果

| 完了基準 # | 実測値 / 判定 / 根拠 | 該当コードスニペット |
|---|---|---|
| #1 | 言語選定: Python 採用。理由: ... | commit XXXXXXX、scripts/fetch_native_deps.py |
| #2 | ビルド成功。libsora_android_sdk.so のサイズ: XX MB。webrtc.jar 同梱クラス: ... | commit XXXXXXX、CMakeLists.txt |
| #4 | CreateOffer 成功。生成 SDP に Opus 含む。検証 assert pass。 | commit XXXXXXX、PoCSmokeTest.kt |
| #11 | パッチ確認結果: ... 実機検証結果: ... | パッチ参照 URL |
| #12 | VP8 Simulcast: encodings.size == 3 を assert pass。 | commit XXXXXXX |
| #13 | CA 共有検証結果: ... | KeyStore 構築コード snippet |
| #14 | 採用案: 方式 B。BuildConfig 生成コード: ... | build.gradle.kts diff |
| ... | ... | ... |
```

各完了基準項目に「実測値・判定・根拠・コードスニペット」を埋める。

### Phase 0 PoC の develop マージ範囲

Go 判定 PR の構成（順序）:

1. Phase 0 PoC 結果セクションを `issues/0061-investigate-rewrite-with-libwebrtc-c.md` 末尾に追記。
2. PoC 使い捨てコードを削除（`PoCSmokeTest.kt` 削除、`build.gradle.kts` の `pixelApi35PoCSmokeTest` task 削除、PoC で書き換えた `RTCComponentFactory.kt` 等の revert）。
3. develop マージ対象のファイル（`gradle/libs.versions.toml` の `compileOnly` 降格、`SDKInfo.libwebrtcInfo()` の reflection フォールバック化、`scripts/native_deps.json` / `scripts/fetch_native_deps.*` / `sora-android-sdk/src/main/cpp/CMakeLists.txt` / `jni_onload.c` の追加、`build.gradle.kts` の `externalNativeBuild` 設定追加）を確定。
4. `CHANGES.md` の `## develop` に `shiguredo-changelog` 規約の `CHANGE → ADD → UPDATE → FIX` 順序で追記（既存の `## develop` セクションは規約順序とずれているが、本 issue では新規追記分のみ規約順序を遵守し、既存ずれの是正は別 issue で扱う）:
   - `[CHANGE] shiguredo-webrtc-android AAR 依存を api から compileOnly に降格する（rewrite 移行に伴う段階的撤去の第一段階）`
   - `[CHANGE] SDKInfo.libwebrtcInfo() を WebrtcBuildVersion の reflection フォールバック実装に書き換える（AAR compileOnly 降格による runtime クラッシュ回避）`
   - `[ADD] PoC 用のビルド構成（scripts/native_deps.json, fetch_native_deps スクリプト, sora-android-sdk/src/main/cpp/CMakeLists.txt, jni_onload.c, externalNativeBuild 設定）を追加する`
5. `issues/0061-investigate-rewrite-with-libwebrtc-c.md` を `issues/closed/` に移動する変更を本 PR 内で git mv する。

PR マージ後の継続作業（PR の commits に含まれない）:

6. Phase 1〜5 を担当する個別 issue を新規起票（SEQUENCE は Phase 0 完了時点の値から順番に消費）。フォールバック規則が発動していた場合は `shiguredo/sora-ios-sdk` リポジトリに通知用 issue を新規起票し、Android 0061 の単独先行内容（採用 libwebrtc バージョン、起票済み webrtc-rs API リスト、major bump 予定タイミング）を共有する。

No-Go 判定時:

- 本ブランチを破棄し `sora-android-sdk/` 配下に変更を残さない。
- 本 issue の解決方法に No-Go の根拠を追記する。
- `CHANGES.md` への追記なし。
- 本 issue は `issues/closed/` に移動する。Phase 1〜5 子 issue 起票はしない。

### Phase 1〜5 完了時の総合条件（サマリ）

最終的に sora-android-sdk として完成形に到達した時点で満たすべき条件のサマリ。詳細な実機検証チェックリストは Phase 5 個別 issue で確定する。

- `gradle/libs.versions.toml` から `shiguredo-webrtc-android` への依存（`compileOnly` 含む）が消え、libwebrtc-c のみに依存している。
- `sora-android-sdk/src/main/kotlin/` および `src/test/` / `src/androidTest/` 配下に残る `import org.webrtc` は、HW encoder ラップ 3 段や `CameraCapturerFactory` など Java 側に残す層に限定され、コア層（`SoraMediaChannel` / `PeerChannel` / `SignalingChannel`）からは消えている。
- 既存サンプルアプリ（`sora-android-sdk-samples`）が新 SDK で接続 / 送受信 / 切断まで通る。
- 主要シナリオの実機検証チェックリスト（Phase 5 個別 issue で確定）がクリアされている。最低限カバーすべき項目: 各 role / カメラ前後切替 / Bluetooth A2DP / SCO 切替 / Background audio / 画面回転 / ネットワーク断 → 再接続 / DataChannel / RPC / 統計情報 / Simulcast / Spotlight / Multistream / Stereo Audio / 各映像コーデック / TURN-UDP/TCP/TLS / WSS の CA / client cert / insecure / HW encoder 機種互換（Xperia 5 II 等含む）/ Android OS バージョン横断 / ハードミュート / ソフトミュート。
- `CHANGES.md` に最終マージ時の変更履歴を追記している（major version bump、「## 破壊変更リスト」を網羅する `[CHANGE]` エントリ、移行ガイドへのリンク）。`[CHANGE]` エントリの追加責任は Phase 5 の最終リリース子 issue が担う（本 issue では候補リスト確定のみ）。
- `sora-android-sdk-samples` の対応 PR がマージ済み。

## 解決方法

### Phase 0: PoC（本 issue の作業範囲）

「## 完了条件 / Phase 0 完了基準」の必須項目 21 項目を着手順序の DAG に沿って実施する。着手順序の概略:

1. 第 1 週: 完了基準 #1〜#4（ビルド構成と最小 JNI 実装）に着手しつつ、#5 / #8 / #18 / #20 を並行で起票・調整開始。#15 / #16 / #19 / #21 は #1〜#4 と独立して並行可能。
2. 第 2 週: #1〜#4 を完了させ、#11 / #12 / #13（技術検証）に着手。#14 も並行。完了基準 #5 の初期合意（API 名と方向性の OK）を取得。
3. 第 3 週: #6 / #7 / #9 / #10（方針確定項目）を確定。残務 #17〜#21 を進める。完了基準 #5 の実装合意（シグネチャ細部）を取得。
4. 第 4 週: Go/No-Go 判定と「Phase 0 PoC 結果」セクションの追記。

PoC で書く実装コードは Phase 1 で再利用しない使い捨てとし、Phase 0 完了時に削除する。Phase 0 で書き換える既存 1 ファイルは `RTCComponentFactory.kt` の create メソッドを第一候補とする。`SimulcastVideoEncoderFactoryWrapper` への連鎖書き換えが必要になった場合、PoC スコープを「使い捨てサンプルとして `androidTest/` 配下に新規追加し、`sora-android-sdk/src/main/` 本体には触らない」に切り替える。

### Phase 0 → Phase 1 の引き継ぎ

PoC で書く実装コードと develop マージ範囲の境界:

- **使い捨て（Phase 0 完了時に削除）**: `PoCSmokeTest.kt`、`build.gradle.kts` の `pixelApi35PoCSmokeTest` task、`RTCComponentFactory.kt` の PoC 改変。
- **develop マージ範囲（永続化）**: `scripts/native_deps.json`, `scripts/fetch_native_deps.*`, `sora-android-sdk/src/main/cpp/CMakeLists.txt`, `jni_onload.c`, `build.gradle.kts` の `externalNativeBuild` 設定、`gradle/libs.versions.toml` の `compileOnly` 降格。

Phase 0 で得た知見（C ブリッジ設計、メモリ管理パターン、callback トランポリン、CA 共有設計）は「## Phase 0 PoC 結果」セクションにコードスニペット付きで記録し、Phase 1 担当者向けの読み方ガイドを併記する（Phase 0 担当者と Phase 1 担当者が異なる場合の引き継ぎ用）。

### Phase 1〜5（Phase 0 完了後に個別 issue として起票）

Phase 0 完了時点で確定する内容を踏まえ、以下の順序で個別 issue を新規起票する。各 issue の分割粒度・カテゴリ・Branch prefix は起票時に確定する。1 issue = 1 branch = 1 PR を厳守、`shiguredo-git` 規約に従う。

- Phase 1: Kotlin ↔ C JNI 共通基盤（ハンドル基盤・トランポリン・Observer Bridge・エラー変換・Logger 連携・メモリ管理ルール）。複数の子 issue（add / refactor）に分割する。**Phase 1 の最初の子 issue で AAR を完全撤去（`compileOnly` も削除）し、`SDKInfo.kt` 等の `org.webrtc.WebrtcBuildVersion` 参照を libwebrtc-c 同梱 `webrtc.jar` 経由（または完了基準 #14 の代替経路）に切り替える作業を同一 PR でアトミックに実施する**。これにより develop は AAR 完全撤去状態でビルド可能を維持する。同子 issue の develop マージ直前に `support/<version>` ブランチを切る（`<version>` は Phase 1 着手時点の直近正式版）。
- Phase 2: 公開 API の型置換（`org.webrtc.MediaStream` → `SoraMediaStream` ほか、「## 互換維持シンボル一覧」と「## 破壊変更リスト」参照）。複数の子 issue（change）に分割する。`sora-android-sdk-samples` の対応 PR を並行起票する。
- Phase 3: コア層の再実装（`SoraMediaChannel` / `PeerChannel` / `SignalingChannel` / `MessageConverter` ほか）。Sora シグナリング 17 メッセージの対応、DataChannel シグナリング切り替え、複数 endpoint failover、`getStats` 定期取得、`onSignalingMessage` 通知。複数の子 issue（add / refactor）に分割する。
- Phase 4: メディア・Android 固有層（カメラキャプチャ、リモート映像描画、HW encoder ラップ 3 段、`JavaAudioDeviceModule`、ハードミュート / ソフトミュート、TLS、DataChannel 圧縮、RPC）。複数の子 issue（add / change）に分割する。Phase 4 の最終 issue で `support/<version>` ブランチへの旧コードの push を完了させる。
- Phase 5: 検証（既存 `SoraE2ETest.kt` の置き換え、`DummyVideoCapturer.kt` 流用、E2E CI 継続、実機検証チェックリスト全項目の合格判定）と doc 系子 issue（ドキュメントリポジトリの改稿、`CHANGES.md` の `[CHANGE]` 列挙確定、`sora-android-sdk-samples` 対応 PR マージ）。

## 互換維持シンボル一覧

### 維持する公開クラス

- `SoraMediaChannel`、`SoraMediaOption`、`SoraAudioOption`、`SoraVideoOption`、`SoraProxyOption`、`SoraSpotlightOption`、`SoraForwardingFilterOption`、`PeerConnectionOption`、`SoraMediaChannel.Listener`、`CameraCapturerFactory`。
- 内部 RTC 層の `RTCComponentFactory` / `PeerChannel` interface / `PeerChannelImpl` / `SignalingChannel` interface / `SignalingChannelImpl` は完了基準 #6 で「公開維持」か「`internal` 化破壊変更」を確定する。

### 維持する公開メソッド・コールバック名

- `SoraMediaChannel`: `connect`（非 suspend）、`disconnect`（非 suspend）、`switchCamera`, `changeCaptureFormat`, `setAudioHardMute`（suspend）、`setAudioSoftMute`, `isAudioRecordingPaused`, `setVideoHardMute`, `setVideoSoftMute`, `getStats`（suspend）、`rpc`（suspend）、`sendDataChannelMessage(label, String)`, `sendDataChannelMessage(label, ByteBuffer)`, `dataToString`（`@Synchronized`）。
- `SoraMediaChannel.Listener`: `onConnect`, `onClose(SoraMediaChannel, SoraCloseEvent)`, `onError`, `onWarning(reason)`, `onWarning(reason, message)`, `onAttendeesCountUpdated`, `onOfferMessage`, `onSignalingMessage`, `onNotificationMessage`, `onPushMessage`, `onPeerConnectionStatsReady`, `onSenderEncodings`, `onDataChannel`, `onDataChannelMessage`, `onAddLocalStream`, `onAddRemoteStream`, `onRemoveRemoteStream(SoraMediaChannel, String label)`。
- `SoraMediaOption`: `enableAudioDownstream`, `enableAudioUpstream`, `enableVideoDownstream`, `enableVideoUpstream`, `enableSimulcast(requestRid: SimulcastRequestRid?)`, `enableSpotlight`。
- `CameraCapturerFactory.create(Context, frontFacingFirst)`。

### 維持する `SoraMediaChannel` コンストラクタ引数（全 21 個中 20 個維持）

`context`, `signalingEndpoint?`, `signalingEndpointCandidates`, `channelId`, `signalingMetadata?`, `mediaOption`, `timeoutSeconds`, `listener?`, `clientId?`, `signalingNotifyMetadata?`, `peerConnectionOption`, `dataChannelSignaling?`, `ignoreDisconnectWebSocket?`, `dataChannels?`, `bundleId?`, `forwardingFiltersOption?`, `insecure`, `caCertificate?`, `clientCertificate?`, `clientPrivateKey?`。残り 1 個 `forwardingFilterOption?`（単数、`@Deprecated`）は破壊変更リスト参照。引数順と型は major bump 時にも維持する。

### 維持する公開フィールド

`SoraMediaOption` の `var` フィールド（型置換が起きるものを除く維持対象、全 19 件中 16 件）: `videoCodec`, `videoBitrate`, `videoVp9Params`, `videoAv1Params`, `videoH264Params`, `videoH265Params`, `audioCodec`, `audioBitrate`, `audioOption`, `role`, `audioStreamingLanguageCode`, `degradationPreference`, `enableCpuOveruseDetection`, `hardwareVideoEncoderResolutionAdjustment`, `proxy`, `softwareVideoEncoderOnly`。残り 3 件（`videoEncoderFactory` / `videoDecoderFactory` / `tcpCandidatePolicy`）は型置換または削除（破壊変更リスト参照）。

`SoraAudioOption` の `var` フィールド（型置換または削除が起きるものを除く維持対象）: `useHardwareAcousticEchoCanceler`, `useHardwareNoiseSuppressor`, `audioProcessingEchoCancellation`, `audioProcessingAutoGainControl`, `audioProcessingHighpassFilter`, `audioProcessingNoiseSuppression`, `audioSource: Int`, `useStereoInput`, `useStereoOutput`, `opusParams: jp.shiguredo.sora.sdk.channel.signaling.message.OpusParams?`, `initialAudioHardMute`。

`SoraProxyOption` の `var` フィールド: `agent`, `hostname`, `port`, `username`, `password`（`type` のみ型置換、それ以外は維持）。

`SoraSpotlightOption`: 引数なしコンストラクタ + `var` フィールド `spotlightNumber`, `spotlightFocusRid`, `spotlightUnfocusRid`（全て外部から設定可能）。

`PeerConnectionOption.getStatsIntervalMSec: Long`。

`SignalingChannelCloseEvent(code: Int, reason: String)`、`SoraCloseEvent(code: Int, reason: String)` および companion (`CLIENT_DISCONNECT_CODE`, `CLIENT_DISCONNECT_REASON`, `createClientDisconnectEvent()`)、`ChannelAttendeesCount(numberOfSendrecvConnections, numberOfSendonlyConnections, numberOfRecvonlyConnections)` + `numberOfConnections` プロパティ。

### 維持する enum / data class

- `SoraVideoOption.Codec`（投稿時点の宣言順 H264 / H265 / VP8 / VP9 / AV1 / DEFAULT、並び順整理は破壊変更リスト参照）、`FrameSize.Landscape` / `Portrait` の各定数（QQVGA / QCIF / HQVGA / QVGA / VGA / qHD / HD / FHD / Res3840x1920 / UHD3840x2160 / UHD4096x2160 系列および Portrait 対応版）、`CaptureType`（DEVICE_CAMERA）、`ResolutionAdjustment`（NONE / MULTIPLE_OF_2 / _4 / _8 / _16）、`SimulcastRid`（R0 / R1 / R2）、`SimulcastRequestRid`（NONE / R0 / R1 / R2）、`SpotlightRid`（NONE / R0 / R1 / R2）、`DegradationPreference`（DISABLED / MAINTAIN_FRAMERATE / MAINTAIN_RESOLUTION / BALANCED、`nativeValue` 公開は破壊変更リスト参照）。
- `SoraAudioOption.Codec`（OPUS / DEFAULT、並び順整理は破壊変更リスト参照）、`jp.shiguredo.sora.sdk.channel.signaling.message.OpusParams`（channels / clockRate / maxplaybackrate / stereo / spropStereo / minptime / useinbandfec / usedtx の 8 フィールド、JSON 名 `channels` / `clock_rate` / `maxplaybackrate` / `stereo` / `sprop_stereo` / `minptime` / `useinbandfec` / `usedtx`）。`SoraAudioOption.opusParams` プロパティで公開。pending/0041 で名前空間移動が提案されており、Phase 2 で吸収可否を判断する。
- `SoraChannelRole`（SENDONLY / RECVONLY / SENDRECV）。
- `SoraErrorReason`（14 case: SIGNALING_FAILURE / ICE_FAILURE / ICE_CLOSED_BY_SERVER / PEER_CONNECTION_FAILED / PEER_CONNECTION_CLOSED / TIMEOUT / ICE_DISCONNECTED / PEER_CONNECTION_DISCONNECTED / AUDIO_TRACK_INIT_ERROR / AUDIO_TRACK_START_ERROR / AUDIO_TRACK_ERROR / AUDIO_RECORD_INIT_ERROR / AUDIO_RECORD_START_ERROR / AUDIO_RECORD_ERROR）、`SoraDisconnectReason`（6 case）、`SoraMessagingError`（7 case）、`SoraRpcErrorReason`（7 case）、`SoraSignalingDirection`（SENT / RECEIVED）、`SoraSignalingTransportType`（WEBSOCKET / DATA_CHANNEL）。
- `SoraMediaOption.SoraCameraConfig` data class（captureType / width / height / frameRate / frontFacingFirst / initialVideoHardMute の 6 フィールド）。
- `SoraForwardingFilterOption` のコンストラクタフィールド（name / priority / action / rules / version / metadata）、`Action`（BLOCK / ALLOW）、`Rule(field, operator, values)`、`Field`（CONNECTION_ID / CLIENT_ID / KIND）、`Operator`（IS_IN / IS_NOT_IN）。

### 型を置換するもの（実態は破壊変更）

| 旧（org.webrtc） | 新（Sora 独自） | 用途 |
|---|---|---|
| `MediaStream` | `SoraMediaStream` | Listener の `onAddLocalStream` / `onAddRemoteStream` |
| `RTCStatsReport` | `SoraStatsReport` | `getStats()` / `onPeerConnectionStatsReady()` |
| `RtpParameters.Encoding` | `SoraSenderEncoding` | `onSenderEncodings()` |
| `RtpParameters.ResolutionRestriction` | `SoraResolutionRestriction` | `Catalog.Encoding.scaleResolutionDownTo`（`OfferMessage.encodings` 経由） |
| `CameraVideoCapturer` | `SoraCameraCapturer` | `CameraCapturerFactory.create()` / `enableVideoUpstream(capturer, ...)` |
| `CameraVideoCapturer.CameraSwitchHandler` | `SoraCameraSwitchHandler` | `switchCamera(handler)` |
| `VideoCapturer` | `SoraVideoCapturer` インタフェース | カスタムキャプチャ用 |
| `AudioDeviceModule` | `SoraAudioDeviceModule` | `SoraAudioOption.audioDeviceModule` |
| `org.webrtc.AudioTrackSink` インタフェース | `SoraAudioTrackSink` インタフェース | `org.webrtc.AudioTrack.addSink` / `removeSink` の引数 |
| `PeerConnection.TcpCandidatePolicy` | `SoraTcpCandidatePolicy` | `SoraMediaOption.tcpCandidatePolicy` |
| `org.webrtc.ProxyType` | `jp.shiguredo.sora.sdk.channel.option.SoraProxyType` | `SoraProxyOption.type`（プロパティ名は維持） |
| `RtpParameters.DegradationPreference` | `SoraVideoOption.DegradationPreference` のみ | 公開 enum のみ残す（`nativeValue` 削除は破壊変更リスト参照） |

### 文字列フォーマット厳格保持

- `SDKInfo.sdkInfo()` 戻り値: `"Sora Android SDK <VERSION> (<REVISION>)"`。
- `SDKInfo.libwebrtcInfo()` 戻り値: `"Shiguredo-build <branch> (<LIBWEBRTC_VERSION> <revision 前 7 文字>)"`。
- `SDKInfo.deviceInfo()` 戻り値: `"Android-SDK: <SDK_INT>, Release: <RELEASE>, Id: <ID>, Device: <DEVICE>, Hardware: <HARDWARE>, Brand: <BRAND>, Manufacturer: <MANUFACTURER>, Model: <MODEL>, Product: <PRODUCT>"`。

これらは connect メッセージの `sora_client` / `libwebrtc` / `environment` フィールドに埋め込まれ、Sora サーバーが期待するフォーマット。

## 破壊変更リスト

major bump で許容する破壊的変更。Phase 0 完了時に暫定確定し、Phase 2 以降の実装で必要に応じて追記する。`CHANGES.md` への `[CHANGE]` エントリ追加は Phase 5 の最終リリース子 issue で行う（本 issue では候補リスト確定のみ）。

### 削除する公開シンボル

- `SoraMediaChannel.Listener.onClose(SoraMediaChannel)`（`@Deprecated`）。
- `SoraMediaChannel.setAudioRecordingPaused`（`@Deprecated`、suspend）。
- `SoraMediaChannel` のコンストラクタ引数 `forwardingFilterOption: SoraForwardingFilterOption?`（単数、`@Deprecated`）。`forwardingFiltersOption` のみ維持。
- `SoraMediaOption.enableMultistream`（`@Deprecated`）。
- `SoraMediaOption.enableLegacyStream`（`@Deprecated`）。
- `SoraMediaOption.enableSimulcast(rid: SimulcastRid?)`（`@Deprecated`）。`enableSimulcast(requestRid: SimulcastRequestRid? = null)` のみ維持。
- `SoraMediaOption.videoEncoderFactory: VideoEncoderFactory?` / `videoDecoderFactory: VideoDecoderFactory?`（内部化、`softwareVideoEncoderOnly` フラグは維持）。
- `SoraMediaOption.enableVideoDownstream(EglBase.Context?)` の引数（`enableVideoDownstream()` に変更）。
- `SoraMediaOption.enableVideoUpstream(VideoCapturer, EglBase.Context?, SoraCameraConfig?)` および `enableVideoUpstream(EglBase.Context?, SoraCameraConfig)` の `EglBase.Context?` 引数（SDK 内部で自動取得）。
- `SoraAudioOption.mediaConstraints: MediaConstraints?`（現状は個別フラグの上位エスケープハッチ。rewrite では削除し、必要な制約は個別フラグ `audioProcessing*` 4 件への移行を促す。companion 定数 `ECHO_CANCELLATION_CONSTRAINT` / `AUTO_GAIN_CONTROL_CONSTRAINT` / `HIGH_PASS_FILTER_CONSTRAINT` / `NOISE_SUPPRESSION_CONSTRAINT` も連動して削除）。
- `SoraVideoOption.DegradationPreference.nativeValue: RtpParameters.DegradationPreference`。
- `MessageConverter.parseUpdateMessage` / `UpdateMessage` data class / `buildUpdateAnswerMessage`（Sora 2022.1.0 で廃止された `update` 型）。`MessageConverter` クラス自体の `internal` 化を Phase 3 で検討（`gson: Gson` / `TAG` 公開 companion フィールドも合わせて隠蔽候補）。
- ABI: armeabi-v7a / x86_64 サポートを撤去し arm64-v8a 単独に縮小。

### 型置換による破壊変更

「## 互換維持シンボル一覧 / 型を置換するもの」表の各行はすべて、シグネチャの公開型を `org.webrtc.*` から Sora 独自型に変える破壊変更。

### enum 並び順の整理（候補）

- `SoraVideoOption.Codec` の宣言順整理（`SoraVideoOption.kt:10` の TODO 反映）。
- `SoraAudioOption.Codec` の宣言順整理（DEFAULT を先頭、`SoraAudioOption.kt:19` の TODO 反映）。

両方とも Kotlin enum の `ordinal()` 経由で値が変わるため、シリアライズに `ordinal()` を使っていないことを Phase 2 で確認したうえで実施する。確認手段: `grep -rn 'ordinal()' sora-android-sdk/src/main` + Gson / Parcelable / 内部 mod 演算等の経路を網羅レビュー。

### ビルド要件変更

- minSdk 21 → 24 / 26 / 29（完了基準 #9 で確定）。
- AAR 配布から libwebrtc-c.a 静的リンクへの切り替え（JitPack 単独運用の困難性に伴う配布方式変更、具体策は issue 0050 で扱う）。

## ブランチ・リリース戦略

### 既存コードの退避

- `support/<version>` ブランチは Phase 1 の最初の子 issue を develop にマージする直前のタイミングで切る。`<version>` は Phase 1 着手時点の直近正式版（投稿時点では `2026.1.0` または `2026.2.0` が見込まれる）。
- `support/<version>` ブランチではセキュリティ修正と致命的 bug の対応のみを継続する。新機能追加は行わない。保守期間は本 issue 内では「rewrite 完了版がリリースされてから 1 年間」を暫定とし、Phase 0 完了時にユーザー影響を踏まえて再確定する。
- support ブランチでの patch リリース可否（tag 打つか）、CHANGES.md の `## <version>.Y` を support ブランチでだけ追加するか develop にもバックポートするか、support と develop の cherry-pick 戦略は Phase 1 着手前に確定する（Phase 0 担当者と Phase 1 担当者が異なる場合は引き継ぎ事項として明記）。

### バージョン番号体系

- SDK バージョンの主系列（`YYYY.M.PATCH`）は維持する。
- rewrite 完了版は major bump として `CHANGES.md` に `[CHANGE]` で記載し、「## 破壊変更リスト」を網羅的に列挙する。major bump 番号は Phase 5 完了時の暦年に依存するため事前確定せず、Phase 5 完了時に決定する。
- `gradle/libs.versions.toml` の `libwebrtc` の供給ソース移管に伴う `canary.py` 側の変更要否は完了基準 #14 で確定する。

### 配布方式

JitPack 単独運用は `libwebrtc-c.a` を含む native build には不向き（`jitpack.yml` の `jdk: openjdk17` のみの構成では NDK / CMake / Chromium clang を含むビルド環境を再現できないため）。配布方式の具体策は本 issue とは別に `issues/0050-investigate-release-aar-upload.md` で扱う。本 issue は「JitPack 単独運用は困難、別経路を要検討」のレベルで方針を示すにとどめ、0050 側で詳細を確定する。本 issue の Go 判定は 0050 の決定と独立する。

### CHANGES.md エントリの順序

`/Users/voluntas/shiguredo/sora-android-sdk/CHANGES.md` の現状 `## develop` セクションは `CHANGE → UPDATE → ADD → FIX` の順で、`shiguredo-changelog` 規約（`CHANGE → ADD → UPDATE → FIX`）と不整合がある。本 issue の Go 判定 PR で `## develop` に新規追記する際は `shiguredo-changelog` 規約に従う順序で追記する（既存のずれ自体の是正は本 issue のスコープ外、別 issue で扱う）。

## 既存 issue との関係

本 issue 着手時点で `issues/` 直下 26 件、`issues/pending/` 17 件、`issues/closed/` 16 件が存在する。各 issue の本 rewrite との関係（rewrite で吸収 / 並行可能 / Phase 0 で再評価）の最終確定は完了基準 #20 で行う。

投稿時点での暫定分類（最終確定は完了基準 #20）:

- **rewrite で同時に解決される候補（9 件）**: `0016` (track-based 受信通知で自然解決) / `0019` (RtpParameters 再設計で adaptivePtime 追加) / `0020` (ログ機構再実装で onIceCandidateError 追加) / `0024` (onTrack ベースで deprecated onAddStream 自然解決) / `0026` (TLS 層再設計で PEM 型統一) / `0030` (エラーハンドリング再設計でネットワーク切断詳細吸収) / `0035` (RtpParameters 再設計で networkPriority 追加) / `0049` (デフォルト値整理で signalingMetadata 吸収) / `0051` (コールバック設計時に onDataChannel 発火タイミング整理)。
- **独立対応候補（11 件）**: `0018` (kotlin-reflect 削除) / `0021` (docs) / `0023` (docs) / `0025` (docs) / `0029` (dummy capturer サンプル、rewrite で API 変更時は再実装が必要になる可能性あり) / `0032` (samples 統合検討) / `0034` (Android 17 対応、minSdk 引き上げと連動可能性) / `0044` (SoraLogger 改善) / `0050` (配布方式議論、本 issue と並行) / `0057` (README HWA) / `0059` (androidTest dummy audio)。
- **要確認（6 件）**: `0015` (USB カメラ、Phase 4 カスタムキャプチャ再評価) / `0017` (video orientation GPU 回転、Phase 4 フレームパイプライン再設計時に再評価) / `0022` (ステレオ受信、Phase 5 検証) / `0028` (simulcast 長時間、Phase 5 検証) / `0031` (Bluetooth 入力、Phase 4 AudioDeviceModule 再設計時に再評価) / `0033` (MediaStream.id、Phase 2 SoraMediaStream 設計時に再評価)。
- **pending（17 件）の取り扱い**: `0027` (TURN-TLS client cert、Phase 4 着手時に pending から復帰) / `0038` (ADM low latency、webrtc-rs 追加要請に統合) / `0041` (OpusParams 名前空間移動、Phase 2 で吸収可否判断) / `0046` (SoraMediaOption.role 切り戻し、Phase 2 で同時判断、pending → active 化は Phase 2 着手時) / `0047` (libwebrtc update 自動化、`scripts/native_deps.json` 化で前提変化、Phase 0 完了後に再起票検討) / `0048` (simulcast multicodec crash、Priority High だが構造的解消の見込みがあるため Phase 5 で再現確認) / `0056` (AAudio 移行、API 26 必要、完了基準 #9 で minSdk 26 採用可否と同時判断)。残り 10 件（`0036` / `0037` / `0039` / `0040` / `0042` / `0043` / `0045` / `0053` / `0054` / `0055`）は Phase 4 メディア層 / Phase 5 検証時に rewrite との関連を完了基準 #20 で再評価する。

## iOS SDK との連動

`shiguredo/sora-ios-sdk` の issue 0070（タイトル: WebRTC.xcframework から libwebrtc_c.xcframework (webrtc_c) への完全移行、ファイル名: `0070-change-migrate-to-webrtc-c-xcframework.md`）と Phase 構成・追加要請・リリース戦略を共有する。両 SDK で同期する事項:

- 両 SDK の Phase 0 進捗の同期方針: 「## 本 issue の性質と進行方針 / Phase 0 着手前のフォールバック規則」参照。
- libwebrtc バージョン整合（m148 維持 / m150 系列統一）: 完了基準 #8 で確定。
- webrtc-rs 起票責任の分担: 完了基準 #5 で確定。Phase 0 着手時に iOS SDK 0070 担当者と合意したうえで、本 issue にステータス列を埋める。
- 同時 major bump の意思決定タイミング: 本 issue 内では決定保留とし、Phase 0 完了時に確定する。フォールバック規則該当時は片方先行で major bump リリース。
- iOS SDK 0070 への本 issue 参照追加: 本 issue 着手前に依頼する。応答が無い場合のフォールバックは「## Phase 0 着手前のフォールバック規則」参照。

## リスクと留意点

### libwebrtc-c 移行に共通するリスク（iOS SDK 0070 と共通）

- メモリ安全性の低下: Kotlin ⇔ C 境界で `Long` ポインタ・`@Volatile` フラグ・global ref を多用する。use-after-free / 参照カウント漏れ / observer 循環参照によるクラッシュを Phase 0 で実測する。対応: 完了基準 #4 PoC テスト。
- クラッシュレポートの可読性低下: スタックトレースが C 関数名と libwebrtc 内部 C++ シンボル中心になる。対応: Phase 0 情報収集項目。
- libwebrtc-c の若さ: 未対応 API や未発見バグの可能性あり。対応: 完了基準 #5。
- libwebrtc-c の API スタビリティ: 対応: Phase 0 で `shiguredo/webrtc-rs` の `RULES.md` 確認。
- 大規模 PR / 長寿命ブランチ: Phase ごとに小さい PR で `develop` へ小まめにマージする運用で対処。
- 外部リポジトリへの波及: `shiguredo/webrtc-rs` への API 追加 PR、`sora-android-sdk-samples` への対応 PR、ドキュメントリポジトリ更新。対応: 完了基準 #5 / #15。
- `.aar` サイズ・起動時間: 対応: Phase 0 情報収集項目、Phase 4 で本格計測。
- NOTICE / LICENSE: 対応: 完了基準 #16。

### Android 固有リスク

- **Android 16K page size 対応**: Google Play は 16K page size を 2025 年から段階的に必須化。`libwebrtc_c.a` が 16K page size に対応していないと Google Play 配信不可。対応: Phase 0 で `readelf -l libwebrtc_c.a | grep MAXPAGESIZE` 相当のコマンドで確認、未対応の場合は webrtc-rs 側への再ビルド要請を Go 阻害要因に格上げ。
- **JNI ローカル参照テーブル枯渇**: 高頻度 JNI コールバック経路で参照リーク発生時に `JNI ERROR (app bug): local reference table overflow`。対応: 完了基準 #4 PoC テストで `DeleteLocalRef` の運用パターンを実測。
- **R8 / ProGuard の `webrtc.jar` 内部クラス難読化**: keep 漏れで release ビルドだけクラッシュする経路。対応: 完了基準 #17 で `consumer-rules.pro` 内容を確定、評価は Phase 1〜2（責任 Phase は完了基準 #21 で確定）。
- **Android Auto / TV / Wear OS**: arm64-v8a 単独で影響範囲が変化。対応: 完了基準 #7 でユーザー影響を評価、責任 Phase は完了基準 #21 で確定。
- **Google Play AAB Split APK 影響**: arm64 単独で配布時の Split APK 経路が変わる。対応: 責任 Phase は完了基準 #21 で確定。
- **機種互換性の低下リスク**: HW encoder の機種互換、Camera2 / Camera1 互換、Bluetooth SCO。対応: 完了基準 #11〜#13 と「Phase 1〜5 完了時の総合条件 サマリ内の主要シナリオ実機検証チェックリスト」でカバー。
- **JitPack 廃止に伴う既存ユーザーへの影響**: 別 issue `0050` で扱うが、本 issue の major bump タイミングと整合させる必要がある。

## 参考リソース

- `shiguredo/sora-flutter-sdk` リポジトリの Android 統合実装
  - `scripts/native_deps.json` および `scripts/fetch_native_deps.dart`（fetch スクリプト参照実装）
  - `android/build.gradle`（`minSdkVersion` / `ndkVersion` の前例、差分検証材料）
  - `android/src/main/cpp/`（C ブリッジ参照実装）
  - `android/consumer-rules.pro`（ProGuard 雛形）
- `shiguredo/webrtc-rs` リポジトリ
  - `RULES.md`（C ラッパー作成ルール、命名規則、API stability 方針）
  - `webrtc/src/` 配下の C ヘッダ群
  - `webrtc/CMakeLists.txt`
  - `Cargo.toml`
- libwebrtc 本体（`webrtc-checkout` の `src/sdk/android/`、`webrtc.jar` および JNI 経由の利用パターン参照）
- `shiguredo/sora-cpp-sdk` リポジトリ（libwebrtc-c 利用の C++ 側実例）
- `shiguredo/sora-ios-sdk` の issue 0070
- 既存 sora-android-sdk: `sora-android-sdk/src/main/kotlin/` 配下全ファイル
- Sora ドキュメント本体（シグナリング / DataChannel / メッセージング / 通知 / 転送フィルター / RPC 仕様。Phase 0 で参照 URL を本 issue に追記する）
