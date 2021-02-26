package jp.shiguredo.sora.sdk.codec

import org.webrtc.*

internal class SimulcastVideoEncoderFactoryWrapper(sharedContext: EglBase.Context?, enableIntelVp8Encoder: Boolean, enableH264HighProfile: Boolean) : VideoEncoderFactory {

    /*
     * ソフトウェアエンコーダーの利用を優先するファクトリーです。
     * 指定されたコーデックにソフトウェアエンコーダーが対応していない場合にハードウェアエンコーダーを利用します。
     * ただし、このクラスは libwebrtc の問題を回避するためのワークアラウンドで実質的に用途はありません。
     *
     * libwebrtc でサイマルキャストを扱うには SimulcastEncoderAdapter を利用します。
     * SimulcastEncoderAdapter はプライマリのエンコーダーとフォールバックのエンコーダー
     * (指定されたコーデックにプライマリのエンコーダーが対応していない場合に利用されます)
     * を持ちます。プライマリに HardwareVideoEncoderFactory 、
     * フォールバックに SoftwareVideoEncoderFactory を指定すると、
     * H.264 の使用時に libwebrtc がクラッシュします。
     * これは SoftwareVideoEncoderFactory が H.264 に非対応であり、
     * createEncoder() が null を返すのに対して libwebrtc 側が null 時の処理に対応していないためです。
     * createEncoder() はフォールバックの利用の有無に関わらず実行されるので、
     * null を回避するために HardwareVideoEncoderFactory に処理を委譲しています。
     * プライマリ・フォールバックの両方で HardwareVideoEncoderFactory を使うことになりますが、特に問題ありません。
     */
    private class Fallback(private val hardwareVideoEncoderFactory: VideoEncoderFactory) : VideoEncoderFactory {

        private val softwareVideoEncoderFactory: VideoEncoderFactory = SoftwareVideoEncoderFactory()

        override fun createEncoder(info: VideoCodecInfo): VideoEncoder? {
            val softwareEncoder = softwareVideoEncoderFactory.createEncoder(info)
            val hardwareEncoder = hardwareVideoEncoderFactory.createEncoder(info)
            return if (hardwareEncoder != null && softwareEncoder != null) {
                VideoEncoderFallback(hardwareEncoder, softwareEncoder)
            } else {
                softwareEncoder ?: hardwareEncoder
            }
        }

        override fun getSupportedCodecs(): Array<VideoCodecInfo> {
            val supportedCodecInfos: MutableList<VideoCodecInfo> = mutableListOf()
            supportedCodecInfos.addAll(softwareVideoEncoderFactory.supportedCodecs)
            supportedCodecInfos.addAll(hardwareVideoEncoderFactory.supportedCodecs)
            return supportedCodecInfos.toTypedArray()
        }

    }

    private val primary: HardwareVideoEncoderFactory
    private val fallback: Fallback
    private val native: SimulcastVideoEncoderFactory

    init {
        primary = HardwareVideoEncoderFactory(sharedContext, enableIntelVp8Encoder, enableH264HighProfile)
        fallback = Fallback(primary)
        native = SimulcastVideoEncoderFactory(primary, fallback)
    }

    override fun createEncoder(info: VideoCodecInfo?): VideoEncoder? {
        return native.createEncoder(info)
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        return native.supportedCodecs
    }

}