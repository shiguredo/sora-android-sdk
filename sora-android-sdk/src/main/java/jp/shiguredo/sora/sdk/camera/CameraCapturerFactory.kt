package jp.shiguredo.sora.sdk.camera

import android.content.Context
import android.os.Build
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer

class CameraCapturerFactory {

    companion object {

        fun create(context: Context) : CameraVideoCapturer? {
            var videoCapturer: CameraVideoCapturer? = null
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (Camera2Enumerator.isSupported(context)) {
                    videoCapturer = createCapturer(Camera2Enumerator(context))
                }
            }
            if (videoCapturer == null) {
                videoCapturer = createCapturer(Camera1Enumerator(true))
            }
            return videoCapturer
        }

        private fun createCapturer(enumerator: CameraEnumerator): CameraVideoCapturer? {
            var capturer : CameraVideoCapturer? = null
            enumerator.deviceNames.forEach {
                deviceName ->
                if (capturer == null) {
                    capturer = findDeviceCamera(enumerator, deviceName, true)
                }
            }
            if (capturer != null) {
                return capturer
            }
            enumerator.deviceNames.forEach {
                deviceName ->
                if (capturer == null) {
                    capturer = findDeviceCamera(enumerator, deviceName, false)
                }
            }
            return capturer
        }

        private fun findDeviceCamera(enumerator: CameraEnumerator,
                                     deviceName: String, frontFacing: Boolean) : CameraVideoCapturer? {
            var capturer: CameraVideoCapturer? = null
            if (enumerator.isFrontFacing(deviceName) == frontFacing) {
                capturer = enumerator.createCapturer(deviceName, null)
            }
            return capturer
        }

    }

}

