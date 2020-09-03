package jp.shiguredo.sora.sdk.ng

import org.webrtc.RendererCommon
import org.webrtc.VideoFrame
import org.webrtc.VideoTrack

/**
 * 映像を描画するためのインターフェースです。
 */
interface VideoRenderer {

    /**
     * 映像が反転していれば `true`
     */
    var isMirrored: Boolean

    /**
     * ハードウェア映像スケーラーが有効であれば `true`
     */
    var hardwareScalerEnabled: Boolean

    /**
     * フレームレート制限時のフレームレート。
     * [fpsReductionEnabled] が `true` の場合、最大フレームレートがこの値に抑えられます。
     */
    var fpsReduction: Float

    /**
     * フレームレート制限の可否。
     * `true` をセットすると、最大フレームレートを [fpsReduction] に抑えます。
     */
    var fpsReductionEnabled: Boolean

    /**
     * 映像レンダラーを映像トラックに割り当てます。
     *
     * @param track 映像トラック
     *
     * @see detachFromVideoTrack
     */
    fun attachToVideoTrack(track: VideoTrack)

    /**
     * 映像トラックに割り当てた映像レンダラーを取り外します。
     *
     * @param track 映像トラック
     *
     * @see attachToVideoTrack
     */
    fun detachFromVideoTrack(track: VideoTrack)

    /**
     * 初期化すべきであれば `true` を返します。
     *
     * @return 初期化すべきなら `true`
     */
    fun shouldInitialization(): Boolean

    /**
     * 終了処理をすべきであれば `true` を返します。
     *
     * @return 終了処理をすべきなら `true`
     */
    fun shouldRelease(): Boolean

    /**
     * 映像を描画するための初期化処理を行います。
     *
     * @param videoRenderingContext 描画に使用するコンテキスト
     */
    fun initialize(videoRenderingContext: VideoRenderingContext)

    /**
     * 映像レンダラーの終了処理を行います。
     * 映像レンダラーの使用後に呼ぶ必要があります。
     *
     * @see initialize
     */
    fun release()

    fun setScalingType(scalingTypeMatchOrientation: RendererCommon.ScalingType?,
                       scalingTypeMismatchOrientation: RendererCommon.ScalingType?)

    fun resume()

    fun pause()

    fun clear()

    fun onFrame(frame: VideoFrame?)

    fun onFirstFrameRendered()

    fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int)

}