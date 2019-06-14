package jp.shiguredo.sora.sdk.channel

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import jp.shiguredo.sora.sdk.channel.SoraMediaChannel.Listener
import jp.shiguredo.sora.sdk.channel.data.ChannelAttendeesCount
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.rtc.PeerChannel
import jp.shiguredo.sora.sdk.channel.rtc.PeerChannelImpl
import jp.shiguredo.sora.sdk.channel.rtc.PeerNetworkConfig
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannel
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannelImpl
import jp.shiguredo.sora.sdk.channel.signaling.message.IceServer
import jp.shiguredo.sora.sdk.channel.signaling.message.NotificationMessage
import jp.shiguredo.sora.sdk.channel.signaling.message.OfferConfig
import jp.shiguredo.sora.sdk.channel.signaling.message.PushMessage
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.ReusableCompositeDisposable
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription
import java.util.*

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
        private val signalingMetadata:       Any?             = "",
        private val mediaOption:             SoraMediaOption,
        private val timeoutSeconds:          Long             = DEFAULT_TIMEOUT_SECONDS,
        private var listener:                Listener?,
        private val clientId:                String?          = null,
        private val signalingNotifyMetadata: Any?             = null
        ) {
    companion object {
        private val TAG = SoraMediaChannel::class.simpleName

        const val DEFAULT_TIMEOUT_SECONDS = 10L
    }

    val role = mediaOption.requiredRole

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
         * Sora との接続でエラーが発生したときに呼び出されるコールバック
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

        override fun onInitialOffer(connectionId: String, sdp: String, config: OfferConfig?) {
            SoraLogger.d(TAG, "[channel:$role] @signaling:onInitialOffer")
            this@SoraMediaChannel.connectionId = connectionId
            handleInitialOffer(sdp, config)
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

        override fun onError(reason: SoraErrorReason) {
            listener?.onError(this@SoraMediaChannel, reason)
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
            SoraLogger.d(TAG, "connect: webrtc-build config version          = ${webrtcBuildVersion}")
            SoraLogger.d(TAG, "connect: webrtc-build commit hash             = ${webrtcRevision}")
        } catch (e : ClassNotFoundException) {
            SoraLogger.d(TAG, "connect: libwebrtc other than Shiguredo build is used.")
        }
        SoraLogger.d(TAG, "connect: mediaOption.upstreamIsRequired       = ${mediaOption.upstreamIsRequired}")
        SoraLogger.d(TAG, "connect: mediaOption.downstreamIsRequired     = ${mediaOption.downstreamIsRequired}")
        SoraLogger.d(TAG, "connect: mediaOption.multistreamEnabled       = ${mediaOption.multistreamEnabled}")
        SoraLogger.d(TAG, "connect: mediaOption.audioIsRequired          = ${mediaOption.audioIsRequired}")
        SoraLogger.d(TAG, "connect: mediaOption.audioUpstreamEnabled     = ${mediaOption.audioUpstreamEnabled}")
        SoraLogger.d(TAG, "connect: mediaOption.audioDownstreamEnabled   = ${mediaOption.audioDownstreamEnabled}")
        SoraLogger.d(TAG, "connect: mediaOption.audioCodec               = ${mediaOption.audioCodec}")
        SoraLogger.d(TAG, "connect: mediaOption.videoIsRequired          = ${mediaOption.videoIsRequired}")
        SoraLogger.d(TAG, "connect: mediaOption.videoUpstreamEnabled     = ${mediaOption.videoUpstreamEnabled}")
        SoraLogger.d(TAG, "connect: mediaOption.videoDownstreamEnabled   = ${mediaOption.videoDownstreamEnabled}")
        SoraLogger.d(TAG, "connect: mediaOption.videoCodec               = ${mediaOption.videoCodec}")
        SoraLogger.d(TAG, "connect: mediaOption.simulcastEnabled         = ${mediaOption.simulcastEnabled}")
        SoraLogger.d(TAG, "connect: mediaOption.videoCapturer            = ${mediaOption.videoCapturer}")
        SoraLogger.d(TAG, "connect: mediaOption.spotlight                = ${mediaOption.spotlight}")
        SoraLogger.d(TAG, "connect: mediaOption.sdpSemantics             = ${mediaOption.sdpSemantics}")
        SoraLogger.d(TAG, "connect: mediaChannel.signalingMetadata       = ${this.signalingMetadata}")
        SoraLogger.d(TAG, "connect: mediaChannel.clientId                = ${this.clientId}")
        SoraLogger.d(TAG, "connect: mediaChannel.signalingNotifyMetadata = ${this.signalingNotifyMetadata}")
        if (mediaOption.planB()) {
            SoraLogger.w(TAG, "Plan-B SDP semantics has no longer been supported. Unified plan should be used.")
        }

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

    private fun handleInitialOffer(sdp: String, config: OfferConfig?) {
        SoraLogger.d(TAG, "[channel:$role] @peer:start")

        peer = PeerChannelImpl(
                appContext    = context,
                networkConfig = PeerNetworkConfig(
                        serverConfig = config,
                        mediaOption  = mediaOption
                ),
                mediaOption   = mediaOption,
                listener      = peerListener
        )

        peer?.run {
            val subscription = handleInitialRemoteOffer(sdp)
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

        peer?.disconnect()
        peer = null
    }
}
