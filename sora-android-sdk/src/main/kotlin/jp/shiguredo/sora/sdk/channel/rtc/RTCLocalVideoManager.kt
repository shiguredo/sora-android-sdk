package jp.shiguredo.sora.sdk.channel.rtc

import android.content.Context
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*
import java.util.*
import android.graphics.Matrix
import kotlin.math.absoluteValue

interface RTCLocalVideoManager {
    fun initTrack(factory: PeerConnectionFactory,
                  eglContext: EglBase.Context?,
                  appContext: Context)
    fun attachTrackToStream(stream: MediaStream)
    fun dispose()
}

// just for Null-Object-Pattern
class RTCNullLocalVideoManager: RTCLocalVideoManager {
    override fun initTrack(factory: PeerConnectionFactory,
                           eglContext: EglBase.Context?,
                           appContext: Context) {}
    override fun attachTrackToStream(stream: MediaStream) {}
    override fun dispose() {}
}

class CapturerObserverWrapper(capturerObserver: CapturerObserver): CapturerObserver {
    private val TAG = CapturerObserverWrapper::class.simpleName
    private val capturerObserver = capturerObserver

    override fun onCapturerStarted(succeess: Boolean) {
        capturerObserver.onCapturerStarted(succeess)
    }

    override fun onCapturerStopped() {
        capturerObserver.onCapturerStopped()
    }

    override fun onFrameCaptured(frame: VideoFrame?) {
        SoraLogger.d(TAG, "frame: rotatedWidth=${frame?.rotatedWidth}, rotatedHeight=${frame?.rotatedHeight}, rotation=${frame?.rotation}")

        /*
        frame?.let {
            val buffer = it.buffer

            if (buffer is TextureBufferImpl && frame.rotation != 0) {
                SoraLogger.d(TAG, "buffer: ${buffer}, ${buffer.width}, ${buffer.height}, ${buffer.unscaledWidth}, ${buffer.unscaledHeight}")
                var matrix = Matrix()
                matrix.setRotate(180F) // , (buffer.width/2).toFloat(), (buffer.height/2).toFloat())
                matrix.setScale(buffer.height.toFloat(), buffer.width.toFloat())

                val newBuffer = buffer.applyTransformMatrix(matrix, buffer.width, buffer.height)
                SoraLogger.d(TAG, "newBuffer: ${newBuffer}, ${newBuffer.width}, ${newBuffer.height}, ${newBuffer.unscaledWidth}, ${newBuffer.unscaledHeight}")
                val newFrame = VideoFrame(newBuffer, frame.rotation, frame.timestampNs)
                capturerObserver.onFrameCaptured(newFrame)
            } else {
                SoraLogger.d(TAG, "raw frame")
                capturerObserver.onFrameCaptured(frame)
            }
        }
         */

        capturerObserver.onFrameCaptured(frame)
    }
}

class RTCLocalVideoManagerImpl(private val capturer: VideoCapturer): RTCLocalVideoManager {

    companion object {
        private val TAG = RTCLocalVideoManagerImpl::class.simpleName
    }

    var source: VideoSource? = null
    var track:  VideoTrack?  = null
    var surfaceTextureHelper: SurfaceTextureHelper? = null

    override fun initTrack(factory: PeerConnectionFactory, eglContext: EglBase.Context?, appContext: Context) {
        SoraLogger.d(TAG, "initTrack isScreencast=${capturer.isScreencast}")
        surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread", eglContext);
        source = factory.createVideoSource(capturer.isScreencast);
        capturer.initialize(surfaceTextureHelper, appContext, CapturerObserverWrapper(source!!.capturerObserver));

        val trackId = UUID.randomUUID().toString()
        track = factory.createVideoTrack(trackId, source)
        track!!.setEnabled(true)
        SoraLogger.d(TAG, "created track => $trackId, $track")
    }

    override fun attachTrackToStream(stream: MediaStream) {
        SoraLogger.d(TAG, "attachTrackToStream")
        track?.let{ stream.addTrack(it) }
    }

    override fun dispose() {
        SoraLogger.d(TAG, "dispose")
        SoraLogger.d(TAG, "dispose surfaceTextureHelper")
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        SoraLogger.d(TAG, "dispose source")
        source?.dispose()
    }
}
