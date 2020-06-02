package jp.shiguredo.sora.sdk.channel

import android.content.Context
import org.webrtc.MediaStream

class MediaChannel @JvmOverloads constructor(
        private val context: Context,
        val configuration: Configuration
) {

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 10L
    }

    private var onConnect: ((Throwable?) -> Unit)? = null

    private var onAddLocalStreamHandler: (stream: MediaStream) -> Unit = {}
    private var onAddRemoteStreamHandler: (stream: MediaStream) -> Unit = {}
    private var onRemoveRemoteStreamHandler: (label: String) -> Unit = {}

    fun onAddLocalStream(handler: (stream: MediaStream) -> Unit) {
        onAddLocalStreamHandler = handler
    }

    fun onAddRemoteStream(handler: (stream: MediaStream) -> Unit) {
        onAddRemoteStreamHandler = handler
    }

    fun onRemoveRemoteStream(handler: (label: String) -> Unit) {
        onRemoveRemoteStreamHandler = handler
    }

    internal fun connect(timeout: Long = DEFAULT_TIMEOUT_SECONDS,
                         completionHandler: (Throwable?) -> Unit) {
        onConnect = completionHandler

    }

}