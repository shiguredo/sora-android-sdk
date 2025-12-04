package jp.shiguredo.sora.sdk.channel.rtc

import android.content.Context
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption.SoraCameraConfig
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.UUID

class RTCLocalVideoManager(
    private val capturer: VideoCapturer,
    private val cameraConfig: SoraCameraConfig?,
) {
    companion object {
        private val TAG = RTCLocalVideoManager::class.simpleName
    }

    var source: VideoSource? = null
    var track: VideoTrack? = null
    var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var isOwnedCapturer: Boolean = true

    fun initTrack(
        factory: PeerConnectionFactory,
        eglContext: EglBase.Context?,
        appContext: Context,
    ) {
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

        if (isOwnedCapturer) {
            capturer.stopCapture()
            capturer.dispose()
        }
    }

    /**
     * SDK 内で生成・管理しているキャプチャの場合にカメラの映像取得を開始します.
     */
    internal fun startOwnedCapture() {
        // 内部生成した VideoCapturer の場合のみ開始する
        // 外部から渡された VideoCapturer の場合、キャプチャの開始は SDK 利用者の責任とする
        if (!isOwnedCapturer) {
            return
        }

        cameraConfig?.let { config ->
            // Sora 接続開始時のハードミュートが有効な場合、キャプチャを開始しない
            if (config.initialVideoHardMute) {
                SoraLogger.d(TAG, "startCaptureIfOwned: initialVideoHardMute is true, skip startCapture")
                return
            }
            capturer.startCapture(config.width, config.height, config.frameRate)
            SoraLogger.d(TAG, "started capture: ${config.width}x${config.height} @ ${config.frameRate}fps")
        } ?: throw IllegalStateException("cameraConfig is null, cannot start capture")
    }

    internal fun startVideoCapture() {
        cameraConfig?.let { config ->
            capturer.startCapture(config.width, config.height, config.frameRate)
        } ?: throw IllegalStateException("cameraConfig is null, cannot start capture")
    }

    internal fun stopVideoCapture() {
        capturer.stopCapture()
    }

    internal fun setTrackEnabled(enabled: Boolean) {
        track?.setEnabled(enabled)
    }

    internal fun switchCamera(handler: CameraVideoCapturer.CameraSwitchHandler?) {
        // CameraVideoCapturer の場合のみスイッチを実行
        val cameraCapturer = capturer as? CameraVideoCapturer
        // TODO(zztkm): CameraVideoCapturer でない場合は例外を投げるべきか検討する
        cameraCapturer?.switchCamera(handler)
    }

    /**
     * キャプチャフォーマットを変更します。
     * RTCLocalVideoManager 内で管理している SoraCameraConfig も併せて更新します。
     *
     * @param width 幅
     * @param height 高さ
     * @param frameRate フレームレート
     */
    internal fun changeCaptureFormat(
        width: Int,
        height: Int,
        frameRate: Int,
    ) {
        capturer.changeCaptureFormat(width, height, frameRate)
        // changeCaptureFormat 以降で startCapture を実効するとき用に設定を更新する
        cameraConfig?.let { config ->
            config.width = width
            config.height = height
            config.frameRate = frameRate
        }
    }

    /**
     * 内部生成されたキャプチャーであることをマークします。
     */
    internal fun markAsOwnedCapturer() {
        isOwnedCapturer = true
    }
}
