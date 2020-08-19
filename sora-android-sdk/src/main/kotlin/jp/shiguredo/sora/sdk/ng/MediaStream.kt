package jp.shiguredo.sora.sdk.ng

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*

class MediaStream internal constructor(val mediaChannel: MediaChannel,
                                       val nativeStream: org.webrtc.MediaStream,
                                       val videoSource: VideoSource?) {

    companion object {
        internal val TAG = MediaStream::class.simpleName!!
    }

    val id: String
    get() = nativeStream.id

    private var _senders: MutableList<RtpSender> = mutableListOf()

    val senders: List<RtpSender>
        get() = _senders

    private var _receivers: MutableList<RtpReceiver> = mutableListOf()

    val receivers: List<RtpReceiver>
        get() = _receivers

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

    internal var videoFilterAdapter = VideoFilterAdapter(this)

    val videoFilters: List<VideoFilter>
        get() = videoFilterAdapter.filters

    private var _videoRendererAdapter = VideoRendererAdapter(this)

    init {
        SoraLogger.d(TAG, "video tracks => ${nativeStream.videoTracks}")
        SoraLogger.d(TAG, "audio tracks => ${nativeStream.audioTracks}")
        for (track in nativeStream.videoTracks) {
            SoraLogger.d(TAG, "add sink => $track")
            track.addSink(_videoRendererAdapter)
        }

        videoSource?.setVideoProcessor(videoFilterAdapter)
    }

    var videoRenderer: VideoRenderer? = null
        set(value) {
            field = value
            if (value != null)
                setVideoRenderer(value, mediaChannel.configuration.sharedVideoRenderingContext)
        }

    fun setVideoRenderer(newRenderer: VideoRenderer,
                         videoRenderingContext: VideoRenderingContext) {
        SoraLogger.d(TAG, "set video renderer => $newRenderer")
        removeVideoRenderer()

        if (mediaChannel.configuration.videoRendererLifecycleManagementEnabled &&
                newRenderer.shouldInitialization()) {
            SoraLogger.d(TAG, "initialize video renderer => $videoRenderingContext")
            newRenderer.initialize(videoRenderingContext)
        }
        _videoRendererAdapter.videoRenderer = newRenderer

        for (sender in _senders) {
            (sender.track() as? VideoTrack)?.let {
                SoraLogger.d(TAG, "attach to video track => $it")
                newRenderer.attachToVideoTrack(it)
            }
        }
    }

    fun removeVideoRenderer() {
        _videoRendererAdapter.videoRenderer?.let { renderer ->
            if (mediaChannel.configuration.videoRendererLifecycleManagementEnabled &&
                    renderer.shouldRelease()) {
                renderer.release()
            }

            for (sender in _senders) {
                (sender.track() as? VideoTrack)?.let { track ->
                    SoraLogger.d(TAG, "detach from video track => $track")
                    renderer.detachFromVideoTrack(track)
                }
            }
        }

        _videoRendererAdapter.videoRenderer = null
    }

    fun addVideoFilter(filter: VideoFilter) {
        videoFilterAdapter.addFilter(filter)
    }

    fun removeVideoFilter(filter: VideoFilter) {
        videoFilterAdapter.removeFilter(filter)
    }

    internal fun basicAddSender(sender: RtpSender) {
        _senders.add(sender)
    }

    internal fun basicAddReceiver(receiver: RtpReceiver) {
        _receivers.add(receiver)
    }

    internal fun close() {
        removeVideoRenderer()

        for (sender in _senders) {
            sender.dispose()
        }
        _senders = mutableListOf()

        for (receiver in _receivers) {
            receiver.dispose()
        }
        _receivers = mutableListOf()

        nativeStream.dispose()
    }

}