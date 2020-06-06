package jp.shiguredo.sora.sdk.ng

import android.content.Context
import jp.shiguredo.sora.sdk.camera.CameraCapturerFactory
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannel
import org.webrtc.PeerConnection
import jp.shiguredo.sora.sdk.ng.MediaStream
import jp.shiguredo.sora.sdk.ng.MediaStreamTrack
import org.webrtc.EglBase
import org.webrtc.RtpSender
import org.webrtc.VideoCapturer

class MediaChannel @JvmOverloads internal constructor(
        private val context: Context,
        val configuration: Configuration
) {

    enum class State {
        READY,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }

    var nativePeerConnection: PeerConnection? = null
        internal set

    var signalingChannel: SignalingChannel? = null
        internal set

    var state: State = State.READY
        internal set

    private var _streams: MutableList<MediaStream>
    val streams: List<MediaStream>
    get() = _streams

    var sender: RtpSender? = null
        internal set

    var eglBase: EglBase? = null
    var videoCapturer: VideoCapturer? = null

    private var onConnect: ((Throwable?) -> Unit)? = null
    private var _onDisconnect: ((Throwable?) -> Unit)? = null
    private var onAddLocalStreamHandler: (stream: MediaStream) -> Unit = {}
    private var onAddRemoteStreamHandler: (stream: MediaStream) -> Unit = {}
    private var onRemoveRemoteStreamHandler: (label: String) -> Unit = {}

    init {
        _streams = mutableListOf()
    }

    internal fun connect(completionHandler: (Throwable?) -> Unit) {
        onConnect = completionHandler

        eglBase = configuration.eglBase
        if (eglBase == null) {
            eglBase = EglBase.create()
            if (eglBase == null) {
                // TODO: エラー
            }
        }

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

    fun onDisconnect(completionHandler: (Throwable?) -> Unit) {
        _onDisconnect = completionHandler
    }

    fun onAddLocalStream(handler: (stream: MediaStream) -> Unit) {
        onAddLocalStreamHandler = handler
    }

    fun onAddRemoteStream(handler: (stream: MediaStream) -> Unit) {
        onAddRemoteStreamHandler = handler
    }

    fun onRemoveRemoteStream(handler: (label: String) -> Unit) {
        onRemoveRemoteStreamHandler = handler
    }

    fun addStream(stream: MediaStream) {
        _streams.add(stream)
    }

    // TODO: Android SDK の Public API はストリームベースとする
    /*
    fun addTrack(track: MediaStreamTrack) {

    }
     */

}