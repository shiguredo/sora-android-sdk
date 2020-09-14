package jp.shiguredo.sora.sdk2

import android.util.Size

/**
 * 利用できる映像コーデックです。
 */
enum class VideoCodec {

    /** VP8 */
    VP8,

    /** VP9 */
    VP9,

    /** AV1 */
    AV1,

    /** H.264 */
    H264,

    /** H.265 */
    H265
}

/**
 * 映像フレームのサイズと方向を表します。
 *
 * @property width 幅
 * @property height 高さ
 * @property direction 方向
 */
class VideoFrameSize(var width: Int,
                     var height: Int,
                     var direction: VideoDirection = VideoDirection.LANDSCAPE) {

    /**
     * 定数を提供します。
     */
    companion object {

        /** QQVGA 160x120 */
        val QQVGA = VideoFrameSize(160, 120).rotate()

        /** QCIF  176x144 */
        val QCIF  = VideoFrameSize(176, 144).rotate()

        /** HQVGA 240x160 */
        val HQVGA = VideoFrameSize(240, 160).rotate()

        /** QVGA  320x240 */
        val QVGA  = VideoFrameSize(320, 240).rotate()

        /** VGA   640x480 */
        val VGA   = VideoFrameSize(640, 480).rotate()

        /** HD    1280x720 */
        val HD    = VideoFrameSize(1280, 720).rotate()

        /** FHD   1920x1080 */
        val FHD   = VideoFrameSize(1920, 1080).rotate()

        /** Res3840x1920   3840x1920 */
        val Res3840x1920 = VideoFrameSize(3840, 1920).rotate()

        /** UHD3840x2160   3840x2160 */
        val UHD3840x2160 = VideoFrameSize(3840, 2160).rotate()

        /** UHD4096x2160   4096x2160 */
        val UHD4096x2160 = VideoFrameSize(4096, 2160).rotate()

    }

    /**
     * 映像の方向を回転します。
     *
     * @return 方向を回転した映像フレーム
     */
    fun rotate(): VideoFrameSize =
        VideoFrameSize(height, width, direction.rotate())

    /**
     * 映像の方向に応じたサイズを返します。
     * 映像の方向が指定された方向と異なる場合、幅と高さを逆にしたサイズを返します。
     *
     * @param direction 映像の方向
     * @return サイズ
     */
    fun toSize(direction: VideoDirection): Size {
        return when (this.direction) {
            direction -> Size(width, height)
            else -> Size(height, width)
        }
    }

}

/**
 * 映像の方向です。
 */
enum class VideoDirection {
    /**
     * ポートレート
     */
    PORTRAIT,

    /**
     * ランドスケープ
     */
    LANDSCAPE;

    /**
     * 映像の方向を回転します。
     *
     * @return 回転後の方向
     */
    fun rotate(): VideoDirection {
        return when (this) {
            PORTRAIT -> LANDSCAPE
            LANDSCAPE -> PORTRAIT
        }
    }

}
