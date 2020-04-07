package jp.shiguredo.sora.sdk.channel

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import jp.shiguredo.sora.sdk.channel.SoraMediaChannel.Listener
import jp.shiguredo.sora.sdk.channel.data.ChannelAttendeesCount
import jp.shiguredo.sora.sdk.channel.option.PeerConnectionOption
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.rtc.PeerChannel
import jp.shiguredo.sora.sdk.channel.rtc.PeerChannelImpl
import jp.shiguredo.sora.sdk.channel.rtc.PeerNetworkConfig
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannel
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannelImpl
import jp.shiguredo.sora.sdk.channel.signaling.message.*
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.ReusableCompositeDisposable
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*
import java.util.*
import kotlin.concurrent.schedule

/**
 * [SignalingChannel] と [PeerChannel] を
 * 管理、協調動作させるためのクラス
 *
 * Sora に接続するアプリケーションは、このクラスを利用することでシグナリングの
 * 詳細が隠蔽され、単一の [Listener] でイベントを受けることが出来ます。
 *
 * @constructor
 * SoraMediaChannel インスタンスを生成します。
 *
 * cf.
 * - シグナリングの手順とデータに関しては Sora のドキュメント
 *   [](https://sora.shiguredo.jp/doc/SIGNALING.html)を参照ください
 *
 * @param context `android.content.Context`
 * @param signalingEndpoint シグナリングの URL
 * @param signalingMetadata connect メッセージに含める `metadata`
 * @param channelId Sora に接続するためのチャネル ID
 * @param mediaOption 映像、音声に関するオプション
 * @param timeoutSeconds WebSocket の接続タイムアウト[秒]
 * @param listener イベントリスナー
 * @param clientId connect メッセージに含める `client_id`
 * @param signalingNotifyMetadata connect メッセージに含める `signaling_notify_metadata`
 */
