package jp.shiguredo.sora.sdk.channel

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import jp.shiguredo.sora.sdk.BuildConfig
import jp.shiguredo.sora.sdk.channel.data.ChannelAttendeesCount
import jp.shiguredo.sora.sdk.channel.option.PeerConnectionOption
import jp.shiguredo.sora.sdk.channel.option.SoraForwardingFilterOption
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.rtc.PeerChannel
import jp.shiguredo.sora.sdk.channel.rtc.PeerChannelImpl
import jp.shiguredo.sora.sdk.channel.rtc.PeerNetworkConfig
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannel
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannelCloseEvent
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannelImpl
import jp.shiguredo.sora.sdk.channel.signaling.message.MessageConverter
import jp.shiguredo.sora.sdk.channel.signaling.message.NotificationMessage
import jp.shiguredo.sora.sdk.channel.signaling.message.OfferConfig
import jp.shiguredo.sora.sdk.channel.signaling.message.OfferMessage
import jp.shiguredo.sora.sdk.channel.signaling.message.PushMessage
import jp.shiguredo.sora.sdk.channel.signaling.message.SwitchedMessage
import jp.shiguredo.sora.sdk.error.SoraDisconnectReason
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.error.SoraMessagingError
import jp.shiguredo.sora.sdk.util.ReusableCompositeDisposable
import jp.shiguredo.sora.sdk.util.SoraLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RTCStatsCollectorCallback
import org.webrtc.RTCStatsReport
import org.webrtc.RtpParameters
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule

/**
 * Sora への接続を行うクラスです.
 *
 * [SignalingChannel] と [PeerChannel] の管理、協調動作制御を行っています.
 * このクラスを利用することでシグナリングの詳細が隠蔽され、単一の [Listener] でイベントを受けることができます.
 *
 * シグナリングの手順とデータに関しては下記の Sora のドキュメントを参照ください.
 *  - [WebSocket 経由のシグナリング](https://sora.shiguredo.jp/doc/SIGNALING)
 *  - [DataChannel 経由のシグナリング](https://sora-doc.shiguredo.jp/DATA_CHANNEL_SIGNALING)
 *
 * @constructor
 * SoraMediaChannel インスタンスを生成します.
 *
 * @param context `android.content.Context`
 * @param signalingEndpoint シグナリングの URL
 * @param signalingEndpointCandidates シグナリングの URL (クラスター機能で複数の URL を利用したい場合はこちらを指定する)
 * @param signalingMetadata connect メッセージに含める `metadata`
 * @param channelId Sora に接続するためのチャネル ID
 * @param mediaOption 映像、音声に関するオプション
 * @param timeoutSeconds WebSocket の接続タイムアウト (秒)
 * @param listener イベントリスナー
 * @param clientId connect メッセージに含める `client_id`
 * @param signalingNotifyMetadata connect メッセージに含める `signaling_notify_metadata`
 * @param dataChannelSignaling connect メッセージに含める `data_channel_signaling`
 * @param ignoreDisconnectWebSocket connect メッセージに含める `ignore_disconnect_websocket`
 * @param dataChannels connect メッセージに含める `data_channels`
 * @param bundleId connect メッセージに含める `bundle_id`
 * @param forwardingFilterOption 転送フィルター機能の設定
 * @param forwardingFiltersOption リスト形式の転送フィルター機能の設定
 */
