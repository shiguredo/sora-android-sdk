package jp.shiguredo.sora.sdk.ng

import android.provider.MediaStore
import jp.shiguredo.sora.sdk.Sora
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.MediaStreamTrack
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender

class MediaStream internal constructor(val mediaChannel: MediaChannel,
                                       val nativeStream: org.webrtc.MediaStream) {

    companion object {
        internal val TAG = MediaStream::class.simpleName!!
    }

    /*
    private var _videoTrack: VideoTrack? = null

    var videoTrack: VideoTrack? = null
        set(value) {
            if (_videoTrack != null) {
                nativeStream?.removeTrack(_videoTrack!!.nativeTrack)
            }
            _videoTrack = value
        }

    var audioTrack: AudioTrack? = null
        set(value) {}
     */

    val id: String
    get() = nativeStream.id

    var sender: RtpSender? = null
        internal set

    var receiver: RtpReceiver? = null
        internal set

    internal val videoTracks: List<MediaStreamTrack>
        get() = nativeStream.videoTracks

    internal val audioTracks: List<MediaStreamTrack>
        get() = nativeStream.audioTracks

    internal val allTracks: List<MediaStreamTrack>
        get() = videoTracks + audioTracks

    var isEnabled: Boolean
        get() {
            for (track in allTracks) {
                if (!track.enabled()) {
                    return false
                }
            }
            return true
        }
        set(value) {
            for (track in allTracks) {
                track.setEnabled(value)
            }
        }

    private var _videoRendererAdapter = VideoRendererAdapter()

    init {
        SoraLogger.d(TAG, "video tracks => ${nativeStream.videoTracks}")
        SoraLogger.d(TAG, "audio tracks => ${nativeStream.audioTracks}")
        for (track in nativeStream.videoTracks) {
            SoraLogger.d(TAG, "add sink => $track")
            track.addSink(_videoRendererAdapter)
        }
    }

    fun setVideoRenderer(newRenderer: VideoRenderer,
                         videoRenderingContext: VideoRenderingContext) {
        SoraLogger.d(TAG, "set video renderer => $newRenderer")
        removeVideoRenderer()

        if (mediaChannel.configuration.managesVideoRendererLifecycle &&
                newRenderer.shouldInitialization()) {
            newRenderer.initialize(videoRenderingContext)
        }
        _videoRendererAdapter.videoRenderer = newRenderer
    }

    fun removeVideoRenderer() {
        if (mediaChannel.configuration.managesVideoRendererLifecycle &&
                _videoRendererAdapter.videoRenderer != null &&
                _videoRendererAdapter.videoRenderer!!.shouldRelease()) {
            _videoRendererAdapter.videoRenderer!!.release()
        }
        _videoRendererAdapter.videoRenderer = null
    }

    internal fun close() {
        removeVideoRenderer()

        sender?.track()?.dispose()
        sender = null

        receiver?.track()?.dispose()
        receiver = null

        /*
        videoTrack?.close()
        videoTrack = null

        audioTrack?.close()
        audioTrack = null
         */

        nativeStream.dispose()
    }

}