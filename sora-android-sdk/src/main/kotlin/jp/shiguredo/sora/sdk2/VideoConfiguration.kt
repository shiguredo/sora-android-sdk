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
                     var direction: VideoDirection) {

    /**
     * 定数を提供します。
     */
    companion object {

        /** QQVGA 160x120 */
        val QQVGA = VideoFrameSize(160, 120, VideoDirection.LANDSCAPE)

        /** QCIF  176x144 */
        val QCIF  = VideoFrameSize(176, 144, VideoDirection.LANDSCAPE)

        /** HQVGA 240x160 */
        val HQVGA = VideoFrameSize(240, 160, VideoDirection.LANDSCAPE)

        /** QVGA  320x240 */
        val QVGA  = VideoFrameSize(320, 240, VideoDirection.LANDSCAPE)

        /** VGA   640x480 */
        val VGA   = VideoFrameSize(640, 480, VideoDirection.LANDSCAPE)

        /** HD    1280x720 */
        val HD    = VideoFrameSize(1280, 720, VideoDirection.LANDSCAPE)

        /** FHD   1920x1080 */
        val FHD   = VideoFrameSize(1920, 1080, VideoDirection.LANDSCAPE)

        /** Res3840x1920   3840x1920 */
        val Res3840x1920 = VideoFrameSize(3840, 1920, VideoDirection.LANDSCAPE)

        /** UHD3840x2160   3840x2160 */
        val UHD3840x2160 = VideoFrameSize(3840, 2160, VideoDirection.LANDSCAPE)

        /** UHD4096x2160   4096x2160 */
        val UHD4096x2160 = VideoFrameSize(4096, 2160, VideoDirection.LANDSCAPE)

    }

    /**
     * 映像の方向を回転します。
     *
     * @param direction 映像の方向。 null の場合は 90 度回転します。
     * @return 方向を回転した映像フレーム
     */
    fun rotate(direction: VideoDirection? = null): VideoFrameSize {
        return if (direction == null) {
            VideoFrameSize(height, width, this.direction.rotated)
        } else {
            when (this.direction) {
                direction -> this
                else -> VideoFrameSize(height, width, this.direction.rotated)
            }
        }
    }

    /**
     * ポートレートに回転したサイズ
     */
    val portrate: VideoFrameSize
        get() = rotate(VideoDirection.PORTRAIT)

    /**
     * ランドスケープに回転したサイズ
     */
    val landscape: VideoFrameSize
        get() = rotate(VideoDirection.LANDSCAPE)

    /**
     * 映像のサイズ
     */
    val size: Size
        get() = Size(width, height)

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
    val rotated: VideoDirection
        get() = when (this) {
            PORTRAIT -> LANDSCAPE
            LANDSCAPE -> PORTRAIT
        }

}
