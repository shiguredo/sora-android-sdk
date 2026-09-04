---
name: sora-android-sdk
description: 時雨堂の WebRTC SFU Sora 向け Android クライアント SDK (sora-android-sdk) の機能・API リファレンス。SoraMediaChannel / SoraMediaOption / SoraAudioOption / SoraVideoOption による接続管理、音声・映像の送受信、ソフトミュート / ハードミュート、カメラ制御、DataChannel メッセージング、JSON-RPC 2.0 over DataChannel、WebRTC 統計情報取得、ステレオ音声の送受信、TLS / mTLS / TURN-TLS / プロキシ設定、サイマルキャスト / スポットライト / 転送フィルター、ビルド・テスト手順に関する質問時に使用。
---

# Sora Android SDK (sora-android-sdk)

- **バージョン**: [SDKInfo.kt](sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/util/SDKInfo.kt) の `VERSION` を参照 (develop は 2026.3.0-canary.0)
- **リポジトリ**: https://github.com/shiguredo/sora-android-sdk
- **ドキュメント**: https://sora-android-sdk.shiguredo.jp/
- **サンプル集**: https://github.com/shiguredo/sora-android-sdk-samples
- **クイックスタート**: https://github.com/shiguredo/sora-android-sdk-quickstart

[WebRTC SFU Sora](https://sora.shiguredo.jp) の Android クライアントアプリケーションを開発するためのライブラリ。言語は Kotlin で、Java からも利用できる (`@JvmOverloads` 等で Java 呼び出しを考慮している)。

## 動作条件

- Android 5 以降 (minSdk 21)。エミュレーターでの動作は保証しない
- Android Studio 2025.3.1 以降
- WebRTC SFU Sora 2025.2.0 以降

## 依存ライブラリ

- `com.github.shiguredo:shiguredo-webrtc-android` (libwebrtc 150.7871.3.0) — `api` 依存なので利用側の `compileClasspath` に `org.webrtc.*` が公開される
- Gson (シグナリング JSON)
- OkHttp (WebSocket)
- kotlinx.coroutines
- RxJava 2 系 (内部)

## インストール

JitPack から配布されている。

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.shiguredo:sora-android-sdk:<Tag>'
}
```

## 基本的な使い方

### 接続の流れ

1. `SoraMediaOption` で映像・音声の有効化と各種オプションを設定する
2. `SoraMediaChannel` を生成する
3. `Listener` を実装してコールバックを登録する
4. `connect()` で接続する
5. `Listener.onConnect` で接続確立を検知する
6. `disconnect()` で切断する

### Kotlin での接続例

```kotlin
val mediaOption = SoraMediaOption().apply {
    // 映像の視聴 (eglContext は null も可)
    enableVideoDownstream(eglContext)
    // 映像の配信 (カメラキャプチャは SDK 内部生成モード)
    enableVideoUpstream(
        eglContext,
        SoraMediaOption.SoraCameraConfig(
            width = 640,
            height = 480,
            frameRate = 30,
        ),
    )
    // 音声の視聴・配信
    enableAudioDownstream()
    enableAudioUpstream()
}

val mediaChannel =
    SoraMediaChannel(
        context = context,
        signalingEndpoint = "wss://sora.example.com/signaling",
        channelId = "sora",
        mediaOption = mediaOption,
        listener = object : SoraMediaChannel.Listener {
            override fun onConnect(mediaChannel: SoraMediaChannel) {
                // 接続確立
            }

            override fun onClose(
                mediaChannel: SoraMediaChannel,
                closeEvent: SoraCloseEvent,
            ) {
                // 切断
            }

            override fun onError(
                mediaChannel: SoraMediaChannel,
                reason: SoraErrorReason,
                message: String,
            ) {
                // エラー
            }
        },
    )

mediaChannel.connect()

