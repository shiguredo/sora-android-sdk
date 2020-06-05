package jp.shiguredo.sora.sdk.ng

import android.content.Context
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannel
import org.webrtc.PeerConnection
import jp.shiguredo.sora.sdk.ng.MediaStream
import jp.shiguredo.sora.sdk.ng.MediaStreamTrack
import org.webrtc.RtpSender

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
    var signalingChannel: SignalingChannel? = null
    var state: State = State.READY
    var streams: List<MediaStream>
    private var sender: RtpSender? = null

    private var onConnect: ((Throwable?) -> Unit)? = null
    private var _onDisconnect: ((Throwable?) -> Unit)? = null
    private var onAddLocalStreamHandler: (stream: MediaStream) -> Unit = {}
    private var onAddRemoteStreamHandler: (stream: MediaStream) -> Unit = {}
    private var onRemoveRemoteStreamHandler: (label: String) -> Unit = {}

    internal fun connect(completionHandler: (Throwable?) -> Unit) {
        onConnect = completionHandler

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

    fun addTrack(track: MediaStreamTrack) {

    }

}