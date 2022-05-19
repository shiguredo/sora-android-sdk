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
    resolutionPixelAlignment: Int
) : VideoEncoderFactory {
    private val hardwareVideoEncoderFactory: VideoEncoderFactory
    private val softwareVideoEncoderFactory: VideoEncoderFactory = SoftwareVideoEncoderFactory()

    init {
        val defaultFactory = HardwareVideoEncoderFactory(eglContext, enableIntelVp8Encoder, enableH264HighProfile)

        // 解像度が奇数の場合、偶数になるように調整する
        val encoderFactory = if (resolutionPixelAlignment == 0) {
            defaultFactory
        } else {
            HardwareVideoEncoderWrapperFactory(defaultFactory, resolutionPixelAlignment)
        }
        hardwareVideoEncoderFactory = encoderFactory
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