class SoraMediaChannel
    @JvmOverloads
    constructor(
        private val context: Context,
        private val signalingEndpoint: String? = null,
        private val signalingEndpointCandidates: List<String> = emptyList(),
        private val channelId: String,
        private val signalingMetadata: Any? = "",
        private val mediaOption: SoraMediaOption,
        private val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        private var listener: Listener?,
        private val clientId: String? = null,
        private val signalingNotifyMetadata: Any? = null,
        private val peerConnectionOption: PeerConnectionOption = PeerConnectionOption(),
        dataChannelSignaling: Boolean? = null,
        ignoreDisconnectWebSocket: Boolean? = null,
        dataChannels: List<Map<String, Any>>? = null,
        private var bundleId: String? = null,
        @Deprecated(
            "この項目は 2025 年 12 月リリース予定の Sora にて廃止されます",
            ReplaceWith("forwardingFiltersOption"),
            DeprecationLevel.WARNING,
        )
        private val forwardingFilterOption: SoraForwardingFilterOption? = null,
        private val forwardingFiltersOption: List<SoraForwardingFilterOption>? = null,
    ) {
        companion object {
            private val TAG = SoraMediaChannel::class.simpleName

            const val DEFAULT_TIMEOUT_SECONDS = 10L
            private const val WEBSOCKET_DISCONNECT_DELAY_SECONDS = 10L
        }

        // connect メッセージに含める `data_channel_signaling`
        private val connectDataChannelSignaling: Boolean?

        // connect メッセージに含める `ignore_disconnect_websocket`
        private val connectIgnoreDisconnectWebSocket: Boolean?

        // connect メッセージに含める `data_channels`
        private val connectDataChannels: List<Map<String, Any>>?

        // メッセージング機能で利用する DataChannel
        // offer メッセージに含まれる `data_channels` のうち、 label が # から始まるもの
        private var dataChannelsForMessaging: List<Map<String, Any>>? = null

        // DataChannel 経由のシグナリングが有効なら true
        // Sora から渡された値 (= offer メッセージ) を参照して更新している
        private var offerDataChannelSignaling: Boolean = false

        // DataChannel 経由のシグナリング利用時に WebSocket の切断を無視するなら true
        // 同じく switched メッセージを参照して更新している
        private var switchedIgnoreDisconnectWebSocket: Boolean = false

        // DataChannel メッセージの ByteBuffer を String に変換するための CharsetDecoder
        // CharsetDecoder はスレッドセーフではないため注意
        private val utf8Decoder =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)

        /**
         * メッセージングで受信したデータを UTF-8 の文字列に変換します.
         *
         * CharsetDecoder がスレッド・セーフでないため、 Synchronized を付与しています.
         * アプリケーションで大量のメッセージを処理してパフォーマンスの問題が生じた場合、独自の関数を定義して利用することを推奨します.
         *
         * @param data 受信したメッセージ
         * @return UTF-8 の文字列
         */
        @Synchronized
        fun dataToString(data: ByteBuffer): String {
            return utf8Decoder.decode(data).toString()
        }

        init {
            if ((signalingEndpoint == null && signalingEndpointCandidates.isEmpty()) ||
                (signalingEndpoint != null && signalingEndpointCandidates.isNotEmpty())
            ) {
                throw IllegalArgumentException("Either signalingEndpoint or signalingEndpointCandidates must be specified")
            }

            // コンストラクタ以外で dataChannelSignaling, ignoreDisconnectWebSocket を参照すべきではない
            // 各種ロジックの判定には Sora のメッセージに含まれる値を参照する必要があるため、以下を利用するのが正しい
            // - offerDataChannelSignaling
            // - switchedIgnoreDisconnectWebSocket
            connectDataChannelSignaling = dataChannelSignaling
            connectIgnoreDisconnectWebSocket = ignoreDisconnectWebSocket
            connectDataChannels = dataChannels
        }

        /**
         * ロール
         */
        val role = mediaOption.role ?: mediaOption.requiredRole

        private var getStatsTimer: Timer? = null
        private var dataChannels: MutableMap<String, DataChannel> = mutableMapOf()

        /**
         * [SoraMediaChannel] からコールバックイベントを受けるリスナー
         */
        interface Listener {
            /**
             * ローカルストリームが追加されたときに呼び出されるコールバック.
             *
             * cf.
             * - `org.webrtc.MediaStream`
             * - `org.webrtc.MediaStream.videoTracks`
             *
             * @param mediaChannel イベントが発生したチャネル
             * @param ms 追加されたメディアストリーム
             */
            fun onAddLocalStream(
                mediaChannel: SoraMediaChannel,
                ms: MediaStream,
            ) {}

            /**
             * リモートストリームが追加されたときに呼び出されるコールバック.
             *
             * cf.
             * - `org.webrtc.MediaStream`
             * - `org.webrtc.MediaStream.videoTracks`
             *
             * @param mediaChannel イベントが発生したチャネル
             * @param ms 追加されたメディアストリーム
             */
            fun onAddRemoteStream(
                mediaChannel: SoraMediaChannel,
                ms: MediaStream,
            ) {}

            /**
             * リモートストリームが削除されたときに呼び出されるコールバック.
             *
             * cf.
             * - `org.webrtc.MediaStream.label()`
             *
             * @param mediaChannel イベントが発生したチャネル
             * @param label メディアストリームのラベル (`ms.label()`)
             */
            fun onRemoveRemoteStream(
                mediaChannel: SoraMediaChannel,
                label: String,
            ) {}

            /**
             * Sora との接続が確立されたときに呼び出されるコールバック.
             *
             * cf.
             * - [PeerChannel]
             *
             * @param mediaChannel イベントが発生したチャネル
             */
            fun onConnect(mediaChannel: SoraMediaChannel) {}

            /**
             * Sora との接続が切断されたときに呼び出されるコールバック.
             *
             * cf.
             * - [PeerChannel]
             *
             * @param mediaChannel イベントが発生したチャネル
             * @param closeEvent 切断イベント
             */
            fun onClose(
                mediaChannel: SoraMediaChannel,
                closeEvent: SoraCloseEvent,
            ) {}

            /**
             * Sora との接続が切断されたときに呼び出されるコールバック.
             *
             * cf.
             * - [PeerChannel]
             *
             * @param mediaChannel イベントが発生したチャネル
             */
            @Deprecated(
                "onClose(mediaChannel: SoraMediaChannel) は非推奨です " +
                    "onClose(mediaChannel: SoraMediaChannel, closeEvent: SoraCloseEvent) を利用してください." +
                    " このコールバックは 2027 年中に廃止予定です.",
                ReplaceWith("onClose(SoraMediaChannel, SoraCloseEvent)"),
                DeprecationLevel.WARNING,
            )
            fun onClose(mediaChannel: SoraMediaChannel) {}

            /**
             * Sora との通信やメディアでエラーが発生したときに呼び出されるコールバック.
             * message の内容がない場合は、空文字列が渡されます.
             *
             * cf.
             * - `org.webrtc.PeerConnection`
             * - `org.webrtc.PeerConnection.Observer`
             * - [PeerChannel]
             *
             * @param reason エラーの理由
             * @param message エラーの情報
             */
            fun onError(
                mediaChannel: SoraMediaChannel,
                reason: SoraErrorReason,
                message: String,
            ) {}

            /**
             * Sora との通信やメディアで警告が発生したときに呼び出されるコールバック.
             *
             * cf.
             * - `org.webrtc.PeerConnection`
             * - `org.webrtc.PeerConnection.Observer`
             * - [PeerChannel]
             *
             * @param reason 警告の理由
             */
            fun onWarning(
                mediaChannel: SoraMediaChannel,
                reason: SoraErrorReason,
            ) {}

            /**
             * Sora との通信やメディアで警告が発生したときに呼び出されるコールバック.
             *
             * cf.
             * - `org.webrtc.PeerConnection`
             * - `org.webrtc.PeerConnection.Observer`
             * - [PeerChannel]
             *
             * @param reason 警告の理由
             * @param message 警告の情報
             */
            fun onWarning(
                mediaChannel: SoraMediaChannel,
                reason: SoraErrorReason,
                message: String,
            ) {}

            /**
             * 接続しているチャネルの参加者が増減したときに呼び出されるコールバック.
             *
             * @param mediaChannel イベントが発生したチャネル
             * @param attendees 配信者数と視聴者数を含むオブジェクト
             */
            fun onAttendeesCountUpdated(
                mediaChannel: SoraMediaChannel,
                attendees: ChannelAttendeesCount,
            ) {}

            /**
             * Sora から type: offer メッセージを受信した際に呼び出されるコールバック.
             *
             * @param mediaChannel イベントが発生したチャネル
             * @param offer Sora から受信した type: offer メッセージ
             */
            fun onOfferMessage(
                mediaChannel: SoraMediaChannel,
                offer: OfferMessage,
            ) {}

            /**
             * Sora のシグナリング通知機能の通知を受信したときに呼び出されるコールバック.
             *
             * @param mediaChannel イベントが発生したチャネル
             * @param notification Sora の通知機能により受信したメッセージ
             */
            fun onNotificationMessage(
                mediaChannel: SoraMediaChannel,
                notification: NotificationMessage,
            ) {}

            /**
             * Sora のプッシュ API によりメッセージを受信したときに呼び出されるコールバック.
             *
             * @param mediaChannel イベントが発生したチャネル
             * @param push プッシュ API により受信したメッセージ
             */
            fun onPushMessage(
                mediaChannel: SoraMediaChannel,
                push: PushMessage,
            ) {}

            /**
             * PeerConnection の getStats() 統計情報を取得したときに呼び出されるコールバック.
             *
             * cf.
             * - https://w3c.github.io/webrtc-stats/
             * - https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/getStats
             *
             * @param mediaChannel イベントが発生したチャネル
             * @param statsReport 統計レポート
             */
            fun onPeerConnectionStatsReady(
                mediaChannel: SoraMediaChannel,
                statsReport: RTCStatsReport,
            ) {}

            /**
             * サイマルキャスト配信のエンコーダー設定を変更するためのコールバック.
             *
             * 引数の encodings は Sora が送ってきた設定を反映した RtpParameters.Encoding のリストです.
             * デフォルトの実装ではなにも行いません.
             * このコールバックを実装し、引数のオブジェクトを変更することで、アプリケーションの要件に従った
             * 設定をエンコーダーにわたすことができます.
             *
             * cf. Web 標準の対応 API は次のとおりです. libwebrtc の native(C++) と android の実装は
             * 異なりますので注意してください.
             * - https://w3c.github.io/webrtc-pc/#dom-rtcrtpencodingparameters
             * - https://w3c.github.io/webrtc-pc/#dom-rtcrtpsender-setparameters
             *
             * @param mediaChannel イベントが発生したチャネル
             * @param encodings Sora から送信された encodings
             */
            fun onSenderEncodings(
                mediaChannel: SoraMediaChannel,
                encodings: List<RtpParameters.Encoding>,
            ) {}

            /**
             * データチャネルが利用可能になったときに呼び出されるコールバック
             *
             * @param mediaChannel イベントが発生したチャネル
             * @param dataChannels Sora の offer メッセージに含まれる data_channels のうち label が # から始まるもの
             */
            fun onDataChannel(
                mediaChannel: SoraMediaChannel,
                dataChannels: List<Map<String, Any>>?,
            ) {}

            /**
             * メッセージング機能のメッセージを受信したときに呼び出されるコールバック
             * ラベルが # から始まるメッセージのみが通知されます
             *
             * @param mediaChannel イベントが発生したチャネル
             * @param label ラベル
             * @param data 受信したメッセージ
             */
            fun onDataChannelMessage(
                mediaChannel: SoraMediaChannel,
                label: String,
                data: ByteBuffer,
            ) {}
        }

        private var peer: PeerChannel? = null
        private var signaling: SignalingChannel? = null

        private var switchedToDataChannel = false

        // 切断処理を開始したことを示すフラグ
        private var closing = false

        // type: redirect で再利用するために、初回接続時の clientOffer を保持する
        private var clientOffer: SessionDescription? = null

        // DataChannel のみのシグナリングで signaling label の type: close を受信したときに取得する code と reason を保持する
        private var dataChannelSignalingCloseEvent: SoraCloseEvent? = null

        // WebSocket 切断の遅延処理用の CoroutineJob
        private var delayedWebSocketDisconnectJob: Job? = null
        private val coroutineScope = CoroutineScope(Dispatchers.IO)

        /**
         * コネクション ID.
         */
        var connectionId: String? = null
            private set

        /**
         * 最初に type: connect を最初に送信したエンドポイント.
         *
         * Sora から type: redirect メッセージを受信した場合、 contactSignalingEndpoint と connectedSignalingEndpoint の値は異なります
         * type: redirect メッセージを受信しなかった場合、 contactSignalingEndpoint と connectedSignalingEndpoint は同じ値です
         */
        var contactSignalingEndpoint: String? = null
            private set

        /**
         * 接続中のエンドポイント.
         */
        var connectedSignalingEndpoint: String? = null
            private set

        private val compositeDisposable = ReusableCompositeDisposable()

        private val signalingListener =
            object : SignalingChannel.Listener {
                override fun onDisconnect(
                    disconnectReason: SoraDisconnectReason?,
                    event: SignalingChannelCloseEvent?,
                ) {
                    SoraLogger.d(
                        TAG,
                        "[channel:$role] @signaling:onDisconnect " +
                            "switchedToDataChannel=$switchedToDataChannel, " +
                            "switchedIgnoreDisconnectWebSocket=$switchedIgnoreDisconnectWebSocket",
                    )
                    if (switchedToDataChannel && switchedIgnoreDisconnectWebSocket) {
                        // なにもしない
                        SoraLogger.d(TAG, "[channel:$role] @signaling:onDisconnect: IGNORE")
                    } else {
                        if (event != null) {
                            internalDisconnect(disconnectReason, SoraCloseEvent(event.code, event.reason))
                            return
                        }
                        internalDisconnect(disconnectReason)
                    }
                }

                override fun onConnect(endpoint: String) {
                    SoraLogger.d(TAG, "[channel:$role] @signaling:onOpen")

                    // SignalingChannel の初回接続時のみ contactSignalingEndpoint を設定する
                    // Sora から type: redirect を受信した場合、このコールバックは複数回実行される可能性がある
                    if (contactSignalingEndpoint == null) {
                        contactSignalingEndpoint = endpoint
                    }
                }

                override fun onInitialOffer(
                    offerMessage: OfferMessage,
                    endpoint: String,
                ) {
                    SoraLogger.d(TAG, "[channel:$role] @signaling:onInitialOffer")
                    this@SoraMediaChannel.connectionId = offerMessage.connectionId
                    handleInitialOffer(offerMessage)
                    connectedSignalingEndpoint = endpoint
                    listener?.onOfferMessage(this@SoraMediaChannel, offerMessage)
                }

                override fun onSwitched(switchedMessage: SwitchedMessage) {
                    SoraLogger.d(TAG, "[channel:$role] @signaling:onSwitched")
                    switchedIgnoreDisconnectWebSocket = switchedMessage.ignoreDisconnectWebsocket ?: false
                    handleSwitched(switchedMessage)
                }

                override fun onUpdatedOffer(sdp: String) {
                    SoraLogger.d(TAG, "[channel:$role] @signaling:onUpdatedOffer")
                    handleUpdateOffer(sdp)
                }

                override fun onReOffer(sdp: String) {
                    SoraLogger.d(TAG, "[channel:$role] @signaling:onReOffer")
                    handleReOffer(sdp)
                }

                override fun onNotificationMessage(notification: NotificationMessage) {
                    SoraLogger.d(TAG, "[channel:$role] @signaling:onNotificationMessage")
                    handleNotificationMessage(notification)
                }

                override fun onPushMessage(push: PushMessage) {
                    SoraLogger.d(TAG, "[channel:$role] @signaling:onPushMessage")
                    listener?.onPushMessage(this@SoraMediaChannel, push)
                }

                override fun onError(reason: SoraErrorReason) {
                    SoraLogger.d(TAG, "[channel:$role] @signaling:onError:$reason")
                    val ignoreError = switchedIgnoreDisconnectWebSocket
                    if (switchedToDataChannel && ignoreError) {
                        // なにもしない
                        SoraLogger.d(TAG, "[channel:$role] @signaling:onError: IGNORE reason=$reason")
                    } else {
                        // エラーをリスナーに通知
                        listener?.onError(this@SoraMediaChannel, reason, "")
                    }
                }

                override fun getStats(handler: (RTCStatsReport?) -> Unit) {
                    if (peer != null) {
                        peer!!.getStats(handler)
                    } else {
                        handler(null)
                    }
                }

                override fun onRedirect(location: String) {
                    SoraLogger.d(TAG, "[channel:$role] @peer:onRedirect")

                    SoraLogger.i(TAG, "[channel:$role] closing old SignalingChannel")
                    signaling?.disconnect(null)

                    SoraLogger.i(TAG, "[channel:$role] opening new SignalingChannel")
                    val handler = Handler(Looper.getMainLooper())
                    handler.post {
                        connectSignalingChannel(clientOffer, location)
                    }
                }
            }

        private val peerListener =
            object : PeerChannel.Listener {
                override fun onLocalIceCandidateFound(candidate: IceCandidate) {
                    SoraLogger.d(TAG, "[channel:$role] @peer:onLocalIceCandidateFound")
                    signaling?.sendCandidate(candidate.sdp)
                }

                override fun onRemoveRemoteStream(label: String) {
                    SoraLogger.d(TAG, "[channel:$role] @peer:onRemoveRemoteStream:$label")
                    if (connectionId != null && label == connectionId) {
                        SoraLogger.d(TAG, "[channel:$role] this stream is mine, ignore")
                        return
                    }
                    listener?.onRemoveRemoteStream(this@SoraMediaChannel, label)
                }

                override fun onAddRemoteStream(ms: MediaStream) {
                    SoraLogger.d(TAG, "[channel:$role] @peer:onAddRemoteStream msid=:${ms.id}, connectionId=$connectionId")
                    if (mediaOption.multistreamEnabled != false && connectionId != null && ms.id == connectionId) {
                        SoraLogger.d(TAG, "[channel:$role] this stream is mine, ignore: ${ms.id}")
                        return
                    }
                    listener?.onAddRemoteStream(this@SoraMediaChannel, ms)
                }

                override fun onAddLocalStream(ms: MediaStream) {
                    SoraLogger.d(TAG, "[channel:$role] @peer:onAddLocalStream")
                    listener?.onAddLocalStream(this@SoraMediaChannel, ms)
                }

                override fun onConnect() {
                    SoraLogger.d(TAG, "[channel:$role] @peer:onConnect")
                    stopTimer()
                    listener?.onConnect(this@SoraMediaChannel)
                }

                override fun onDataChannelOpen(
                    label: String,
                    dataChannel: DataChannel,
                ) {
                    this@SoraMediaChannel.dataChannels[label] = dataChannel
                }

                override fun onDataChannelMessage(
                    label: String,
                    dataChannel: DataChannel,
                    dataChannelBuffer: DataChannel.Buffer,
                ) {
                    if (peer == null) {
                        return
                    }

                    SoraLogger.d(TAG, "[channel:$role] @peer:onDataChannelMessage label=$label")
                    val buffer = peer!!.unzipBufferIfNeeded(label, dataChannelBuffer.data)

                    if (label.startsWith("#")) {
                        listener?.onDataChannelMessage(this@SoraMediaChannel, label, buffer)
                    } else {
                        try {
                            val message = dataToString(buffer)
                            MessageConverter.parseType(message)?.let { type ->
                                when (label) {
                                    "signaling" -> handleSignalingViaDataChannel(dataChannel, type, message)
                                    "notify" -> handleNotifyViaDataChannel(type, message)
                                    "push" -> handlePushViaDataChannel(type, message)
                                    "stats" -> handleStatsViaDataChannel(dataChannel, type, message)
                                    "e2ee" -> {
                                        SoraLogger.i(TAG, "NOT IMPLEMENTED: label=$label, type=$type, message=$message")
                                    }
                                    else -> {
                                        SoraLogger.i(TAG, "Unknown label: label=$label, type=$type, message=$message")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            SoraLogger.e(TAG, "failed to process DataChannel message", e)
                        }
                    }
                }

                override fun onDataChannelClosed(
                    label: String,
                    dataChannel: DataChannel,
                ) {
                    SoraLogger.d(TAG, "[channel:$role] @peer:onDataChannelClosed label=$label")

                    dataChannelSignalingCloseEvent?.let { event ->
                        internalDisconnect(SoraDisconnectReason.DATACHANNEL_ONCLOSE, event)
                        return
                    }

                    // dataChannelSignalingCloseEvent が null の場合、DataChannel が閉じられた理由を知る方法がないため reason は null にする
                    internalDisconnect(null)
                }

                override fun onSenderEncodings(encodings: List<RtpParameters.Encoding>) {
                    SoraLogger.d(TAG, "[channel:$role] @peer:onSenderEncodings")
                    listener?.onSenderEncodings(this@SoraMediaChannel, encodings)
                }

                override fun onError(reason: SoraErrorReason) {
                    SoraLogger.d(TAG, "[channel:$role] @peer:onError:$reason")
                    listener?.onError(this@SoraMediaChannel, reason, "")
                }

                override fun onError(
                    reason: SoraErrorReason,
                    message: String,
                ) {
                    SoraLogger.d(TAG, "[channel:$role] @peer:onError:$reason:$message")
                    listener?.onError(this@SoraMediaChannel, reason, message)
                }

                override fun onWarning(reason: SoraErrorReason) {
                    SoraLogger.d(TAG, "[channel:$role] @peer:onWarning:$reason")
                    listener?.onWarning(this@SoraMediaChannel, reason)
                }

                override fun onWarning(
                    reason: SoraErrorReason,
                    message: String,
                ) {
                    SoraLogger.d(TAG, "[channel:$role] @peer:onWarning:$reason:$message")
                    listener?.onWarning(this@SoraMediaChannel, reason, message)
                }

                override fun onDisconnect(disconnectReason: SoraDisconnectReason?) {
                    SoraLogger.d(TAG, "[channel:$role] @peer:onDisconnect:$disconnectReason")

                    internalDisconnect(disconnectReason)
                }
            }

        /**
         * Sora に接続します.
         *
         * アプリケーションで接続後の処理が必要な場合は [Listener.onConnect] で行います.
         */
        fun connect() {
            try {
                val kClass = Class.forName("org.webrtc.WebrtcBuildVersion")
                val webrtcBranch = kClass.getField("webrtc_branch").get(null)
                val webrtcCommit = kClass.getField("webrtc_commit").get(null)
                val maintVersion = kClass.getField("maint_version").get(null)
                val webrtcRevision = kClass.getField("webrtc_revision").get(null)
                val webrtcBuildVersion =
                    listOf(webrtcBranch, webrtcCommit, maintVersion)
                        .joinToString(separator = ".")
                SoraLogger.d(TAG, "libwebrtc version = $webrtcBuildVersion @ $webrtcRevision")
            } catch (e: ClassNotFoundException) {
                SoraLogger.d(TAG, "connect: libwebrtc other than Shiguredo build is used.")
            }

            SoraLogger.d(
                TAG,
                """connect: SoraMediaOption
            |requiredRole               = ${mediaOption.requiredRole}
            |upstreamIsRequired         = ${mediaOption.upstreamIsRequired}
            |downstreamIsRequired       = ${mediaOption.downstreamIsRequired}
            |multistreamEnabled         = ${mediaOption.multistreamEnabled}
            |audioIsRequired            = ${mediaOption.audioIsRequired}
            |audioUpstreamEnabled       = ${mediaOption.audioUpstreamEnabled}
            |audioDownstreamEnabled     = ${mediaOption.audioDownstreamEnabled}
            |audioCodec                 = ${mediaOption.audioCodec}
            |audioBitRate               = ${mediaOption.audioBitrate}
            |audioSource                = ${mediaOption.audioOption.audioSource}
            |useStereoInput             = ${mediaOption.audioOption.useStereoInput}
            |useStereoOutput            = ${mediaOption.audioOption.useStereoOutput}
            |videoIsRequired            = ${mediaOption.videoIsRequired}
            |videoUpstreamEnabled       = ${mediaOption.videoUpstreamEnabled}
            |videoUpstreamContext       = ${mediaOption.videoUpstreamContext}
            |videoDownstreamEnabled     = ${mediaOption.videoDownstreamEnabled}
            |videoDownstreamContext     = ${mediaOption.videoDownstreamContext}
            |videoEncoderFactory        = ${mediaOption.videoEncoderFactory}
            |videoDecoderFactory        = ${mediaOption.videoDecoderFactory}
            |videoCodec                 = ${mediaOption.videoCodec}
            |videoBitRate               = ${mediaOption.videoBitrate}
            |videoVp9Params             = ${mediaOption.videoVp9Params}
            |videoAv1Params             = ${mediaOption.videoAv1Params}
            |videoH264Params            = ${mediaOption.videoH264Params}
            |videoCapturer              = ${mediaOption.videoCapturer}
            |simulcastEnabled           = ${mediaOption.simulcastEnabled}
            |simulcastRid               = ${mediaOption.simulcastRid}
            |spotlightEnabled           = ${mediaOption.spotlightEnabled}
            |spotlightNumber            = ${mediaOption.spotlightOption?.spotlightNumber}
            |audioStreamingLanguageCode = ${mediaOption.audioStreamingLanguageCode}
            |signalingMetadata          = ${this.signalingMetadata}
            |clientId                   = ${this.clientId}
            |bundleId                   = ${this.bundleId}
            |signalingNotifyMetadata    = ${this.signalingNotifyMetadata}
            |forwardingFilter           = ${this.forwardingFilterOption}
            |forwardingFilters           = ${this.forwardingFiltersOption}
                """.trimMargin(),
            )

            if (closing) {
                return
            }
            startTimer()
            requestClientOfferSdp()
        }

        private var timer: Timer? = null

        private fun startTimer() {
            stopTimer()
            timer = Timer()
            timer!!.schedule(
                object : TimerTask() {
                    override fun run() {
                        timer = null
                        onTimeout()
                    }
                },
                timeoutSeconds * 1000,
            )
        }

        private fun stopTimer() {
            timer?.cancel()
            timer = null
        }

        private fun onTimeout() {
            SoraLogger.d(TAG, "[channel:$role] @peer:onTimeout")
            listener?.onError(this, SoraErrorReason.TIMEOUT, "")

            // ここに来た場合、 Sora に接続出来ていない = disconnect メッセージを送信する必要がない
            // そのため、 reason は null で良い
            internalDisconnect(null)
        }

        private fun requestClientOfferSdp() {
            val mediaOption =
                SoraMediaOption().apply {
                    enableVideoDownstream(null)
                    enableAudioDownstream()
                }
            val clientOfferPeer =
                PeerChannelImpl(
                    appContext = context,
                    networkConfig =
                        PeerNetworkConfig(
                            serverConfig =
                                OfferConfig(
                                    iceServers = emptyList(),
                                    iceTransportPolicy = "",
                                ),
                            mediaOption = mediaOption,
                        ),
                    mediaOption = mediaOption,
                    listener = null,
                )
            clientOfferPeer.run {
                val subscription =
                    requestClientOfferSdp()
                        .observeOn(Schedulers.io())
                        .subscribeBy(
                            onSuccess = {
                                SoraLogger.d(TAG, "[channel:$role] @peer:clientOfferSdp")
                                disconnect(null)

                                if (it.isFailure) {
                                    SoraLogger.d(TAG, "[channel:$role] failed to create client offer SDP: ${it.exceptionOrNull()?.message}")
                                }
                                val handler = Handler(Looper.getMainLooper())
                                clientOffer = it.getOrNull()
                                handler.post {
                                    connectSignalingChannel(clientOffer)
                                }
                            },
                            onError = {
                                SoraLogger.w(
                                    TAG,
                                    "[channel:$role] failed request client offer SDP: ${it.message}",
                                )
                                disconnect(SoraDisconnectReason.SIGNALING_FAILURE)
                            },
                        )
                compositeDisposable.add(subscription)
            }
        }

        private fun connectSignalingChannel(
            clientOfferSdp: SessionDescription?,
            redirectLocation: String? = null,
        ) {
            val endpoints =
                when {
                    redirectLocation != null -> listOf(redirectLocation)
                    signalingEndpointCandidates.isNotEmpty() -> signalingEndpointCandidates
                    else -> listOf(signalingEndpoint!!)
                }

            signaling =
                SignalingChannelImpl(
                    endpoints = endpoints,
                    role = role,
                    channelId = channelId,
                    connectDataChannelSignaling = connectDataChannelSignaling,
                    connectIgnoreDisconnectWebSocket = connectIgnoreDisconnectWebSocket,
                    mediaOption = mediaOption,
                    connectMetadata = signalingMetadata,
                    listener = signalingListener,
                    clientOfferSdp = clientOfferSdp,
                    clientId = clientId,
                    bundleId = bundleId,
                    signalingNotifyMetadata = signalingNotifyMetadata,
                    connectDataChannels = connectDataChannels,
                    redirect = redirectLocation != null,
                    forwardingFilterOption = forwardingFilterOption,
                    forwardingFiltersOption = forwardingFiltersOption,
                )
            signaling!!.connect()
        }

        private fun handleInitialOffer(offerMessage: OfferMessage) {
            SoraLogger.d(TAG, "[channel:$role] initial offer")

            SoraLogger.d(TAG, "[channel:$role] @peer:starting")
            peer =
                PeerChannelImpl(
                    appContext = context,
                    networkConfig =
                        PeerNetworkConfig(
                            serverConfig = offerMessage.config,
                            mediaOption = mediaOption,
                        ),
                    mediaOption = mediaOption,
                    simulcastEnabled = offerMessage.simulcast,
                    dataChannelConfigs = offerMessage.dataChannels,
                    listener = peerListener,
                )

            if (offerMessage.dataChannels?.isNotEmpty() == true) {
                offerDataChannelSignaling = true
                dataChannelsForMessaging =
                    offerMessage.dataChannels.filter {
                        it.containsKey("label") && (it["label"] as? String)?.startsWith("#") ?: false
                    }
            }

            if (0 < peerConnectionOption.getStatsIntervalMSec) {
                getStatsTimer = Timer()
                SoraLogger.d(TAG, "Schedule getStats with interval ${peerConnectionOption.getStatsIntervalMSec} [msec]")
                getStatsTimer?.schedule(0L, peerConnectionOption.getStatsIntervalMSec) {
                    peer?.getStats(
                        RTCStatsCollectorCallback {
                            listener?.onPeerConnectionStatsReady(this@SoraMediaChannel, it)
                        },
                    )
                }
            }
            peer?.run {
                val subscription =
                    handleInitialRemoteOffer(offerMessage.sdp, offerMessage.mid, offerMessage.encodings)
                        .observeOn(Schedulers.io())
                        .subscribeBy(
                            onSuccess = {
                                SoraLogger.d(TAG, "[channel:$role] @peer:answer")
                                signaling?.sendAnswer(it.description)
                            },
                            onError = {
                                val msg = "[channel:$role] failure in handleInitialOffer: ${it.message}"
                                SoraLogger.w(TAG, msg, it)
                                disconnect(SoraDisconnectReason.SIGNALING_FAILURE)
                            },
                        )
                compositeDisposable.add(subscription)
            }
        }

        private fun handleSwitched(switchedMessage: SwitchedMessage) {
            switchedToDataChannel = true
            switchedIgnoreDisconnectWebSocket = switchedMessage.ignoreDisconnectWebsocket ?: false
            val earlyCloseWebSocket = switchedIgnoreDisconnectWebSocket
            // ignore_disconnect_websocket が true の場合は、 WebSocket の接続は不要となるので切断する
            // WebSocket 経由でシグナリングメッセージを送信する際、
            // WebSocket 切断とのレースコンディションを最小限に抑えるため WebSocket の切断までに遅延を入れる。
            // TODO: WebSocket 切断の遅延より長く DataChannel の確立が遅延した場合、 WebSocket 切断と
            //       type: disconnect のレースコンディションは存在するため
            //       DataChannel の signaling ラベルがオープンしてることを確認してから WebSocket の切断を行う必要がある
            //       ただし現在の実装でも実用上はほぼ問題ないと想定されるため、対応優先度は低い
            if (earlyCloseWebSocket) {
                delayedWebSocketDisconnectJob =
                    coroutineScope.launch {
                        // delay はミリ秒
                        delay(WEBSOCKET_DISCONNECT_DELAY_SECONDS * 1000)
                        // WebSocket の切断を行う
                        signaling?.disconnect(null)
                    }
            }
            listener?.onDataChannel(this, dataChannelsForMessaging)
        }

        private fun handleUpdateOffer(sdp: String) {
            peer?.run {
                val subscription =
                    handleUpdatedRemoteOffer(sdp)
                        .observeOn(Schedulers.io())
                        .subscribeBy(
                            onSuccess = {
                                SoraLogger.d(TAG, "[channel:$role] @peer:about to send updated answer")
                                signaling?.sendUpdateAnswer(it.description)
                            },
                            onError = {
                                val msg = "[channel:$role] failed handle updated offer: ${it.message}"
                                SoraLogger.w(TAG, msg)
                                disconnect(SoraDisconnectReason.SIGNALING_FAILURE)
                            },
                        )
                compositeDisposable.add(subscription)
            }
        }

        private fun handleReOffer(sdp: String) {
            peer?.run {
                val subscription =
                    handleUpdatedRemoteOffer(sdp)
                        .observeOn(Schedulers.io())
                        .subscribeBy(
                            onSuccess = {
                                SoraLogger.d(TAG, "[channel:$role] @peer:about to send re-answer")
                                signaling?.sendReAnswer(it.description)
                            },
                            onError = {
                                val msg = "[channel:$role] failed handle re-offer: ${it.message}"
                                SoraLogger.w(TAG, msg)
                                disconnect(SoraDisconnectReason.SIGNALING_FAILURE)
                            },
                        )
                compositeDisposable.add(subscription)
            }
        }

        /**
         * DataChannel 経由のシグナリングの signaling ラベルで受信したメッセージを処理します.
         */
        private fun handleSignalingViaDataChannel(
            dataChannel: DataChannel,
            type: String,
            message: String,
        ) {
            when (type) {
                "re-offer" -> {
                    val reOfferMessage = MessageConverter.parseReOfferMessage(message)
                    handleReOfferViaDataChannel(dataChannel, reOfferMessage.sdp)
                }
                "close" -> {
                    val closeMessage = MessageConverter.parseCloseMessage(message)
                    // DataChannel のみのシグナリング かつ Sora の設定で `data_channel_signaling_close_message` が有効な場合に
                    // Sora から切断が発生した際、DataChannel を閉じる前に `type: close` メッセージが送られてくるため、
                    // dataChannelSignalingCloseEvent に code と reason を設定する
                    dataChannelSignalingCloseEvent = SoraCloseEvent(closeMessage.code, closeMessage.reason)
                }
                else -> {
                    SoraLogger.i(TAG, "Unknown signaling type: type=$type, message=$message")
                }
            }
        }

        private fun handleReOfferViaDataChannel(
            dataChannel: DataChannel,
            sdp: String,
        ) {
            peer?.run {
                val subscription =
                    handleUpdatedRemoteOffer(sdp)
                        .observeOn(Schedulers.io())
                        .subscribeBy(
                            onSuccess = {
                                SoraLogger.d(TAG, "[channel:$role] @peer:about to send re-answer")
                                peer?.sendReAnswer(dataChannel, it.description)
                            },
                            onError = {
                                val msg = "[channel:$role] failed handle re-offer: ${it.message}"
                                SoraLogger.w(TAG, msg)
                                disconnect(SoraDisconnectReason.SIGNALING_FAILURE)
                            },
                        )
                compositeDisposable.add(subscription)
            }
        }

        /**
         * DataChannel 経由のシグナリングの notify ラベルで受信したメッセージを処理します.
         */
        private fun handleNotifyViaDataChannel(
            type: String,
            message: String,
        ) {
            when (type) {
                "notify" -> {
                    val notificationMessage = MessageConverter.parseNotificationMessage(message)
                    handleNotificationMessage(notificationMessage)
                }
                else -> {
                    SoraLogger.i(TAG, "Unknown notify type: type=$type, message=$message")
                }
            }
        }

        /**
         * DataChannel 経由のシグナリングの push ラベルで受信したメッセージを処理します.
         */
        private fun handlePushViaDataChannel(
            type: String,
            message: String,
        ) {
            when (type) {
                "push" -> {
                    val pushMessage = MessageConverter.parsePushMessage(message)
                    listener?.onPushMessage(this@SoraMediaChannel, pushMessage)
                }
                else -> {
                    SoraLogger.i(TAG, "Unknown push type: type=$type, message=$message")
                }
            }
        }

        /**
         * DataChannel 経由のシグナリングの stats ラベルで受信したメッセージを処理します.
         */
        private fun handleStatsViaDataChannel(
            dataChannel: DataChannel,
            type: String,
            message: String,
        ) {
            when (type) {
                "req-stats" -> {
                    // req-stats は type しかないので parse しない
                    handleReqStats(dataChannel)
                }
                else -> {
                    SoraLogger.i(TAG, "Unknown stats type: type=$type, message=$message")
                }
            }
        }

        private fun handleReqStats(dataChannel: DataChannel) {
            peer?.getStats {
                it?.let { reports ->
                    peer?.sendStats(dataChannel, reports)
                }
            }
        }

        private fun handleNotificationMessage(notification: NotificationMessage) {
            when (notification.eventType) {
                "connection.created", "connection.destroyed" -> {
                    val attendees =
                        ChannelAttendeesCount(
                            numberOfSendrecvConnections = notification.numberOfSendrecvConnections ?: 0,
                            numberOfSendonlyConnections = notification.numberOfSendonlyConnections ?: 0,
                            numberOfRecvonlyConnections = notification.numberOfRecvonlyConnections ?: 0,
                        )
                    listener?.onAttendeesCountUpdated(this@SoraMediaChannel, attendees)
                }
            }
            listener?.onNotificationMessage(this@SoraMediaChannel, notification)
        }

        /**
         * Sora への接続を切断します.
         *
         * アプリケーションとして切断後の処理が必要な場合は [Listener.onClose] で行います.
         */
        fun disconnect() {
            // SoraMediaChannel.disconnect() 起因で internalDisconnect() を呼んだ場合、
            // SoraCloseEvent は固定値として code: 1000, reason: "NO-ERROR" を設定する
            internalDisconnect(SoraDisconnectReason.NO_ERROR, SoraCloseEvent.createClientDisconnectEvent())
        }

        private fun internalDisconnect(
            disconnectReason: SoraDisconnectReason?,
            closeEvent: SoraCloseEvent? = null,
        ) {
            if (closing) {
                return
            }
            closing = true
            SoraLogger.d(TAG, "[channel:$role] internalDisconnect: $disconnectReason")

            stopTimer()
            delayedWebSocketDisconnectJob?.cancel()
            delayedWebSocketDisconnectJob = null
            disconnectReason?.let {
                sendDisconnectIfNeeded(it)
            }
            compositeDisposable.dispose()

            // 既に type: disconnect を送信しているので、 disconnectReason は null で良い
            signaling?.disconnect(null)
            signaling = null

            getStatsTimer?.cancel()
            getStatsTimer = null

            // 既に type: disconnect を送信しているので、 disconnectReason は null で良い
            peer?.disconnect(null)
            peer = null

            listener?.onClose(this)
            if (closeEvent != null) {
                listener?.onClose(this, closeEvent)
            } else {
                listener?.onClose(this, SoraCloseEvent.createClientDisconnectEvent())
            }
            listener = null

            // onClose によってアプリケーションで定義された切断処理を実行した後に contactSignalingEndpoint と connectedSignalingEndpoint を null にする
            contactSignalingEndpoint = null
            connectedSignalingEndpoint = null
        }

        private fun sendDisconnectOverWebSocket(disconnectReason: SoraDisconnectReason) {
            signaling?.sendDisconnect(disconnectReason)
        }

        private fun sendDisconnectOverDataChannel(disconnectReason: SoraDisconnectReason) {
            dataChannels["signaling"]?.let {
                peer?.sendDisconnect(it, disconnectReason)
            }
        }

        private fun sendDisconnectIfNeeded(disconnectReason: SoraDisconnectReason) {
            val state = peer?.connectionState()
            SoraLogger.d(
                TAG,
                "[channel:$role] sendDisconnectIfNeeded switched=$switchedToDataChannel, " +
                    "switchedIgnoreDisconnectWebSocket=$switchedIgnoreDisconnectWebSocket, " +
                    "reason=$disconnectReason, PeerConnectionState=$state",
            )

            if (state == PeerConnection.PeerConnectionState.FAILED) {
                // この関数に到達した時点で PeerConnectionState が FAILED なのでメッセージの送信は不要
                return
            }

            when (disconnectReason) {
                SoraDisconnectReason.NO_ERROR -> {
                    if (!offerDataChannelSignaling) {
                        // WebSocket のみ
                        sendDisconnectOverWebSocket(disconnectReason)
                    } else {
                        // WebSocket と DataChannel / DataChannel のみ
                        if (!switchedToDataChannel) {
                            // type: switched 未受信
                            sendDisconnectOverWebSocket(disconnectReason)
                        } else {
                            // type: switched 受信済
                            sendDisconnectOverDataChannel(disconnectReason)
                        }
                    }
                }

                SoraDisconnectReason.WEBSOCKET_ONCLOSE, SoraDisconnectReason.WEBSOCKET_ONERROR -> {
                    // DataChannel シグナリング利用かつ、WebSocket 切断を無視しない場合
                    if (switchedToDataChannel && !switchedIgnoreDisconnectWebSocket) {
                        sendDisconnectOverDataChannel(disconnectReason)
                    }
                }

                SoraDisconnectReason.SIGNALING_FAILURE, SoraDisconnectReason.PEER_CONNECTION_STATE_FAILED -> {
                    // メッセージの送信は不要
                }

                SoraDisconnectReason.DATACHANNEL_ONCLOSE -> {
                    // DataChannel のみのシグナリング利用時に Sora から type: close を受信した場合、メッセージの送信は不要
                }

                else -> {
                    // SoraDisconnectReason のすべての条件が網羅されていて欲しい
                    if (BuildConfig.DEBUG) {
                        throw Exception("when statement should be exhaustive.")
                    }
                    SoraLogger.i(TAG, "when statement should be exhaustive.")
                }
            }
        }

        /**
         * メッセージを送信します.
         *
         * @param label ラベル
         * @param data 送信する文字列
         * @return エラー
         */
        fun sendDataChannelMessage(
            label: String,
            data: String,
        ): SoraMessagingError {
            return sendDataChannelMessage(label, ByteBuffer.wrap(data.toByteArray()))
        }

        /**
         * メッセージを送信します.
         *
         * @param label ラベル
         * @param data 送信するデータ
         * @return エラー
         */
        fun sendDataChannelMessage(
            label: String,
            data: ByteBuffer,
        ): SoraMessagingError {
            if (!switchedToDataChannel) {
                return SoraMessagingError.NOT_READY
            }

            if (!label.startsWith("#")) {
                SoraLogger.w(TAG, "label must start with \"#\"")
                return SoraMessagingError.INVALID_LABEL
            }

            val dataChannel = dataChannels[label]

            if (dataChannel == null) {
                SoraLogger.w(TAG, "data channel for label: $label not found")
                return SoraMessagingError.LABEL_NOT_FOUND
            }

            if (dataChannel.state() != DataChannel.State.OPEN) {
                return SoraMessagingError.INVALID_STATE
            }

            if (peer == null) {
                return SoraMessagingError.PEER_CHANNEL_UNAVAILABLE
            }
            val buffer = peer!!.zipBufferIfNeeded(label, data)

            val succeeded = dataChannel.send(DataChannel.Buffer(buffer, true))
            SoraLogger.d(TAG, "state=${dataChannel.state()}  succeeded=$succeeded")
            return if (succeeded) {
                SoraMessagingError.OK
            } else {
                SoraMessagingError.SEND_FAILED
            }
        }
    }
