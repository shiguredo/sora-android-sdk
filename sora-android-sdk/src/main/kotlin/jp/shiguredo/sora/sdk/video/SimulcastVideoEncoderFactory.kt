package jp.shiguredo.sora.sdk.video

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*


class SimulcastVideoEncoderFactory (
        private val eglContext: EglBase.Context?,
        private val enableIntelVp8Encoder: Boolean = true,
        private val enableH264HighProfile: Boolean = false
) : VideoEncoderFactory {
    companion object {
        val TAG = SimulcastVideoEncoderFactory::class.simpleName
    }

    private val hardwareVideoEncoderFactory = HardwareVideoEncoderFactory(
            eglContext, enableIntelVp8Encoder, enableH264HighProfile)

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        return hardwareVideoEncoderFactory.supportedCodecs
    }

    override fun createEncoder(videoCodecInfo: VideoCodecInfo?): VideoEncoder? {
        SoraLogger.d(TAG, "createEncoder: codec=${videoCodecInfo?.name} params=${videoCodecInfo?.params}")
        return SimulcastVideoEncoder(hardwareVideoEncoderFactory, listOf())
    }

}

class SimulcastVideoEncoder (
        val encoderFactory: VideoEncoderFactory,
        val encodings: List<RtpParameters.Encoding>
) : VideoEncoder {
    companion object {
        val TAG = SimulcastVideoEncoderFactory::class.simpleName!!
    }

    val encoders = mutableListOf<SingleStreamVideoEncoder>()

    override fun setRateAllocation(allocation: VideoEncoder.BitrateAllocation?, frameRate: Int): VideoCodecStatus {
        TODO("not implemented")
    }

    override fun initEncode(settings: VideoEncoder.Settings?, encodeCallback: VideoEncoder.Callback?): VideoCodecStatus {
        TODO("not implemented")
    }

    override fun getImplementationName(): String {
        return "$TAG (${encodings})"
    }

    override fun getScalingSettings(): VideoEncoder.ScalingSettings {
        TODO("not implemented")
    }

    override fun release(): VideoCodecStatus {
        TODO("not implemented")
    }

    override fun encode(frame: VideoFrame?, info: VideoEncoder.EncodeInfo?): VideoCodecStatus {
        TODO("not implemented")
    }

}


class SingleStreamVideoEncoder(
        private val encoder: VideoEncoder,
        private val spatialIndex: Int = 0) : VideoEncoder {
    companion object {
        val TAG = SimulcastVideoEncoderFactory::class.simpleName
    }

    private val encodeCallback = VideoEncoder.Callback { originalEncodedImage, codecSpecificInfo ->
        if (originalCallback == null) {
            SoraLogger.w(TAG, "original encoder callback is not specified yet, skip image $originalEncodedImage")
            return@Callback
        }

        val builder = EncodedImage.builder()
                .setBuffer(originalEncodedImage.buffer,
                        null) // TODO(shino): releaseCallback は null で良いか
                .setEncodedWidth(originalEncodedImage.encodedWidth)
                .setEncodedHeight(originalEncodedImage.encodedHeight)
                .setCaptureTimeNs(originalEncodedImage.captureTimeNs)
                .setFrameType(originalEncodedImage.frameType)
                .setRotation(originalEncodedImage.rotation)
                .setCompleteFrame(originalEncodedImage.completeFrame)
                .setQp(originalEncodedImage.qp)
                .setSpatialIndex(spatialIndex)
        val encodedImage = builder.createEncodedImage()
        originalCallback?.onEncodedFrame(encodedImage, codecSpecificInfo)
    }

    private var originalCallback: VideoEncoder.Callback? = null

    override fun setRateAllocation(allocation: VideoEncoder.BitrateAllocation?, frameRate: Int): VideoCodecStatus {
        return encoder.setRateAllocation(allocation, frameRate)
    }

    override fun initEncode(originalSettings: VideoEncoder.Settings?, originalCallback: VideoEncoder.Callback?): VideoCodecStatus {
        SoraLogger.d(TAG, "initEncode: originalSettings=$originalSettings")
        this.originalCallback = originalCallback
        // TODO(shino): ここに width/hieght や frameRate が入っているので適当に変更する
        val settings = originalSettings
        return encoder.initEncode(settings, encodeCallback)
    }

    override fun getImplementationName(): String {
        return "$TAG (${encoder.implementationName})"
    }

    override fun getScalingSettings(): VideoEncoder.ScalingSettings {
        return encoder.scalingSettings
    }

    override fun release(): VideoCodecStatus {
        return encoder.release()
    }

    override fun encode(frame: VideoFrame?, info: VideoEncoder.EncodeInfo?): VideoCodecStatus {
        return encoder.encode(frame, info)
    }
}