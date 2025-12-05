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

/**
 * ローカルの映像を管理するクラス.
 *
 * @param capturer 映像キャプチャ
 * @param cameraConfig カメラ設定
 * @param isOwnedCapturer capturer が SDK 内で生成・管理されている場合は true を指定する
 */
class RTCLocalVideoManager(
    private val capturer: VideoCapturer,
    private val cameraConfig: SoraCameraConfig?,
    private val isOwnedCapturer: Boolean = false,
) {
    companion object {
        private val TAG = RTCLocalVideoManager::class.simpleName
    }

    var source: VideoSource? = null
    var track: VideoTrack? = null
    var surfaceTextureHelper: SurfaceTextureHelper? = null

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
        if (isOwnedCapturer) {
            SoraLogger.d(TAG, "dispose owned VideoCapturer")
            /*
              CameraCapturer.dispose() を SurfaceTextureHelper.dispose() より先に呼ぶ理由。

              CameraCapturer は initialize() で SurfaceTextureHelper から
              android.os.Handler を受け取り、cameraThreadHandler にセットしている。

              SurfaceTextureHelper.dispose() を呼ぶと handler.getLooper().quit() が呼ばれ、
              Handler に関連付いた Looper が終了する。

              CameraCapturer.dispose() (stopCapture() を呼ぶだけ) は内部で cameraThreadHandler.post() を呼ぶが、
              事前に SurfaceTextureHelper.dispose() を呼んでいた場合、
              stopCapture() 時点で cameraThreadHandler に関連付く Looper が終了済みのため、cameraThreadHandler.post() の
              メッセージ送信に失敗し、warning ログが出力されてしまう。
              これを防ぐために、。CameraCapturer.dispose() を先に呼ぶ。
             */
            capturer.dispose()
        }

        SoraLogger.d(TAG, "dispose surfaceTextureHelper")
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        SoraLogger.d(TAG, "dispose source")
        source?.dispose()
    }

    /**
     * SDK 内で生成・管理しているキャプチャの場合にカメラの映像取得を開始します.
     */
    internal fun startOwnedCapture() {
        // 内部生成した VideoCapturer の場合のみ開始する
        // 外部から渡された VideoCapturer の場合、キャプチャの開始は SDK 利用者の責任とする
        if (!isOwnedCapturer) {
            SoraLogger.d(TAG, "startOwnedCapture: capturer is not owned, skip startCapture")
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
}
