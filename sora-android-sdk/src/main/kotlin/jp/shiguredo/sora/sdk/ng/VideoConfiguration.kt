package jp.shiguredo.sora.sdk.ng

import android.graphics.Point

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
class VideoFrameSize {

    companion object {

        /** QQVGA 160x120 */
        val QQVGA = Point(160, 120)
        /** QCIF  176x144 */
        val QCIF  = Point(176, 144)
        /** HQVGA 240x160 */
        val HQVGA = Point(240, 160)
        /** QVGA  320x240 */
        val QVGA  = Point(320, 240)
        /** VGA   640x480 */
        val VGA   = Point(640, 480)
        /** HD    1280x720 */
        val HD    = Point(1280, 720)
        /** FHD   1920x1080 */
        val FHD   = Point(1920, 1080)
        /** Res3840x1920   3840x1920 */
        val Res3840x1920 = Point(3840, 1920)
        /** UHD3840x2160   3840x2160 */
        val UHD3840x2160 = Point(3840, 2160)
        /** UHD4096x2160   4096x2160 */
        val UHD4096x2160 = Point(4096, 2160)

    }

}

enum class VideoDirection {
    PORTRAIT,
    LANDSCAPE
}
