package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*
import java.util.*

interface RTCLocalVideoManager {
    fun initTrack(factory: PeerConnectionFactory)
    fun attachTrackToStream(stream: MediaStream)
    fun dispose()
}

// just for Null-Object-Pattern
class RTCNullLocalVideoManager: RTCLocalVideoManager {
    override fun initTrack(factory: PeerConnectionFactory) {}
    override fun attachTrackToStream(stream: MediaStream) {}
    override fun dispose() {}
}

class RTCLocalVideoManagerImpl(private val capturer: VideoCapturer): RTCLocalVideoManager {

    val TAG = RTCLocalVideoManagerImpl::class.simpleName

    var source: VideoSource? = null
    var track:  VideoTrack?  = null

    override fun initTrack(factory: PeerConnectionFactory) {
        SoraLogger.d(TAG, "initTask")
        source = factory.createVideoSource(capturer)
        val trackId = UUID.randomUUID().toString()
        track = factory.createVideoTrack(trackId, source)
        track!!.setEnabled(true)
    }

    override fun attachTrackToStream(stream: MediaStream) {
        SoraLogger.d(TAG, "attachTrackToStream")
        track?.let{ stream.addTrack(it) }
    }

    override fun dispose() {
        SoraLogger.d(TAG, "dispose")
        SoraLogger.d(TAG, "disable track")
        capturer.dispose()
        SoraLogger.d(TAG, "source.dispose")
        source?.dispose()
    }
}

