package jp.shiguredo.sora.sdk.ng

import android.graphics.Point
import android.provider.MediaStore
import android.util.Size
import jp.shiguredo.sora.sdk.channel.option.SoraVideoOption

/**
 * 利用できる映像コーデックを示します
 */
enum class VideoCodec {

    /** VP8 */
    VP8,
    /** VP9 */
    VP9,
    AV1,
    /** H.264 */
    H264,
    H265
}

/**
 * 映像のフレームサイズをまとめるクラスです
 */
class VideoFrameSize(var width: Int,
                     var height: Int,
                     var direction: VideoDirection = VideoDirection.LANDSCAPE) {

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

    fun rotate(): VideoFrameSize =
        VideoFrameSize(height, width, direction.rotate())

    fun toSize(direction: VideoDirection): Size {
        return when (this.direction) {
            direction -> Size(width, height)
            else -> Size(height, width)
        }
    }

}

enum class VideoDirection {
    PORTRAIT,
    LANDSCAPE;

    fun rotate(): VideoDirection {
        return when (this) {
            PORTRAIT -> LANDSCAPE
            LANDSCAPE -> PORTRAIT
        }
    }

}
