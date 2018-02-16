package jp.shiguredo.sora.sdk.channel

import android.content.Context
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import jp.shiguredo.sora.sdk.channel.data.ChannelAttendeesCount
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.rtc.PeerChannel
import jp.shiguredo.sora.sdk.channel.rtc.PeerChannelImpl
import jp.shiguredo.sora.sdk.channel.rtc.PeerNetworkConfig
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannel
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannelImpl
import jp.shiguredo.sora.sdk.channel.signaling.message.NotificationMessage
import jp.shiguredo.sora.sdk.channel.signaling.message.OfferConfig
import jp.shiguredo.sora.sdk.channel.signaling.message.PushMessage
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.ReusableCompositeDisposable
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import java.util.*

/**
 * [SignalingChannel] と [PeerChannel] を
 * 管理、協調動作させるためのクラス
 *
 * Sora に接続するアプリケーションは、このクラスを利用することでシグナリングの
 * 詳細が隠蔽され、単一の [Listener] でイベントを受けることが出来ます。
 */
class SoraMediaChannel(
        private val context:           Context,
        private val signalingEndpoint: String,
        private val channelId:         String?,
        private val signalingMetadata: String = "",
        private val mediaOption:       SoraMediaOption,
        private val timeoutSeconds:    Long = 10,
        private var listener:          Listener?
) {
    val TAG = SoraMediaChannel::class.simpleName

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
         * - `org.webrtc.MediaStream.vidoTracks`
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
         * Sora のプッシュ API によりメッセージを受信したときに呼び出されるコールバック
         *
         * @param mediaChannel イベントが発生したチャネル
         * @param push プッシュ API により受信したメッセージ
         */
        fun onPushMessage(mediaChannel: SoraMediaChannel, push : PushMessage) {}
    }

    private var peer:      PeerChannel?      = null
    private var signaling: SignalingChannel? = null

    private var closing = false
    private var clientId: String? = null

    private val compositeDisposable = ReusableCompositeDisposable()

    private val signalingListener = object : SignalingChannel.Listener {

        override fun onDisconnect() {
            SoraLogger.d(TAG, "[channel:$role] @signaling:onClose")
            disconnect()
        }

        override fun onConnect() {
            SoraLogger.d(TAG, "[channel:$role] @signaling:onOpen")
        }

        override fun onInitialOffer(clientId: String, sdp: String, config: OfferConfig) {
            SoraLogger.d(TAG, "[channel:$role] @signaling:onInitialOffer")
            this@SoraMediaChannel.clientId = clientId
            handleInitialOffer(sdp, config)
        }

        override fun onUpdatedOffer(sdp: String) {
            SoraLogger.d(TAG, "[channel:$role] @signaling:onUpdatedOffer")
            handleUpdateOffer(sdp)
        }

        override fun onNotificationMessage(notification: NotificationMessage) {
            SoraLogger.d(TAG, "[channel:$role] @signaling:onNotificationMessage")
            when (notification.eventType) {
                "connection.created", "connection.destroyed" -> {
                    val attendees = ChannelAttendeesCount(
                            numberOfDownstreams = notification.numberOfDownstreamConnections,
                            numberOfUpstreams = notification.numberOfUpstreamConnections
                    )
                    listener?.onAttendeesCountUpdated(this@SoraMediaChannel, attendees)
                }
                // XXX in future, support more varieties of notification message here
                else -> SoraLogger.i(TAG, "unsupported notification event type: "
                        + notification.eventType)
            }
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
            if (clientId != null && label == clientId) {
                SoraLogger.d(TAG, "[channel:$role] this stream is mine, ignore")
                return
            }
            listener?.onRemoveRemoteStream(this@SoraMediaChannel, label)
        }

        override fun onAddRemoteStream(ms: MediaStream) {
            SoraLogger.d(TAG, "[channel:$role] @peer:onAddRemoteStream:${ms.label()}")
            if (mediaOption.upstreamIsRequired && clientId != null && ms.label() == clientId) {
                SoraLogger.d(TAG, "[channel:$role] this stream is mine, ignore")
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
        if (closing) {
            return
        }
        connectSignalingChannel()
        startTimer()
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

    private fun handleInitialOffer(sdp: String, config: OfferConfig) {

        SoraLogger.d(TAG, "[channel:$role] @peer:start")

        val networkConfig = PeerNetworkConfig(
                serverConfig = config,
                enableTcp    = true
        )

        peer = PeerChannelImpl(
                appContext    = context,
                networkConfig = networkConfig,
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
                                SoraLogger.w(TAG, "[channel:$role] failed to start: ${it.message}")
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
                               SoraLogger.d(TAG, "[channel:$role] @peer:answer")
                               signaling?.sendUpdateAnswer(it.description)
                           },
                           onError = {
                               SoraLogger.w(TAG, "[channel:$role] failed handle updated offer: ${it.message}")
                               disconnect()
                           }
                    )
            compositeDisposable.add(subscription)
        }
    }

    private fun connectSignalingChannel() {
        signaling = SignalingChannelImpl(
                endpoint    = signalingEndpoint,
                role        = role,
                channelId   = channelId,
                mediaOption = mediaOption,
                metadata    = signalingMetadata,
                listener    = signalingListener
        )
        signaling!!.connect()
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