// 切断
mediaChannel.disconnect()
```

`Listener` のメソッドはすべてデフォルト実装 (空) を持つため、必要なものだけオーバーライドすればよい。

## API リファレンス

### SoraMediaChannel

#### コンストラクタ

```kotlin
SoraMediaChannel(
    context: Context,
    signalingEndpoint: String? = null,
    signalingEndpointCandidates: List<String> = emptyList(),
    channelId: String,
    signalingMetadata: Any? = null,
    mediaOption: SoraMediaOption,
    timeoutSeconds: Long = 10L,
    listener: Listener?,
    clientId: String? = null,
    signalingNotifyMetadata: Any? = null,
    peerConnectionOption: PeerConnectionOption = PeerConnectionOption(),
    dataChannelSignaling: Boolean? = null,
    ignoreDisconnectWebSocket: Boolean? = null,
    dataChannels: List<Map<String, Any>>? = null,
    bundleId: String? = null,
    forwardingFilterOption: SoraForwardingFilterOption? = null, // 非推奨
    forwardingFiltersOption: List<SoraForwardingFilterOption>? = null,
    insecure: Boolean = false,
    caCertificate: String? = null,
    clientCertificate: String? = null,
    clientPrivateKey: String? = null,
)
```

- `signalingEndpoint` と `signalingEndpointCandidates` はどちらか一方を利用する (クラスター機能は Candidates 側)
- `signalingMetadata` / `signalingNotifyMetadata` は未指定 (`null`) と `JsonNull` の場合は connect メッセージに含めない。空文字は明示指定として送信される
- `dataChannels` はメッセージング用 DataChannel の定義 (label は `#` で始まる)
- 証明書はすべて PEM 文字列。`caCertificate` はちょうど 1 個の証明書のみ、`clientCertificate` はチェーン可、`clientPrivateKey` は PKCS#8

#### 主なメソッド

| メソッド | 説明 |
|---|---|
| `connect()` | Sora へ接続する |
| `disconnect()` | 接続を切断する。`onClose` は `SoraCloseEvent(code: 1000, reason: "NO-ERROR")` で通知される |
| `suspend fun getStats(): RTCStatsReport?` | W3C 準拠の統計情報を非同期で取得。未接続や失敗時は `null` |
| `fun setAudioSoftMute(muted: Boolean): Boolean` | 音声ソフトミュート (デジタルサイレンス送出) |
| `suspend fun setAudioHardMute(muted: Boolean): Boolean` | 音声ハードミュート (録音停止) |
| `fun isAudioRecordingPaused(): Boolean` | 録音停止中かどうか |
| `fun setVideoSoftMute(muted: Boolean): Boolean` | 映像ソフトミュート (黒塗り送出) |
| `fun setVideoHardMute(muted: Boolean): Boolean` | 映像ハードミュート (キャプチャ停止 + ソフトミュート併用)。`SoraCameraConfig` 指定 (SDK 内部生成のカメラ) が必要 |
| `fun switchCamera(handler: CameraVideoCapturer.CameraSwitchHandler?)` | フロント / リアカメラ切り替え |
| `fun changeCaptureFormat(width: Int, height: Int, frameRate: Int)` | キャプチャフォーマット変更 |
| `suspend fun rpc(method: String, paramsJson: String?, isNotificationRequest: Boolean = false, timeoutMillis: Long = 5000L): SoraRpcResult?` | JSON-RPC 2.0 呼び出し (`SoraRpcException` を投げる) |
| `fun sendDataChannelMessage(label: String, data: String): SoraMessagingError` | メッセージ送信 (文字列) |
| `fun sendDataChannelMessage(label: String, data: ByteBuffer): SoraMessagingError` | メッセージ送信 (バイナリ) |

#### プロパティ

- `var connectionId: String?` — Sora が払い出した接続 ID (offer 受信時に設定)
- `var contactSignalingEndpoint: String?` — 最初に type: connect を送信したエンドポイント (redirect 後も元のまま)
- `var connectedSignalingEndpoint: String?` — 接続中のエンドポイント (redirect 後はリダイレクト先)

#### 非推奨 API

| 非推奨 | 代替 |
|---|---|
| `suspend fun setAudioRecordingPaused(paused: Boolean)` | `setAudioHardMute(muted)` |
| コンストラクタ `forwardingFilterOption` | `forwardingFiltersOption` |
| `mediaOption.enableSimulcast(rid: SimulcastRid?)` | `mediaOption.enableSimulcast(requestRid: SimulcastRequestRid?)` |
| `mediaOption.enableMultistream()` / `enableLegacyStream()` | レガシーストリーム機能は Sora 2025.6 で廃止 |
| `Listener.onClose(mediaChannel)` | `Listener.onClose(mediaChannel, closeEvent)` |

### Listener コールバック

