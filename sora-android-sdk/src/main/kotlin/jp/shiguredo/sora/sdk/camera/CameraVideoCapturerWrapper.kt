package jp.shiguredo.sora.sdk.camera

import android.content.Context
import org.webrtc.CameraVideoCapturer
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper

class CameraVideoCapturerWrapper(private val capturer: CameraVideoCapturer,
                                 private val fixedResolution: Boolean = false): CameraVideoCapturer {
    override fun startCapture(width: Int, height: Int, framerate: Int) {
        capturer.startCapture(width, height, framerate)
    }

    override fun stopCapture() {
        capturer.stopCapture()
    }

    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        capturer.changeCaptureFormat(width, height, framerate)
    }

    override fun switchCamera(handler: CameraVideoCapturer.CameraSwitchHandler?) {
        capturer.switchCamera(handler)
    }

    override fun switchCamera(handler: CameraVideoCapturer.CameraSwitchHandler?, cameraName: String?) {
        capturer.switchCamera(handler, cameraName)
    }

    override fun isScreencast(): Boolean {
        return fixedResolution
    }

    override fun initialize(surfaceTextureHelper: SurfaceTextureHelper?, context: Context?,
                            capturerObserver: CapturerObserver?) {
        capturer.initialize(surfaceTextureHelper, context, capturerObserver)
    }

    override fun dispose() {
        capturer.dispose()
    }
}