package jp.shiguredo.sora.sdk.codec

import jp.shiguredo.sora.sdk.channel.option.SoraVideoOption
import org.webrtc.EglBase
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoEncoder
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoEncoderFallback

internal class SoraDefaultVideoEncoderFactory(
    eglContext: EglBase.Context?,
    enableIntelVp8Encoder: Boolean = true,
    enableH264HighProfile: Boolean = false,
    resolutionAdjustment: SoraVideoOption.ResolutionAdjustment,
    private val softwareOnly: Boolean = false,
) : VideoEncoderFactory {
    private val hardwareVideoEncoderFactory: VideoEncoderFactory
    private val softwareVideoEncoderFactory: VideoEncoderFactory = SoftwareVideoEncoderFactory()

    init {
        val defaultFactory = HardwareVideoEncoderFactory(eglContext, enableIntelVp8Encoder, enableH264HighProfile)

        // 解像度の調整が必要な場合、改造した VideoEncoderFactory を利用する
        hardwareVideoEncoderFactory =
            if (resolutionAdjustment == SoraVideoOption.ResolutionAdjustment.NONE) {
                defaultFactory
            } else {
                HardwareVideoEncoderWrapperFactory(defaultFactory, resolutionAdjustment.value)
            }
    }

    override fun createEncoder(info: VideoCodecInfo): VideoEncoder? {
        if (softwareOnly) {
            return softwareVideoEncoderFactory.createEncoder(info)
        }
        val softwareEncoder: VideoEncoder? = softwareVideoEncoderFactory.createEncoder(info)
        val hardwareEncoder: VideoEncoder? = hardwareVideoEncoderFactory.createEncoder(info)
        if (hardwareEncoder != null && softwareEncoder != null) {
            return VideoEncoderFallback(softwareEncoder, hardwareEncoder)
        }
        return hardwareEncoder ?: softwareEncoder
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        if (softwareOnly) {
            return softwareVideoEncoderFactory.supportedCodecs
        }
        val supportedCodecInfos = LinkedHashSet<VideoCodecInfo>()
        supportedCodecInfos.addAll(listOf(*softwareVideoEncoderFactory.supportedCodecs))
        supportedCodecInfos.addAll(listOf(*hardwareVideoEncoderFactory.supportedCodecs))
        return supportedCodecInfos.toTypedArray()
    }
}
