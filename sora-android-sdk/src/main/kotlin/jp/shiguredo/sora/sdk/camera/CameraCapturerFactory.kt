package jp.shiguredo.sora.sdk.camera

import android.content.Context
import android.os.Build
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer

/**
 * カメラからの映像を取得するための `CameraVideoCapturer` のファクトリクラスです.
 *
 * Camera1, Camera2 を統一的に扱うことが出来ます.
 * cf.
 * - `org.webrtc.CameraVideoCapturer`
 */
class CameraCapturerFactory {

    companion object {
        val TAG = CameraCapturerFactory::class.simpleName

        /**
         * `CameraVideoCapturer` のインスタンスを生成します.
         *
         * 複数のカメラがある場合はフロントのカメラを優先します.
         *
         * @param context application context
         * @param fixedResolution true の場合は解像度維持を優先、false の場合は
         * フレームレート維持を優先する. デフォルト値は false.
         * @param frontFacingFirst true の場合はフロントカメラを優先、false の場合は
         * リアカメラを優先して選択する. デフォルト値は true.
         * @return 生成された `CameraVideoCapturer`
         */
        @JvmOverloads
        fun create(context: Context,
                   fixedResolution: Boolean = false,
                   frontFacingFirst: Boolean = true) : CameraVideoCapturer? {
            SoraLogger.d(TAG, "create camera capturer")
            var videoCapturer: CameraVideoCapturer? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (Camera2Enumerator.isSupported(context)) {
                    SoraLogger.d(TAG, "create camera capturer: Camera2Enumerator")
                    videoCapturer = createCapturer(Camera2Enumerator(context), frontFacingFirst)
                }
            }
            if (videoCapturer == null) {
                SoraLogger.d(TAG, "create camera capturer: Camera1Enumerator")
                videoCapturer = createCapturer(Camera1Enumerator(true), frontFacingFirst)
            }

            if (videoCapturer == null) {
                SoraLogger.d(TAG, "cannot create camera capturer")
                return null
            }

            SoraLogger.d(TAG, "camera capturer => $videoCapturer")
            return when (videoCapturer.isScreencast) {
                fixedResolution -> {
                    videoCapturer
                }
                else -> {
                    SoraLogger.d(TAG, "Wrap capturer: original.isScreencast=${videoCapturer.isScreencast}, fixedResolution=${fixedResolution}")
                    CameraVideoCapturerWrapper(videoCapturer, fixedResolution)
                }
            }
        }

        private fun createCapturer(enumerator: CameraEnumerator, frontFacingFirst: Boolean): CameraVideoCapturer? {
            var capturer : CameraVideoCapturer? = null
            enumerator.deviceNames.forEach {
                deviceName ->
                if (capturer == null) {
                    capturer = findDeviceCamera(enumerator, deviceName, frontFacingFirst)
                }
            }
            if (capturer != null) {
                return capturer
            }
            enumerator.deviceNames.forEach {
                deviceName ->
                if (capturer == null) {
                    capturer = findDeviceCamera(enumerator, deviceName, !frontFacingFirst)
                }
            }
            return capturer
        }

        private fun findDeviceCamera(enumerator: CameraEnumerator,
                                     deviceName: String,
                                     frontFacing: Boolean) : CameraVideoCapturer? {
            var capturer: CameraVideoCapturer? = null
            if (enumerator.isFrontFacing(deviceName) == frontFacing) {
                capturer = enumerator.createCapturer(deviceName, null)
            }
            return capturer
        }

    }

}

