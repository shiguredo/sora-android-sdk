package jp.shiguredo.sora.sdk.channel.option

import android.graphics.Point

class SoraVideoOption {

    enum class Codec {
        H264,
        VP8,
        VP9
    }

    class FrameSize {

        class Landscape {

            companion object {

                val QQVGA = Point(160, 120)
                val QCIF  = Point(176, 144)
                val HQVGA = Point(240, 160)
                val QVGA  = Point(320, 240)
                val VGA   = Point(640, 480)
                val HD    = Point(1280, 720)
                val FHD   = Point(1920, 1080)

            }
        }

        class Portrait {

            companion object {

                val QQVGA = Point(120, 160)
                val QCIF  = Point(144, 176)
                val HQVGA = Point(160, 240)
                val QVGA  = Point(240, 320)
                val VGA   = Point(480, 640)
                val HD    = Point(720, 1280)
                val FHD   = Point(1080, 1920)

            }
        }

    }

}
