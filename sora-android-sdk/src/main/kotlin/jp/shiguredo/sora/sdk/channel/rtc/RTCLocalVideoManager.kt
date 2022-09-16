package jp.shiguredo.sora.sdk.channel.rtc

import android.content.Context
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.UUID

class RTCLocalVideoManager(private val capturer: VideoCapturer) {

    companion object {
        private val TAG = RTCLocalVideoManager::class.simpleName
    }

    var source: VideoSource? = null
    var track: VideoTrack? = null
    var surfaceTextureHelper: SurfaceTextureHelper? = null

    fun initTrack(factory: PeerConnectionFactory, eglContext: EglBase.Context?, appContext: Context) {
        SoraLogger.d(TAG, "initTrack isScreencast=${capturer.isScreencast}")
        surfaceTextureHelper =
            SurfaceTextureHelper.create("CaptureThread", eglContext)
        source = factory.createVideoSource(capturer.isScreencast)
        capturer.initialize(surfaceTextureHelper, appContext, source!!.capturerObserver)

        val trackId = UUID.randomUUID().toString()
        track = factory.createVideoTrack(trackId, source)
        track?.setEnabled(true)
        SoraLogger.d(TAG, "created track => $trackId, $track")
    }

    fun dispose() {
        SoraLogger.d(TAG, "dispose")
        SoraLogger.d(TAG, "dispose surfaceTextureHelper")
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        SoraLogger.d(TAG, "dispose source")
        source?.dispose()
    }
}