| コールバック | 発火タイミング |
|---|---|
| `onAddLocalStream(mediaChannel, ms)` | ローカルストリーム追加時 |
| `onAddRemoteStream(mediaChannel, ms)` | リモートストリーム追加時 |
| `onRemoveRemoteStream(mediaChannel, label)` | リモートストリーム削除時 |
| `onConnect(mediaChannel)` | Sora との接続確立時 |
| `onClose(mediaChannel, closeEvent)` | 切断時 (正常・異常どちらも) |
| `onError(mediaChannel, reason, message)` | エラー時 (`message` は空文字のことがある) |
| `onWarning(mediaChannel, reason)` / `onWarning(mediaChannel, reason, message)` | 警告時 |
| `onAttendeesCountUpdated(mediaChannel, attendees)` | チャネル参加者数の増減時 |
| `onOfferMessage(mediaChannel, offer)` | Sora から type: offer 受信時 |
| `onSignalingMessage(mediaChannel, direction, transportType, rawMessage)` | シグナリングメッセージ送受信時 (sora-js-sdk と通知対象を合わせている) |
| `onNotificationMessage(mediaChannel, notification)` | シグナリング通知受信時 |
| `onPushMessage(mediaChannel, push)` | プッシュ API メッセージ受信時 |
| `onPeerConnectionStatsReady(mediaChannel, statsReport)` | `getStatsIntervalMSec` による定期統計受信時 (0 なら発火しない) |
| `onSenderEncodings(mediaChannel, encodings)` | サイマルキャストの encoder 設定変更時 |
| `onDataChannel(mediaChannel, dataChannels)` | 全メッセージング用 DataChannel (`#` ラベル) が OPEN になった時点で一度だけ |
| `onDataChannelOpened(mediaChannel, label)` | 受け取ったすべての DataChannel がラベルごとに OPEN になった時点で一度だけ |
| `onDataChannelMessage(mediaChannel, label, data)` | `#` ラベルのメッセージ受信時 |
| `onAddRemoteTrack(mediaChannel, track, streamId)` | リモートトラック追加時 |
| `onRemoveRemoteTrack(mediaChannel, trackId, streamId)` | リモートトラック削除時 |

### SoraMediaOption

| メンバー | 説明 |
|---|---|
| `enableVideoDownstream(eglContext)` | 映像視聴を有効化 |
| `enableVideoUpstream(capturer, eglContext, cameraConfig)` | 映像配信を有効化 (ユーザー提供の `VideoCapturer`) |
| `enableVideoUpstream(eglContext, cameraConfig)` | 映像配信を有効化 (カメラキャプチャを SDK 内部生成) |
| `enableAudioDownstream()` | 音声視聴を有効化 |
| `enableAudioUpstream()` | 音声配信を有効化 |
| `enableSimulcast(requestRid: SimulcastRequestRid? = null)` | サイマルキャスト有効化 |
| `enableSpotlight(option, enableSimulcast = true)` | スポットライト有効化 (マルチストリームも有効化される) |
| `role: SoraChannelRole?` | 明示指定。未指定なら送受信設定から自動決定 (`requiredRole`) |
| `videoCodec` / `audioCodec` | コーデック (`DEFAULT` は Sora のデフォルト値) |
| `videoBitrate` / `audioBitrate` | ビットレート |
| `videoVp9Params` / `videoAv1Params` / `videoH264Params` / `videoH265Params` | コーデックパラメーター |
| `audioOption` | 音声オプション (`SoraAudioOption`) |
| `videoEncoderFactory` / `videoDecoderFactory` | カスタム factory |
| `softwareVideoEncoderOnly` | ソフトウェアエンコーダーのみ使用 |
| `degradationPreference` | `MAINTAIN_FRAMERATE` / `MAINTAIN_RESOLUTION` / `BALANCED` / `DISABLED` |
| `hardwareVideoEncoderResolutionAdjustment` | HW エンコーダー入力解像度の倍数調整 (`MULTIPLE_OF_16` が既定) |
| `enableCpuOveruseDetection` | `googCpuOveruseDetection` 相当 |
| `tcpCandidatePolicy` | `ENABLED` / `DISABLED` |
| `proxy` | プロキシ設定 (`SoraProxyOption`) |
| `audioStreamingLanguageCode` | Sora の音声ストリーミング機能の言語コード |

`SoraMediaOption.SoraCameraConfig(captureType, width, height, frameRate, frontFacingFirst, initialVideoHardMute)` のデフォルトは 640x480 / 30fps / フロント優先 / ハードミュートなし。

