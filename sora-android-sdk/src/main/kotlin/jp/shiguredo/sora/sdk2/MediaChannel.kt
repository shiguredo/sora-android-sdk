package jp.shiguredo.sora.sdk2

import jp.shiguredo.sora.sdk.channel.SoraMediaChannel
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannel
import jp.shiguredo.sora.sdk.channel.signaling.message.PushMessage
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*

/**
 * メディアデータの送受信を行います。
 *
 * @property configuration 接続設定
 */
class MediaChannel internal constructor(
        val configuration: Configuration
) {

    internal companion object {
        val TAG = MediaChannel::class.simpleName!!
    }

    /**
     * 接続状態を表します。
     */
    enum class State {

        /**
         * 接続可能な状態
         */
        READY,

        /**
         * 接続試行中
         */
        CONNECTING,

        /**
         * 接続完了
         */
        CONNECTED,

        /**
         * 接続解除中
         */
        DISCONNECTING,

        /**
         * 接続解除完了
         */
        DISCONNECTED
    }

    /**
     * シグナリングチャネル
     */
    var signalingChannel: SignalingChannel? = null
        internal set

    /**
     * 接続 ID
     */
    val connectionId: String?
        get() = _basicMediaChannel?.connectionId

    /**
     * 接続状態
     */
    var state: State = State.READY
        internal set

    /**
     * ストリームのリスト
     */
    val streams: List<MediaStream>
    get() = _streams

    private var _streams: MutableList<MediaStream> = mutableListOf()

    /**
     * 送信ストリーム
     */
    val senderStream: MediaStream?
        get() =
            _basicMediaChannel?.peer?.senders?.firstOrNull().let { sender ->
                _streams.firstOrNull {
                    sender?.streams?.contains(it.id) ?: false
                }
            }

    /**
     * 映像キャプチャー
     */
    val videoCapturer: VideoCapturer?
        get() = configuration.videoCapturer

    /**
     * 映像レンダラーで共有する映像描画コンテキスト
     */
    val videoRenderingContext: VideoRenderingContext

    private var _completionHandler: ((error: Throwable?) -> Unit)? = null
    private var _onDisconnect: (() -> Unit)? = null
    private var _onFailure: ((error: Throwable) -> Unit)? = null
    private var _onAddLocalStream: (stream: MediaStream) -> Unit = {}
    private var _onAddRemoteStream: (stream: MediaStream) -> Unit = {}
    private var _onRemoveRemoteStream: (label: String) -> Unit = {}
    private var _onPush: (message: PushMessage) -> Unit = {}

    private var _basicMediaChannel: SoraMediaChannel? = null
    private var _basicMediaOption: SoraMediaOption? = null

    init {
        videoRenderingContext = configuration.videoRenderingContext ?: VideoRenderingContext()
    }

    internal fun connect(completionHandler: (error: Throwable?) -> Unit) {
        state = State.CONNECTING

        _completionHandler = completionHandler

        configuration.printDebug(TAG, "connect")

        _basicMediaOption = configuration.toSoraMediaOption()
        _basicMediaChannel = SoraMediaChannel(
                context           = configuration.context,
                signalingEndpoint = configuration.url,
                channelId         = configuration.channelId,
                mediaOption       = _basicMediaOption!!,
                listener          = basicMediaChannelListner)
        _basicMediaChannel!!.connect()
    }

    private fun basicDisconnect() {
        videoRenderingContext.release()
        _basicMediaChannel!!.disconnect()
        state = State.DISCONNECTED
    }

    /**
     * 接続を解除します。
     *
     * @see [onDisconnect]
     */
    fun disconnect() {
        state = State.DISCONNECTING
        basicDisconnect()
        if (_onDisconnect != null) {
            _onDisconnect!!()
            _onDisconnect = null
        }
    }

    private fun fail(error: Throwable) {
        basicDisconnect()

        when (state) {
            State.CONNECTING ->
                if (_completionHandler != null) {
                    _completionHandler!!(error)
                    _completionHandler = null
                }
            else ->
                if (_onFailure != null) {
                    _onFailure!!(error)
                    _onFailure = null
                }
        }
    }

    /**
     * 接続解除時に呼ばれるハンドラをセットします。
     *
     * @param handler ハンドラ
     */
    fun onDisconnect(handler: () -> Unit) {
        SoraLogger.d(TAG, "@onDisconnect")
        _onDisconnect = handler
    }

    /**
     * エラー発生時に呼ばれるハンドラをセットします。
     *
     * @param handler 発生したエラーを受け取るハンドラ
     */
    fun onFailure(handler: (error: Throwable) -> Unit) {
        SoraLogger.d(TAG, "@onFailure")
        _onFailure = handler
    }

    /**
     * ローカルのストリーム (送信ストリーム) が追加されたときに呼ばれるハンドラをセットします。
     *
     * @param handler 追加されたストリームを受け取るハンドラ
     */
    fun onAddLocalStream(handler: (stream: MediaStream) -> Unit) {
        SoraLogger.d(TAG, "@onAddLocalStream")
        _onAddLocalStream = handler
    }

    /**
     * リモートのストリーム (受信ストリーム) が追加されたときに呼ばれるハンドラをセットします。
     *
     * @param handler 追加されたストリームを受け取るハンドラ
     */
    fun onAddRemoteStream(handler: (stream: MediaStream) -> Unit) {
        SoraLogger.d(TAG, "@onAddRemoteStream")
        _onAddRemoteStream = handler
    }

    /**
     * リモートのストリーム (受信ストリーム) が除去されたときに呼ばれるハンドラをセットします。
     *
     * @param handler 除去されたストリームの ID を受け取るハンドラ
     */
    fun onRemoveRemoteStream(handler: (label: String) -> Unit) {
        SoraLogger.d(TAG, "@onRemoveRemoteStream")
        _onRemoveRemoteStream = handler
    }

    /**
     * ストリームを追加します。
     *
     * @param stream 追加するストリーム
     */
    fun addStream(stream: MediaStream) {
        _streams.add(stream)
    }

    private fun getStream(native: org.webrtc.MediaStream): MediaStream? {
        for (stream in _streams) {
            if (stream.nativeStream == native) {
                return stream
            }
        }
        return null
    }

    private val basicMediaChannelListner = object : SoraMediaChannel.Listener {

        override fun onConnect(mediaChannel: SoraMediaChannel) {
            SoraLogger.d(TAG, "onConnect")
            state = State.CONNECTED

            if (configuration.role.isSender && configuration.videoCapturer != null) {
                val size = configuration.videoFrameSize
                SoraLogger.d(TAG, "start capturer: width => ${size.width}, height => ${size.height}, fps => ${configuration.videoFps}")
                configuration.videoCapturer!!.startCapture(size.width, size.height, configuration.videoFps)
            }

            if (_completionHandler != null) {
                _completionHandler!!(null)
                _completionHandler = null
            }
        }

        override fun onClose(mediaChannel: SoraMediaChannel) {
            SoraLogger.d(TAG, "onClose")
            disconnect()
        }

        override fun onError(mediaChannel: SoraMediaChannel, reason: SoraErrorReason) {
            SoraLogger.d(TAG, "onError [$reason]")
            fail(SoraError(SoraError.Kind.fromReason(reason)))
        }

        override fun onError(mediaChannel: SoraMediaChannel, reason: SoraErrorReason, message: String) {
            SoraLogger.d(TAG, "onError [$reason]: $message")
            fail(SoraError(SoraError.Kind.fromReason(reason)))
        }

        override fun onAddRemoteStream(mediaChannel: SoraMediaChannel,
                                       ms: org.webrtc.MediaStream) {
            SoraLogger.d(TAG, "onAddRemoteStream")

            val newStream = MediaStream(this@MediaChannel, ms, null)
            addStream(newStream)
            _onAddRemoteStream(newStream)
        }

        override fun onAddLocalStream(mediaChannel: SoraMediaChannel,
                                      ms: org.webrtc.MediaStream,
                                      videoSource: VideoSource?) {
            SoraLogger.d(TAG, "onAddLocalStream")

            val newStream = MediaStream(this@MediaChannel, ms, videoSource)

            mediaChannel.peer?.senders?.let {
                for (sender in it) {
                    SoraLogger.d(TAG, "add sender $sender to new stream $newStream")
                    newStream.mutableSenders.add(sender)
                }
            }
            addStream(newStream)
        }

        override fun onAddSender(mediaChannel: SoraMediaChannel, sender: RtpSender, ms: Array<out org.webrtc.MediaStream>) {
            SoraLogger.d(TAG, "onAddSender")
            for (nativeStream in ms) {
                val stream = getStream(nativeStream)
                SoraLogger.d(TAG, "add sender $sender to stream $stream")
                stream?.basicAddSender(sender)
            }
        }

        override fun onAddReceiver(mediaChannel: SoraMediaChannel, receiver: RtpReceiver, ms: Array<out org.webrtc.MediaStream>) {
            SoraLogger.d(TAG, "onAddReceiver")
            for (nativeStream in ms) {
                val stream = getStream(nativeStream)
                stream?.basicAddReceiver(receiver)
            }
        }

        override fun onRemoveReceiver(mediaChannel: SoraMediaChannel, id: String) {
            SoraLogger.d(TAG, "onRemoveReceiver")
            /*
            val stream = getStream(id)
            if (stream != null) {
                stream!!.receiver = null
            }
             */
        }

        override fun onPushMessage(mediaChannel: SoraMediaChannel, push: PushMessage) {
            SoraLogger.d(TAG, "onPushMessage: push=${push}")
        }

    }

}
