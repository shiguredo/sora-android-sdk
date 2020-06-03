package jp.shiguredo.sora.sdk.channel

import android.content.Context
import jp.shiguredo.sora.sdk.channel.signaling.SignalingChannel
import org.webrtc.MediaStream
import org.webrtc.PeerConnection

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

    var peerConnection: PeerConnection? = null
    var signalingChannel: SignalingChannel? = null
    var state: State = State.READY

    private var onConnect: ((Throwable?) -> Unit)? = null
    private var onAddLocalStreamHandler: (stream: MediaStream) -> Unit = {}
    private var onAddRemoteStreamHandler: (stream: MediaStream) -> Unit = {}
    private var onRemoveRemoteStreamHandler: (label: String) -> Unit = {}

    internal fun connect(completionHandler: (Throwable?) -> Unit) {
        onConnect = completionHandler

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

}