### SoraAudioOption

| メンバー | 既定値 | 説明 |
|---|---|---|
| `audioSource` | `VOICE_COMMUNICATION` | `MediaRecorder.AudioSource` |
| `useStereoInput` | `false` | ステレオ入力 (送信) |
| `useStereoOutput` | `false` | ステレオ出力 (受信) |
| `audioAttributes` | `null` | 出力用 `AudioAttributes`。`useStereoOutput` と併用する (既定の `USAGE_VOICE_COMMUNICATION` + `CONTENT_TYPE_SPEECH` ではステレオがモノラルへダウンミックスされる場合がある) |
| `audioDeviceModule` | `null` | カスタム ADM。非 null の場合、SDK 内部で ADM を生成しないため `audioSource` / `useStereoInput` / `useStereoOutput` / `audioAttributes` / `useHardware*` / `initialAudioHardMute` は無視される |
| `useHardwareAcousticEchoCanceler` / `useHardwareNoiseSuppressor` | `true` | 端末組み込み AEC / NS |
| `audioProcessingEchoCancellation` / `audioProcessingAutoGainControl` / `audioProcessingHighpassFilter` / `audioProcessingNoiseSuppression` | `true` | MediaConstraints 経由の音声処理 |
| `mediaConstraints` | `null` | 非 null の場合 `audioProcessing*` は無視される |
| `opusParams` | `null` | `OpusParams` |
| `initialAudioHardMute` | `false` | 接続時に音声ハードミュート |

### SoraVideoOption

- `Codec`: `H264` / `H265` / `VP8` / `VP9` / `AV1` / `DEFAULT`
- `CaptureType`: `DEVICE_CAMERA`
- `FrameSize.Landscape` / `FrameSize.Portrait`: `QQVGA` / `QCIF` / `HQVGA` / `QVGA` / `VGA` / `qHD` / `HD` / `FHD` / `UHD*` など
- `SimulcastRid`: `R0` / `R1` / `R2` (接続時の simulcast_rid、非推奨の経路)
- `SimulcastRequestRid`: `NONE` / `R0` / `R1` / `R2` (simulcast_request_rid)
- `SpotlightRid`: `NONE` / `R0` / `R1` / `R2`
- `ResolutionAdjustment`: `NONE` / `MULTIPLE_OF_2` / `MULTIPLE_OF_4` / `MULTIPLE_OF_8` / `MULTIPLE_OF_16`
- `DegradationPreference`: `DISABLED` / `MAINTAIN_FRAMERATE` / `MAINTAIN_RESOLUTION` / `BALANCED`

H.264 / H.265 はハードウェアデコーダー / エンコーダー対応、VP9 / AV1 は対応端末でハードウェアを利用可能。

### その他のオプション

- `SoraSpotlightOption`: `spotlightNumber` / `spotlightFocusRid` / `spotlightUnfocusRid`
- `SoraForwardingFilterOption`: `name` / `priority` / `action` (`BLOCK` / `ALLOW`) / `rules` (`Field`: `CONNECTION_ID` / `CLIENT_ID` / `KIND`, `Operator`: `IS_IN` / `IS_NOT_IN`) / `version` / `metadata`
- `SoraProxyOption`: `type` (`ProxyType`) / `agent` / `hostname` / `port` / `username` / `password`
- `PeerConnectionOption.getStatsIntervalMSec`: 定期統計取得の間隔 (0 なら無効)

## DataChannel メッセージング

- connect 時に `dataChannels` でラベルを定義する。**label は `#` で始まる必要がある**
- DataChannel シグナリングへ切り替わった後 (`onDataChannel` 発火後) に送信できる
- 送信: `sendDataChannelMessage(label, data)`。エラーは `SoraMessagingError` で返る (`NOT_READY` / `INVALID_LABEL` / `LABEL_NOT_FOUND` / `INVALID_STATE` / `SEND_FAILED` / `PEER_CHANNEL_UNAVAILABLE`)
- 受信: `onDataChannelMessage(mediaChannel, label, data)`

