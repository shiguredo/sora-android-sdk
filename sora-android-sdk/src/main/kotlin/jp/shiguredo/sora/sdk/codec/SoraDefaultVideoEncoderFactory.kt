package jp.shiguredo.sora.sdk.codec

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
    enableResolutionAdjustment: Boolean,
) : VideoEncoderFactory {
    private val hardwareVideoEncoderFactory: VideoEncoderFactory
    private val softwareVideoEncoderFactory: VideoEncoderFactory = SoftwareVideoEncoderFactory()

    init {
        val defaultFactory = HardwareVideoEncoderFactory(eglContext, enableIntelVp8Encoder, enableH264HighProfile)
        hardwareVideoEncoderFactory = if (enableResolutionAdjustment) {
            HardwareVideoEncoderWrapperFactory(defaultFactory)
        } else {
            defaultFactory
        }
    }

    override fun createEncoder(info: VideoCodecInfo): VideoEncoder? {
        val softwareEncoder: VideoEncoder? = softwareVideoEncoderFactory.createEncoder(info)
        val hardwareEncoder: VideoEncoder? = hardwareVideoEncoderFactory.createEncoder(info)
        if (hardwareEncoder != null && softwareEncoder != null) {
            return VideoEncoderFallback(softwareEncoder, hardwareEncoder)
        }
        return hardwareEncoder ?: softwareEncoder
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        val supportedCodecInfos = LinkedHashSet<VideoCodecInfo>()
        supportedCodecInfos.addAll(listOf(*softwareVideoEncoderFactory.supportedCodecs))
        supportedCodecInfos.addAll(listOf(*hardwareVideoEncoderFactory.supportedCodecs))
        return supportedCodecInfos.toTypedArray()
    }
}
