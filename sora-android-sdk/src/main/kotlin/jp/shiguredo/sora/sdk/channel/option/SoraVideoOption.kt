package jp.shiguredo.sora.sdk.channel.option

import android.graphics.Point
import org.webrtc.RtpParameters

/**
 * 映像に関するオプションをまとめるクラスです.
 */
class SoraVideoOption {
    // TODO(zztkm): 破壊的変更にはなるが、Codec の並び順を Sora のドキュメントに合わせて変更する

    /**
     * 利用できる映像コーデックを示します.
     */
    enum class Codec {
        /** H.264 */
        H264,

        /** H.265 */
        H265,

        /** VP8 */
        VP8,

        /** VP9 */
        VP9,

        /** AV1 */
        AV1,

        /** Sora のデフォルト値を利用 */
        DEFAULT,
    }

    /**
     * 映像のフレームサイズをまとめるクラスです.
     */
    class FrameSize {
        /**
         * ランドスケープモードで利用できるフレームサイズを示します.
         */
        class Landscape {
            companion object {
                /** QQVGA 160x120 */
                val QQVGA = Point(160, 120)

                /** QCIF  176x144 */
                val QCIF = Point(176, 144)

                /** HQVGA 240x160 */
                val HQVGA = Point(240, 160)

                /** QVGA  320x240 */
                val QVGA = Point(320, 240)

                /** VGA   640x480 */
                val VGA = Point(640, 480)

                /** qHD   960x540 */
                val qHD = Point(960, 540)

                /** HD    1280x720 */
                val HD = Point(1280, 720)

                /** FHD   1920x1080 */
                val FHD = Point(1920, 1080)

                /** Res3840x1920   3840x1920 */
                val Res3840x1920 = Point(3840, 1920)

                /** UHD3840x2160   3840x2160 */
                val UHD3840x2160 = Point(3840, 2160)

                /** UHD4096x2160   4096x2160 */
                val UHD4096x2160 = Point(4096, 2160)
            }
        }

        /**
         * ポートレートモードで利用できるフレームサイズを示します.
         */
        class Portrait {
            companion object {
                /** QQVGA 120x160 */
                val QQVGA = Point(120, 160)

                /** QCIF  144x176 */
                val QCIF = Point(144, 176)

                /** HQVGA 160x240 */
                val HQVGA = Point(160, 240)

                /** QVGA  240x320 */
                val QVGA = Point(240, 320)

                /** VGA   480x640 */
                val VGA = Point(480, 640)

                /** qHD   540x960 */
                val qHD = Point(540, 960)

                /** HD    720x1280 */
                val HD = Point(720, 1280)

                /** FHD   1080x1920 */
                val FHD = Point(1080, 1920)

                /** Res1920x3840   1920x3840 */
                val Res1920x3840 = Point(1920, 3840)

                /** UHD2160x3840   2160x3840 */
                val UHD2160x3840 = Point(2160, 3840)

                /** UHD2160x4096   2160x4096 */
                val UHD2160x4096 = Point(2160, 4096)
            }
        }
    }

    enum class SimulcastRid(
        private val value: String,
    ) {
        /**
         * r0
         */
        R0("r0"),

        /**
         * r1
         */
        R1("r1"),

        /**
         * r2
         */
        R2("r2"),
        ;

        override fun toString(): String = value
    }

    enum class SpotlightRid(
        private val value: String,
    ) {
        /**
         * none
         */
        NONE("none"),

        /**
         * r0
         */
        R0("r0"),

        /**
         * r1
         */
        R1("r1"),

        /**
         * r2
         */
        R2("r2"),
        ;

        override fun toString(): String = value
    }

    enum class ResolutionAdjustment(
        val value: UInt,
    ) {
        /**
         * 解像度を調整しない
         */
        NONE(1u),

        /**
         * 解像度が2の倍数になるように調整する
         */
        MULTIPLE_OF_2(2u),

        /**
         * 解像度が4の倍数になるように調整する
         */
        MULTIPLE_OF_4(4u),

        /**
         * 解像度が8の倍数になるように調整する
         */
        MULTIPLE_OF_8(8u),

        /**
         * 解像度が16の倍数になるように調整する
         */
        MULTIPLE_OF_16(16u),
    }

    /**
     * (リソースの逼迫により) 送信する映像の品質が維持できない場合の挙動を示します.
     *
     * WebRTC の RtpParameters.DegradationPreference に対応します.
     * 映像エンコーダーがCPUやネットワーク帯域の制限に直面した際の振る舞いを制御します.
     */
    enum class DegradationPreference(
        val nativeValue: RtpParameters.DegradationPreference,
    ) {
        /**
         * 品質調整を無効にします.
         * 解像度とフレームレートの両方を維持しようとします.
         */
        DISABLED(RtpParameters.DegradationPreference.DISABLED),

        /**
         * フレームレートの維持を優先します.
         * 必要に応じて解像度を下げます.
         */
        MAINTAIN_FRAMERATE(RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE),

        /**
         * 解像度の維持を優先します.
         * 必要に応じてフレームレートを下げます.
         */
        MAINTAIN_RESOLUTION(RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION),

        /**
         * バランスを取ります.
         * 解像度とフレームレートの両方を適応的に調整します.
         */
        BALANCED(RtpParameters.DegradationPreference.BALANCED),
    }
}