```kotlin
// 接続時に定義
val mediaChannel =
    SoraMediaChannel(
        context = context,
        signalingEndpoint = "...",
        channelId = "sora",
        mediaOption = mediaOption,
        dataChannels =
            listOf(
                mapOf(
                    "label" to "#example",
                    "direction" to "sendrecv",
                    "compress" to true,
                ),
            ),
        listener = listener,
    )

// 送信 (switched 後)
mediaChannel.sendDataChannelMessage("#example", "hello")

// 受信
override fun onDataChannelMessage(
    mediaChannel: SoraMediaChannel,
    label: String,
    data: ByteBuffer,
) {
    // data は UTF-8 デコードして利用する
}
```

`compress: true` の場合、圧縮 / 展開は SDK が自動で行う (`ZipHelper`)。

## RPC (JSON-RPC 2.0 over DataChannel)

Sora が RPC 対応 (`offer` の `rpc_methods` にメソッドが含まれる) の場合に利用できる。

```kotlin
try {
    when (val result = mediaChannel.rpc("2025.2.0/RequestSimulcastRid", """{"receiver_connection_id": "...", "rid": "r0"}""")) {
        is SoraRpcResult.Success -> {
            // result.result (JSON 文字列)
        }
        is SoraRpcResult.Error -> {
            // result.error.code / result.error.message
        }
        null -> {
            // notification リクエスト
        }
    }
} catch (e: SoraRpcException) {
    // SoraRpcErrorReason: NOT_AVAILABLE / DATA_CHANNEL_UNAVAILABLE / DATA_CHANNEL_CLOSED / PEER_UNAVAILABLE / SEND_FAILED / TIMEOUT / PARSE_ERROR
}
```

- id 採番と JSON-RPC のエンベロープは SDK が担当する (利用者は id を指定しない)
- `isNotificationRequest = true` ならレスポンスを待たず `null` が返る
- タイムアウトは `timeoutMillis` (デフォルト 5 秒)

## WebRTC 統計情報

```kotlin
// 1 回だけ取得
val report = mediaChannel.getStats()

// 定期取得 (間隔ミリ秒。0 で無効)
val peerConnectionOption = PeerConnectionOption().apply {
    getStatsIntervalMSec = 5000L
}
```

定期取得の結果は `Listener.onPeerConnectionStatsReady` で通知される。

## ステレオ音声の送受信

- 送信: `SoraAudioOption.useStereoInput = true` で AudioRecord を 2ch にしてステレオ送信
- 受信: `SoraAudioOption.useStereoOutput = true` + `audioAttributes` 指定 (例: `USAGE_MEDIA` + `CONTENT_TYPE_MUSIC`)
  - `useStereoOutput = true` の場合、answer SDP の Opus fmtp に `stereo=1;sprop-stereo=1` を自動追記する
  - **この SDP 書き換えはクライアント側が answer を組み立てる経路 (Sora が offer を生成する構成) でのみ機能する**。通常のクライアント offer 経路 (Sora が answer を生成) では SDK 側の書き換えは行われないため、Sora サーバ側の設定に依存する

## エラー・イベント

### SoraErrorReason

`SIGNALING_FAILURE` / `ICE_FAILURE` / `ICE_CLOSED_BY_SERVER` / `PEER_CONNECTION_FAILED` / `PEER_CONNECTION_CLOSED` / `TIMEOUT` / `ICE_DISCONNECTED` / `PEER_CONNECTION_DISCONNECTED` / `AUDIO_TRACK_INIT_ERROR` / `AUDIO_TRACK_START_ERROR` / `AUDIO_TRACK_ERROR` / `AUDIO_RECORD_INIT_ERROR` / `AUDIO_RECORD_START_ERROR` / `AUDIO_RECORD_ERROR`

### SoraDisconnectReason (SDK 内部で type: disconnect に含める reason)

`NO_ERROR` / `WEBSOCKET_ONCLOSE` / `WEBSOCKET_ONERROR` / `DATACHANNEL_ONCLOSE` / `PEER_CONNECTION_STATE_FAILED` / `SIGNALING_FAILURE`

### SoraCloseEvent

`code: Int` / `reason: String`。クライアントからの切断時は `code: 1000, reason: "NO-ERROR"`。

## 開発者向け情報

### リポジトリ構成

- `sora-android-sdk/src/main/kotlin/jp/shiguredo/sora/sdk/` — SDK 本体
  - `channel/SoraMediaChannel.kt` — 公開 API の中心
  - `channel/option/` — オプションクラス
  - `channel/rtc/` — PeerConnection / ADM / コーデック制御
  - `channel/signaling/` — WebSocket / DataChannel シグナリング
  - `channel/rpc/` — JSON-RPC 2.0
  - `camera/` / `codec/` — カメラキャプチャ / 映像コーデック
  - `error/` / `util/` — エラー enum / ユーティリティ
