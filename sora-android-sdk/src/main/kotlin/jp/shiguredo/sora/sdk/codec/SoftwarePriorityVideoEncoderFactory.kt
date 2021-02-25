package jp.shiguredo.sora.sdk.codec

import org.webrtc.*

/**
 * 映像コーデックのエンコードにソフトウェアエンコーダーを優先して利用します。
 * ソフトウェアエンコーダーが用意されていない映像コーデックに対してのみ、ハードウェアエンコーダーを利用します。
 * サイマルキャストで使われます。
 */
class SoftwarePriorityVideoEncoderFactory(private val hardwareVideoEncoderFactory: VideoEncoderFactory) : VideoEncoderFactory {

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
