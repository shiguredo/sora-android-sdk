package jp.shiguredo.sora.sdk.ng

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import jp.shiguredo.sora.sdk.camera.CameraCapturerFactory
import jp.shiguredo.sora.sdk.channel.SoraMediaChannel
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannel
import jp.shiguredo.sora.sdk.channel.signaling.message.PushMessage
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.ng.MediaStream
import jp.shiguredo.sora.sdk.ng.MediaStreamTrack
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*

class MediaChannel @JvmOverloads internal constructor(
        val configuration: Configuration
) {

    companion object {
        private val TAG = MediaChannel::class.simpleName!!
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

    private var _streams: MutableList<MediaStream>
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

    init {
        _streams = mutableListOf()
    }

    internal fun connect(completionHandler: (error: Throwable?) -> Unit) {
        state = State.CONNECTING

        _onConnect = completionHandler

        configuration.printDebug(TAG, "connect")

        _basicMediaOption = configuration.toSoraMediaOption()
        _basicMediaChannel = SoraMediaChannel(
                context           = configuration.context,
                signalingEndpoint = configuration.url.toString(),
                channelId         = configuration.channelId,
                mediaOption       = _basicMediaOption!!,
                listener          = basicMediaChannelListner)
        _basicMediaChannel!!.connect()


        /*
        // Configuration
        if (videoEnabled) {
            if (role == Role.SEND || role == Role.SENDRECV) {
                videoCapturer = CameraCapturerFactory.create(context)
                if (videoSendEglBaseContext == null) {
                    videoSendEglBaseContext = eglBase.eglBaseContext
                }
            } else if (role == Role.RECV) {
                if (videoRecvEglBaseContext == null) {
                    videoRecvEglBaseContext = eglBase.eglBaseContext
                }
            }
        }
         */
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
        _onDisconnect = handler
    }

    fun onFailure(handler: (error: Throwable) -> Unit) {
        _onFailure = handler
    }

    fun onAddLocalStream(handler: (stream: MediaStream) -> Unit) {
        _onAddLocalStream = handler
    }

    fun onAddRemoteStream(handler: (stream: MediaStream) -> Unit) {
        _onAddRemoteStream = handler
    }

    fun onRemoveRemoteStream(handler: (label: String) -> Unit) {
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

    private fun getStream(id: String): MediaStream? {
        for (stream in _streams) {
            if (stream.id == id) {
                return stream
            }
        }
        return null
    }

    private val basicMediaChannelListner = object : SoraMediaChannel.Listener {

        override fun onConnect(mediaChannel: SoraMediaChannel) {
            Log.d(TAG, "onConnect")
            state = State.CONNECTED
            if (_onConnect != null) {
                _onConnect!!(null)
            }
        }

        override fun onClose(mediaChannel: SoraMediaChannel) {
            Log.d(TAG, "onClose")
            disconnect()
        }

        override fun onError(mediaChannel: SoraMediaChannel, reason: SoraErrorReason) {
            Log.d(TAG, "onError [$reason]")
            fail(SoraError(SoraError.Kind.fromReason(reason)))
        }

        override fun onError(mediaChannel: SoraMediaChannel, reason: SoraErrorReason, message: String) {
            SoraLogger.d(TAG, "onError [$reason]: $message")
            fail(SoraError(SoraError.Kind.fromReason(reason)))
        }

        override fun onAddRemoteStream(mediaChannel: SoraMediaChannel, ms: org.webrtc.MediaStream) {
            Log.d(TAG, "onAddRemoteStream")

            val newStream = MediaStream(this@MediaChannel, ms)
            addStream(newStream)
            if (_onAddRemoteStream != null) {
                _onAddRemoteStream!!(newStream)
            }
        }

        override fun onAddLocalStream(mediaChannel: SoraMediaChannel, ms: org.webrtc.MediaStream) {
            Log.d(TAG, "onAddLocalStream")

            val newStream = MediaStream(this@MediaChannel, ms)
            addStream(newStream)
        }

        override fun onAddSender(mediaChannel: SoraMediaChannel, sender: RtpSender, ms: Array<out org.webrtc.MediaStream>) {
            Log.d(TAG, "onAddSender")
            for (nativeStream in ms) {
                val stream = getStream(nativeStream)
                if (stream != null) {
                    stream!!.sender = sender
                }
            }
        }

        override fun onAddReceiver(mediaChannel: SoraMediaChannel, receiver: RtpReceiver, ms: Array<out org.webrtc.MediaStream>) {
            Log.d(TAG, "onAddReceiver")
            for (nativeStream in ms) {
                val stream = getStream(nativeStream)
                if (stream != null) {
                    stream!!.receiver = receiver
                }
            }
        }

        override fun onRemoveReceiver(mediaChannel: SoraMediaChannel, id: String) {
            Log.d(TAG, "onRemoveReceiver")
            val stream = getStream(id)
            if (stream != null) {
                stream!!.receiver = null
            }
        }

        override fun onPushMessage(mediaChannel: SoraMediaChannel, push: PushMessage) {
            Log.d(TAG, "onPushMessage: push=${push}")
        }

    }

}