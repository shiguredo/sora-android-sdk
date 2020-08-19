package jp.shiguredo.sora.sdk.ng

import jp.shiguredo.sora.sdk.channel.SoraMediaChannel
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannel
import jp.shiguredo.sora.sdk.channel.signaling.message.PushMessage
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*

class MediaChannel internal constructor(
        val configuration: Configuration
) {

    companion object {
        internal val TAG = MediaChannel::class.simpleName!!
    }

    enum class State {
        READY,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }

    var signalingChannel: SignalingChannel? = null
        internal set

    var state: State = State.READY
        internal set

    private var _streams: MutableList<MediaStream> = mutableListOf()
    val streams: List<MediaStream>
    get() = _streams

    var eglBase: EglBase? = null
    var videoCapturer: VideoCapturer? = null

    private var _onConnect: ((error: Throwable?) -> Unit)? = null
    private var _onDisconnect: (() -> Unit)? = null
    private var _onFailure: ((error: Throwable) -> Unit)? = null
    private var _onAddLocalStream: (stream: MediaStream) -> Unit = {}
    private var _onAddRemoteStream: (stream: MediaStream) -> Unit = {}
    private var _onRemoveRemoteStream: (label: String) -> Unit = {}
    private var _onPush: (message: PushMessage) -> Unit = {}


    private var _basicMediaChannel: SoraMediaChannel? = null
    private var _basicMediaOption: SoraMediaOption? = null

    internal fun connect(completionHandler: (error: Throwable?) -> Unit) {
        state = State.CONNECTING

        _onConnect = completionHandler

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
        _basicMediaChannel!!.disconnect()
        state = State.DISCONNECTED
    }

    fun disconnect() {
        state = State.DISCONNECTING
        basicDisconnect()
        if (_onDisconnect != null) {
            _onDisconnect!!()
        }
    }

    private fun fail(error: Throwable) {
        basicDisconnect()

        when (state) {
            State.CONNECTING ->
                if (_onConnect != null) {
                    _onConnect!!(error)
                }
            else ->
                if (_onFailure != null) {
                    _onFailure!!(error)
                }
        }
    }

    fun onDisconnect(handler: () -> Unit) {
        SoraLogger.d(TAG, "@onDisconnect")
        _onDisconnect = handler
    }

    fun onFailure(handler: (error: Throwable) -> Unit) {
        SoraLogger.d(TAG, "@onFailure")
        _onFailure = handler
    }

    fun onAddLocalStream(handler: (stream: MediaStream) -> Unit) {
        SoraLogger.d(TAG, "@onAddLocalStream")
        _onAddLocalStream = handler
    }

    fun onAddRemoteStream(handler: (stream: MediaStream) -> Unit) {
        SoraLogger.d(TAG, "@onAddRemoteStream")
        _onAddRemoteStream = handler
    }

    fun onRemoveRemoteStream(handler: (label: String) -> Unit) {
        SoraLogger.d(TAG, "@onRemoveRemoteStream")
        _onRemoveRemoteStream = handler
    }

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

            if (_onConnect != null) {
                _onConnect!!(null)
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
                    newStream._senders.add(sender)
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
                /*
                    if (stream != null) {
                        stream!!.sender = sender
                    }
                 */
            }
        }

        override fun onAddReceiver(mediaChannel: SoraMediaChannel, receiver: RtpReceiver, ms: Array<out org.webrtc.MediaStream>) {
            SoraLogger.d(TAG, "onAddReceiver")
            /*
            for (nativeStream in ms) {
                val stream = getStream(nativeStream)
                if (stream != null) {
                    stream!!.receiver = receiver
                }
            }
             */
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
