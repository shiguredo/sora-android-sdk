package jp.shiguredo.sora.sdk.video

import jp.shiguredo.sora.sdk.channel.signaling.message.Encoding
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*
import java.lang.IllegalArgumentException


class SimulcastVideoEncoderFactory (
        private val encodings: List<Encoding>,
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
        return SimulcastVideoEncoder(hardwareVideoEncoderFactory, encodings, videoCodecInfo)
    }
}

class SimulcastVideoEncoder (
        private val encoderFactory: VideoEncoderFactory,
        private val encodings: List<Encoding>,
        private val videoCodecInfo: VideoCodecInfo?
) : VideoEncoder {
    companion object {
        val TAG = SimulcastVideoEncoderFactory::class.simpleName!!
    }

    val encoders: MutableList<SingleStreamVideoEncoder> = encodings.map {
        // TODO(shino): インデックス付き map (forEach?) が欲しい
        val spatialIndex = when (it.rid) {
            "low" -> 0
            "middle" -> 1
            "high" -> 2
            else -> throw IllegalArgumentException("rid=${it.rid}")
        }
        SingleStreamVideoEncoder(encoderFactory.createEncoder(videoCodecInfo)!!, it, spatialIndex)
    }.toMutableList()

    override fun setRateAllocation(allocation: VideoEncoder.BitrateAllocation?, frameRate: Int): VideoCodecStatus {
        // TODO(shino): ひとまず low に渡す
        return encoders[0].setRateAllocation(allocation, frameRate)
    }

    override fun initEncode(originalSettings: VideoEncoder.Settings, encodeCallback: VideoEncoder.Callback): VideoCodecStatus {
        SoraLogger.i(TAG, "resolution=${originalSettings.width}x${originalSettings.height}, " +
                "maxFrameRate=${originalSettings.maxFramerate}, capabilities=${originalSettings.capabilities}")
        encoders.map {
            var width = originalSettings.width
            var height = originalSettings.height
            if (it.encoding.scaleResolutionDownBy != null) {
                val scale = it.encoding.scaleResolutionDownBy as Int
                // TODO(shino): へんな数字になるとコケるかも。エンコーダの capability と比較する必要あり
                width = width / scale
                height = height / scale
            }
            val settings = VideoEncoder.Settings(
                    originalSettings.numberOfCores,
                    width,
                    height,
                    originalSettings.startBitrate,  // TODO(shino): ここはどうする?
                    originalSettings.maxFramerate,
                    1,  // numberOfSimulcastStreams
                    originalSettings.automaticResizeOn,
                    originalSettings.capabilities)
            it.initEncode(settings, encodeCallback)
        }
    }

    override fun getImplementationName(): String {
        return "$TAG (${encodings})"
    }

    override fun getScalingSettings(): VideoEncoder.ScalingSettings {
        // TODO(shino): ひとまず low に渡す
        return encoders[0].scalingSettings
    }

    override fun release(): VideoCodecStatus {
        encoders.map { it.release() }
        encoders.clear()
    }

    override fun encode(frame: VideoFrame?, info: VideoEncoder.EncodeInfo?): VideoCodecStatus {
        encoders.map { it.encode(frame, info) }
    }
}


class SingleStreamVideoEncoder(
        private val encoder: VideoEncoder,
        val encoding: Encoding,
        val spatialIndex: Int) : VideoEncoder {
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