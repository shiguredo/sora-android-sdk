package jp.shiguredo.sora.sdk.channel.option

import android.graphics.Point

/**
 * 映像に関するオプションをまとめるクラスです
 */
class SoraVideoOption {

    /**
     * 利用できる映像コーデックを示します
     */
    enum class Codec {
        /** H.264 */
        H264,
        /** VP8 */
        VP8,
        /** VP9 */
        VP9
    }

    /**
     * 映像のフレームサイズをまとめるクラスです
     */
    class FrameSize {

        /**
         * ランドスケープモードで利用できるフレームサイズを示します
         */
        class Landscape {

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

            }
        }

        /**
         * ポートレートモードで利用できるフレームサイズを示します
         */
        class Portrait {

            companion object {

                /** QQVGA 120x160 */
                val QQVGA = Point(120, 160)
                /** QCIF  144x176 */
                val QCIF  = Point(144, 176)
                /** HQVGA 160x240 */
                val HQVGA = Point(160, 240)
                /** QVGA  240x320 */
                val QVGA  = Point(240, 320)
                /** VGA   480x640 */
                val VGA   = Point(480, 640)
                /** HD    720x1280 */
                val HD    = Point(720, 1280)
                /** FHD   1080x1920 */
                val FHD   = Point(1080, 1920)

            }
        }

    }

}