- `sora-android-sdk/src/test/` — JUnit (Robolectric) 単体テスト
- `sora-android-sdk/src/androidTest/` — Sora 実サーバを使う e2e テスト
- `issues/` — issue 管理 (markdown、`shiguredo-issues` スキル参照)
- `CHANGES.md` — 変更履歴 (`shiguredo-changelog` スキル参照)

### ビルド・テスト

```bash
# フォーマット / チェック
./gradlew ktlintFormat
./gradlew ktlintCheck

# 単体テスト
./gradlew :sora-android-sdk:testDebugUnitTest

# リリースビルド
./gradlew :sora-android-sdk:assembleRelease

# e2e テスト (Gradle Managed Device: Pixel 7 / API 35 / arm64)
TEST_SORA_SIGNALING_URL=wss://... \
TEST_SECRET_KEY=... \
TEST_CHANNEL_ID_PREFIX=... \
TEST_CHANNEL_ID_SUFFIX=... \
./gradlew pixelApi35AndroidE2ETest
```

- e2e テストの環境変数は `buildConfigField` として埋め込まれる (`TEST_SORA_SIGNALING_URL` / `TEST_SECRET_KEY` / `TEST_CHANNEL_ID_PREFIX` / `TEST_CHANNEL_ID_SUFFIX`)
- `include_app_dir.txt` にサンプルアプリのパスを書くと複数プロジェクト構成で一緒にビルドできる (settings.gradle.kts)

### 規約

- コメントはすべて日本語。ログメッセージはすべて英語
- 全角と半角の間には半角スペースを入れる。絵文字は使わない
- モックやスタブは使わない (テストは実物ベース)
- 変更履歴は `CHANGES.md` に記載する
- git 運用・issue 運用は `shiguredo-git` / `shiguredo-issues` スキルに従う

## 既知の制限事項・注意点

- **カスタム ADM 指定時は音声オプションが無視される**: `audioDeviceModule` が非 null の場合、`audioSource` / `useStereoInput` / `useStereoOutput` / `audioAttributes` / `useHardware*` は SDK 内部の ADM 生成に使われない
- **ステレオ受信の SDP 書き換えはサーバ offer 経路のみ**: クライアント offer 経路 (通常接続) では answer SDP を組み立てないため書き換えが動作しない
- **映像ハードミュートは SDK 内部生成カメラのみ**: `setVideoHardMute` は `enableVideoUpstream(eglContext, cameraConfig)` で `SoraCameraConfig` を指定した場合のみ利用可能
- **`signalingMetadata` 未指定時は metadata を送信しない**: 従来 (空文字送信) と挙動が異なるため、空文字を明示指定するか確認が必要 (`[CHANGE]`)
- **エミュレーターでは動作保証しない**: カメラ・音声デバイスの制約による
- **Sora 側設定が必要な機能**: messaging_only 接続、RPC、DataChannel シグナリング、ステレオ受信 (クライアント offer 経路)、H.265 などは Sora サーバ側の設定に依存する

## クイックリファレンス

| やりたいこと | コード |
|---|---|
| 接続 | `SoraMediaChannel(...)` → `connect()` |
| 切断 | `disconnect()` |
| 接続確立の検知 | `Listener.onConnect` |
| エラー検知 | `Listener.onError(reason, message)` |
| 音声ソフトミュート | `setAudioSoftMute(true)` |
| 音声ハードミュート | `setAudioHardMute(true)` (suspend) |
| 映像ソフトミュート | `setVideoSoftMute(true)` |
| 映像ハードミュート | `setVideoHardMute(true)` (SoraCameraConfig 必須) |
| カメラ切り替え | `switchCamera(handler)` |
| 統計取得 | `getStats()` (suspend) または `getStatsIntervalMSec` + `onPeerConnectionStatsReady` |
| メッセージ送信 | `sendDataChannelMessage("#label", data)` |
| メッセージ受信 | `Listener.onDataChannelMessage` |
| RPC 呼び出し | `rpc(method, paramsJson)` (suspend) |
| ステレオ送信 | `SoraAudioOption.useStereoInput = true` |
| ステレオ受信 | `SoraAudioOption.useStereoOutput = true` + `audioAttributes` |