class SoraMediaChannel @JvmOverloads constructor(
        private val context:                 Context,
        private val signalingEndpoint:       String,
        private val channelId:               String?,
        private val signalingMetadata:       Any?                 = "",
        private val mediaOption:             SoraMediaOption,
        private val timeoutSeconds:          Long                 = DEFAULT_TIMEOUT_SECONDS,
        private var listener:                Listener?,
        private val clientId:                String?              = null,
        private val signalingNotifyMetadata: Any?                 = null,
        private val peerConnectionOption:    PeerConnectionOption = PeerConnectionOption()
        ) {
    companion object {
        private val TAG = SoraMediaChannel::class.simpleName

        const val DEFAULT_TIMEOUT_SECONDS = 10L
    }

    val role = mediaOption.requiredRole
    var getStatsTimer: Timer? = null

    /**
     * [SoraMediaChannel] からコールバックイベントを受けるリスナー
     */
    interface Listener {
        /**
         * ローカルストリームが追加されたときに呼び出されるコールバック
         *
         * cf.
         * - `org.webrtc.MediaStream`
         * - `org.webrtc.MediaStream.videoTracks`
         *
         * @param mediaChannel イベントが発生したチャネル
         * @param ms 追加されたメディアストリーム
         */
        fun onAddLocalStream(mediaChannel: SoraMediaChannel, ms: MediaStream) {}

        /**
         * リモートストリームが追加されたときに呼び出されるコールバック
         *
         * cf.
         * - `org.webrtc.MediaStream`
         * - `org.webrtc.MediaStream.videoTracks`
         *
         * @param mediaChannel イベントが発生したチャネル
         * @param ms 追加されたメディアストリーム
         */
        fun onAddRemoteStream(mediaChannel: SoraMediaChannel, ms: MediaStream) {}

        /**
         * リモートストリームが削除されたときに呼び出されるコールバック
         *
         * cf.
         * - `org.webrtc.MediaStream.label()`
         *
         * @param mediaChannel イベントが発生したチャネル
         * @param label メディアストリームのラベル (`ms.label()`)
         */
        fun onRemoveRemoteStream(mediaChannel: SoraMediaChannel, label: String) {}

        /**
         * Sora との接続が確立されたときに呼び出されるコールバック
         *
         * @see PeerChannel
         * @param mediaChannel イベントが発生したチャネル
         */
        fun onConnect(mediaChannel: SoraMediaChannel) {}

        /**
         * Sora との接続が切断されたときに呼び出されるコールバック
         *
         * @see PeerChannel
         * @param mediaChannel イベントが発生したチャネル
         */
        fun onClose(mediaChannel: SoraMediaChannel) {}

        /**
         * Sora との通信やメディアでエラーが発生したときに呼び出されるコールバック
         *
         * cf.
         * - `org.webrtc.PeerConnection`
         * - `org.webrtc.PeerConnection.Observer`
         *
         * @see PeerChannel
         * @param reason エラーの理由
         */
        fun onError(mediaChannel: SoraMediaChannel, reason: SoraErrorReason) {}

        /**
         * Sora との通信やメディアでエラーが発生したときに呼び出されるコールバック
         *
         * cf.
         * - `org.webrtc.PeerConnection`
         * - `org.webrtc.PeerConnection.Observer`
         *
         * @see PeerChannel
         * @param reason エラーの理由
         * @param message エラーの情報
         */
        fun onError(mediaChannel: SoraMediaChannel, reason: SoraErrorReason, message: String) {}

        /**
         * Sora との通信やメディアで警告が発生したときに呼び出されるコールバック
         *
         * cf.
         * - `org.webrtc.PeerConnection`
         * - `org.webrtc.PeerConnection.Observer`
         *
         * @see PeerChannel
         * @param reason 警告の理由
         */
        fun onWarning(mediaChannel: SoraMediaChannel, reason: SoraErrorReason) {}

        /**
         * Sora との通信やメディアで警告が発生したときに呼び出されるコールバック
         *
         * cf.
         * - `org.webrtc.PeerConnection`
         * - `org.webrtc.PeerConnection.Observer`
         *
         * @see PeerChannel
         * @param reason 警告の理由
         * @param message 警告の情報
         */
        fun onWarning(mediaChannel: SoraMediaChannel, reason: SoraErrorReason, message: String) {}

        /**
         * 接続しているチャネルの参加者が増減したときに呼び出されるコールバック
         *
         * @param mediaChannel イベントが発生したチャネル
         * @param attendees 配信者数と視聴者数を含むオブジェクト
         */
        fun onAttendeesCountUpdated(mediaChannel: SoraMediaChannel, attendees: ChannelAttendeesCount) {}

        /**
         * Sora のシグナリング通知機能の通知を受信したときに呼び出されるコールバック
         *
         * @param mediaChannel イベントが発生したチャネル
         * @param notification プッシュ API により受信したメッセージ
         */
        fun onNotificationMessage(mediaChannel: SoraMediaChannel, notification : NotificationMessage) {}

        /**
         * Sora のプッシュ API によりメッセージを受信したときに呼び出されるコールバック
         *
         * @param mediaChannel イベントが発生したチャネル
         * @param push プッシュ API により受信したメッセージ
         */
        fun onPushMessage(mediaChannel: SoraMediaChannel, push : PushMessage) {}

        /**
         * PeerConnection の getStats() 統計情報を取得したときに呼び出されるコールバック
         *
         * cf.
         * - https://w3c.github.io/webrtc-stats/
         * - https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/getStats
         *
         * @param mediaChannel イベントが発生したチャネル
         * @param statsReports 統計レポート
         */
        fun onPeerConnectionStatsReady(mediaChannel: SoraMediaChannel, statsReport: RTCStatsReport) {}

        /**
         * サイマルキャスト配信のエンコーダ設定を変更するためのコールバック
         *
         * 引数の encodings は Sora が送ってきた設定を反映した RtpParameters.Encoding のリストです。
         * デフォルトの実装ではなにも行いません。
         * このコールバックを実装し、引数のオブジェクトを変更することで、アプリケーションの要件に従った
         * 設定をエンコーダにわたすことができます。
         *
         * cf. Web 標準の対応 API は次のとおりです。libwebrtc の native(C++) と android の実装は
         * 異なりますので注意してください。
         * - https://w3c.github.io/webrtc-pc/#dom-rtcrtpencodingparameters
         * - https://w3c.github.io/webrtc-pc/#dom-rtcrtpsender-setparameters
         *
         * @param mediaChannel イベントが発生したチャネル
         * @param encodings Sora から送信された encodings
         * @return encodings または、それを変更したオブジェクト
         */
        fun onSenderEncodings(mediaChannel: SoraMediaChannel, encodings: List<RtpParameters.Encoding>) {}

    }

    private var peer:            PeerChannel?      = null
    private var signaling:       SignalingChannel? = null

    private var closing = false
    private var connectionId: String? = null

    private val compositeDisposable = ReusableCompositeDisposable()

    private val signalingListener = object : SignalingChannel.Listener {

        override fun onDisconnect() {
            SoraLogger.d(TAG, "[channel:$role] @signaling:onClose")
            disconnect()
        }

        override fun onConnect() {
            SoraLogger.d(TAG, "[channel:$role] @signaling:onOpen")
        }

        override fun onInitialOffer(offerMessage: OfferMessage) {
            SoraLogger.d(TAG, "[channel:$role] @signaling:onInitialOffer")
            this@SoraMediaChannel.connectionId = offerMessage.connectionId
            handleInitialOffer(offerMessage)
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
            when (notification.eventType) {
                "connection.created", "connection.destroyed" -> {
                    val attendees = ChannelAttendeesCount(
                            numberOfDownstreams = notification.numberOfDownstreamConnections!!,
                            numberOfUpstreams = notification.numberOfUpstreamConnections!!
                    )
                    listener?.onAttendeesCountUpdated(this@SoraMediaChannel, attendees)
                }
            }
            listener?.onNotificationMessage(this@SoraMediaChannel, notification)
        }

        override fun onPushMessage(push: PushMessage) {
            SoraLogger.d(TAG, "[channel:$role] @signaling:onPushMessage")
            listener?.onPushMessage(this@SoraMediaChannel, push)
        }

        override fun onError(reason: SoraErrorReason) {
            listener?.onError(this@SoraMediaChannel, reason)
        }
    }

    private val peerListener = object : PeerChannel.Listener {

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
            SoraLogger.d(TAG, "[channel:$role] @peer:onAddRemoteStream msid=:${ms.id}, connectionId=${connectionId}")
            if (mediaOption.multistreamEnabled && connectionId != null && ms.id == connectionId) {
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

        override fun onSenderEncodings(encodings: List<RtpParameters.Encoding>) {
            SoraLogger.d(TAG, "[channel:$role] @peer:onSenderEncodings")
            listener?.onSenderEncodings(this@SoraMediaChannel, encodings)
        }

        override fun onError(reason: SoraErrorReason) {
            listener?.onError(this@SoraMediaChannel, reason)
        }

        override fun onError(reason: SoraErrorReason, message: String) {
            listener?.onError(this@SoraMediaChannel, reason, message)
        }

        override fun onWarning(reason: SoraErrorReason) {
            listener?.onWarning(this@SoraMediaChannel, reason)
        }

        override fun onWarning(reason: SoraErrorReason, message: String) {
            listener?.onWarning(this@SoraMediaChannel, reason, message)
        }

        override fun onDisconnect() {
            SoraLogger.d(TAG, "[channel:$role] @peer:onClose")
            disconnect()
        }
    }

    /**
     * Sora に接続します。
     *
     * アプリケーションで接続後の処理が必要な場合は [Listener.onConnect] で行います。
     */
    fun connect() {
        try {
            val kClass = Class.forName("org.webrtc.WebrtcBuildVersion")
            val webrtcBranch = kClass.getField("webrtc_branch").get(null)
            val webrtcCommit = kClass.getField("webrtc_commit").get(null)
            val maintVersion = kClass.getField("maint_version").get(null)
            val webrtcRevision = kClass.getField("webrtc_revision").get(null)
            val webrtcBuildVersion = listOf(webrtcBranch, webrtcCommit, maintVersion)
                    .joinToString(separator = ".")
            SoraLogger.d(TAG, "libwebrtc version = ${webrtcBuildVersion} @ ${webrtcRevision}")
        } catch (e : ClassNotFoundException) {
            SoraLogger.d(TAG, "connect: libwebrtc other than Shiguredo build is used.")
        }

        SoraLogger.d(TAG, """connect: SoraMediaOption
            |upstreamIsRequired      = ${mediaOption.upstreamIsRequired}
            |downstreamIsRequired    = ${mediaOption.downstreamIsRequired}
            |multistreamEnabled      = ${mediaOption.multistreamEnabled}
            |audioIsRequired         = ${mediaOption.audioIsRequired}
            |audioUpstreamEnabled    = ${mediaOption.audioUpstreamEnabled}
            |audioDownstreamEnabled  = ${mediaOption.audioDownstreamEnabled}
            |audioCodec              = ${mediaOption.audioCodec}
            |audioBitRate            = ${mediaOption.audioBitrate}
            |audioSource             = ${mediaOption.audioOption.audioSource}
            |useStereoInput          = ${mediaOption.audioOption.useStereoInput}
            |useStereoOutput         = ${mediaOption.audioOption.useStereoOutput}
            |videoIsRequired         = ${mediaOption.videoIsRequired}
            |videoUpstreamEnabled    = ${mediaOption.videoUpstreamEnabled}
            |videoDownstreamEnabled  = ${mediaOption.videoDownstreamEnabled}
            |videoCodec              = ${mediaOption.videoCodec}
            |videoBitRate            = ${mediaOption.videoBitrate}
            |simulcastEnabled        = ${mediaOption.simulcastEnabled}
            |videoCapturer           = ${mediaOption.videoCapturer}
            |spotlight               = ${mediaOption.spotlight}
            |signalingMetadata       = ${this.signalingMetadata}
            |clientId                = ${this.clientId}
            |signalingNotifyMetadata = ${this.signalingNotifyMetadata}""".trimMargin())

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
        timer!!.schedule(object: TimerTask() {
            override fun run() {
                timer = null
                onTimeout()
            }
        }, timeoutSeconds*1000)
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private fun onTimeout() {
        SoraLogger.d(TAG, "[channel:$role] @peer:onTimeout")
        listener?.onError(this, SoraErrorReason.TIMEOUT)
        disconnect()
    }


    private fun requestClientOfferSdp() {
        val mediaOption = SoraMediaOption().apply {
            enableVideoDownstream(null)
            enableAudioDownstream()
        }
        val clientOfferPeer = PeerChannelImpl(
                appContext = context,
                networkConfig = PeerNetworkConfig(
                        serverConfig = OfferConfig(
                                iceServers = emptyList<IceServer>(),
                                iceTransportPolicy = ""),
                        mediaOption = mediaOption),
                mediaOption = mediaOption,
                listener = null
        )
        clientOfferPeer.run {
            val subscription = requestClientOfferSdp()
                    .observeOn(Schedulers.io())
                    .subscribeBy(
                            onSuccess = {
                                SoraLogger.d(TAG, "[channel:$role] @peer:clientOfferSdp")
                                disconnect()
                                val handler = Handler(Looper.getMainLooper())
                                handler.post() {
                                    connectSignalingChannel(it)
                                }
                            },
                            onError = {
                                SoraLogger.w(TAG,
                                        "[channel:$role] failed request client offer SDP: ${it.message}")
                                disconnect()
                            }

                    )
            compositeDisposable.add(subscription)
        }
    }

    private fun connectSignalingChannel(clientOfferSdp : SessionDescription) {
        signaling = SignalingChannelImpl(
                endpoint                = signalingEndpoint,
                role                    = role,
                channelId               = channelId,
                mediaOption             = mediaOption,
                connectMetadata         = signalingMetadata,
                listener                = signalingListener,
                clientOfferSdp          = clientOfferSdp,
                clientId                = clientId,
                signalingNotifyMetadata = signalingNotifyMetadata
        )
        signaling!!.connect()
    }

    private fun handleInitialOffer(offerMessage: OfferMessage) {
        SoraLogger.d(TAG, "[channel:$role] @peer:start")

        peer = PeerChannelImpl(
                appContext    = context,
                networkConfig = PeerNetworkConfig(
                        serverConfig = offerMessage.config,
                        mediaOption  = mediaOption
                ),
                mediaOption   = mediaOption,
                listener      = peerListener
        )

        if (0 < peerConnectionOption.getStatsIntervalMSec) {
            getStatsTimer = Timer()
            SoraLogger.d(TAG, "Schedule getStats with inteval ${peerConnectionOption.getStatsIntervalMSec} [msec]")
            getStatsTimer?.schedule(0L, peerConnectionOption.getStatsIntervalMSec) {
                peer?.getStats(RTCStatsCollectorCallback {
                    listener?.onPeerConnectionStatsReady(this@SoraMediaChannel, it)
                })
            }
        }
        peer?.run {
            val subscription = handleInitialRemoteOffer(offerMessage.sdp, offerMessage.encodings)
                    .observeOn(Schedulers.io())
                    .subscribeBy(
                            onSuccess = {
                                SoraLogger.d(TAG, "[channel:$role] @peer:answer")
                                signaling?.sendAnswer(it.description)
                            },
                            onError = {
                                val msg = "[channel:$role] failure in handleInitialOffer: ${it.message}"
                                SoraLogger.w(TAG, msg)
                                disconnect()
                            }
                    )
            compositeDisposable.add(subscription)
        }
    }

    private fun handleUpdateOffer(sdp: String) {
        peer?.run {
            val subscription = handleUpdatedRemoteOffer(sdp)
                    .observeOn(Schedulers.io())
                    .subscribeBy(
                            onSuccess = {
                                SoraLogger.d(TAG, "[channel:$role] @peer:about to send updated answer")
                                signaling?.sendUpdateAnswer(it.description)
                            },
                            onError = {
                                val msg = "[channel:$role] failed handle updated offer: ${it.message}"
                                SoraLogger.w(TAG, msg)
                                disconnect()
                            }
                    )
            compositeDisposable.add(subscription)
        }
    }

    private fun handleReOffer(sdp: String) {
        peer?.run {
            val subscription = handleUpdatedRemoteOffer(sdp)
                    .observeOn(Schedulers.io())
                    .subscribeBy(
                            onSuccess = {
                                SoraLogger.d(TAG, "[channel:$role] @peer:about to send re-answer")
                                signaling?.sendReAnswer(it.description)
                            },
                            onError = {
                                val msg = "[channel:$role] failed handle re-offer: ${it.message}"
                                SoraLogger.w(TAG, msg)
                                disconnect()
                            }
                    )
            compositeDisposable.add(subscription)
        }
    }

    /**
     * Sora への接続を切断します。
     *
     * アプリケーションとして切断後の処理が必要な場合は [Listener.onClose] で行います。
     */
    fun disconnect() {
        if (closing)
            return
        closing = true

        stopTimer()
        compositeDisposable.dispose()

        listener?.onClose(this)
        listener = null

        signaling?.disconnect()
        signaling = null

        getStatsTimer?.cancel()
        getStatsTimer = null

        peer?.disconnect()
        peer = null
    }
}
