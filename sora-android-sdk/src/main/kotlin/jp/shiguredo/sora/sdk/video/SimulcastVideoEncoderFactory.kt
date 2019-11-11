package jp.shiguredo.sora.sdk.video

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*


class SimulcastVideoEncoderFactory (
        private val configs: List<Config>,
        private val eglContext: EglBase.Context?,
        private val enableIntelVp8Encoder: Boolean = true,
        private val enableH264HighProfile: Boolean = false
) : VideoEncoderFactory {
    companion object {
        val TAG = SimulcastVideoEncoderFactory::class.simpleName
    }

    data class Config (
            val scaleResolutionDownBy: Double? = null,
            val maxBitrate: Int? = null,
            val maxFramerate: Int? = null,
            val useHardwareEncoder: Boolean = true
    )

    private val videoEncoderFactory = HardwareVideoEncoderFactory(
            eglContext, enableIntelVp8Encoder, enableH264HighProfile)

    init {
        SoraLogger.d(TAG, "init: configs=${configs}")
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        return videoEncoderFactory.supportedCodecs
    }

    override fun createEncoder(videoCodecInfo: VideoCodecInfo): VideoEncoder? {
        SoraLogger.d(TAG, "createEncoder: codec=${videoCodecInfo.name} params=${videoCodecInfo.params}")
        return SimulcastVideoEncoder(videoEncoderFactory, configs, videoCodecInfo)
    }
}

class SimulcastVideoEncoder (
        private val encoderFactory: VideoEncoderFactory,
        private val configs: List<SimulcastVideoEncoderFactory.Config>,
        private val videoCodecInfo: VideoCodecInfo
) : VideoEncoder {
    companion object {
        val TAG = SimulcastVideoEncoderFactory::class.simpleName!!
    }

    private val encoders: List<SingleStreamVideoEncoder> =
            configs.mapIndexed { spatialIndex, config ->
                val bareEncoder = encoderFactory.createEncoder(videoCodecInfo)!!
                SingleStreamVideoEncoder(bareEncoder, config, spatialIndex)
            }

    override fun initEncode(originalSettings: VideoEncoder.Settings, encodeCallback: VideoEncoder.Callback): VideoCodecStatus {
        SoraLogger.i(TAG, """initEncode:
            |numberOfCores=${originalSettings.numberOfCores}
            |width=${originalSettings.width}
            |height=${originalSettings.height}
            |maxFramerate=${originalSettings.maxFramerate}
            |automaticResizeOn=${originalSettings.automaticResizeOn}
            |numberOfSimulcastStreams=${originalSettings.numberOfSimulcastStreams}
            |lossNotification=${originalSettings.capabilities.lossNotification}
        """.trimMargin())
        val statusList = encoders.map {
            val (width, height) = calculateResolution(it.config, originalSettings)
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
        // TODO(shino): ひとまず low のものを戻す
        return statusList[0]
    }

    private fun calculateResolution(config: SimulcastVideoEncoderFactory.Config,
                                    settings: VideoEncoder.Settings): Pair<Int, Int> {
        when (val scale = config.scaleResolutionDownBy) {
            null -> return Pair(settings.width, settings.height)
            else -> {
                // TODO(shino): へんな数字になるとコケるかも。エンコーダの capability と比較する必要あり
                val width = (settings.width / scale).toInt()
                val height = (settings.height / scale).toInt()
                return Pair(width, height)
            }
        }
    }

    override fun setRateAllocation(allocation: VideoEncoder.BitrateAllocation, frameRate: Int): VideoCodecStatus {
        // TODO(shino): ひとまず low に渡す
        return encoders[0].setRateAllocation(allocation, frameRate)
    }

    override fun getImplementationName(): String {
        return "$TAG (${configs})"
    }

    override fun getScalingSettings(): VideoEncoder.ScalingSettings {
        // TODO(shino): ひとまず low に渡す
        return encoders[0].scalingSettings
    }

    override fun release(): VideoCodecStatus {
        val statusList = encoders.map { it.release() }
        // TODO(shino): ひとまず low のものを戻す
        return statusList[0]
    }

    override fun encode(frame: VideoFrame, info: VideoEncoder.EncodeInfo): VideoCodecStatus {
        SoraLogger.i(TAG, "encode: streams=${info.frameTypes.size}, " +
                "frameTypes=${info.frameTypes.map { it.name }.joinToString(separator = ", ")}")
        val statusList = info.frameTypes.mapIndexed { index, _ ->
            if (index < encoders.size) {
                encoders[index].encode(frame, info)
            } else {
                SoraLogger.e(TAG, "Too many frameTypes: " +
                        "frameTypes=${info.frameTypes.map { it.name }.joinToString(separator = ", ")}")
                VideoCodecStatus.ERROR
            }
        }
        // TODO(shino): ひとまず low のものを戻す
        return statusList[0]
    }
}


class SingleStreamVideoEncoder(
        private val encoder: VideoEncoder,
        val config: SimulcastVideoEncoderFactory.Config,
        private val spatialIndex: Int) : VideoEncoder {
    companion object {
        val TAG = SimulcastVideoEncoderFactory::class.simpleName
    }

    private lateinit var settings: VideoEncoder.Settings

    private val encodeCallback = VideoEncoder.Callback { originalEncodedImage, codecSpecificInfo ->
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
        originalCallback.onEncodedFrame(encodedImage, codecSpecificInfo)
    }

    private lateinit var originalCallback: VideoEncoder.Callback

    override fun setRateAllocation(allocation: VideoEncoder.BitrateAllocation, frameRate: Int): VideoCodecStatus {
        return encoder.setRateAllocation(allocation, frameRate)
    }

    override fun initEncode(settings: VideoEncoder.Settings, originalCallback: VideoEncoder.Callback): VideoCodecStatus {
        SoraLogger.i(TAG, """initEncode:
            |numberOfCores=${settings.numberOfCores}
            |width=${settings.width}
            |height=${settings.height}
            |maxFramerate=${settings.maxFramerate}
            |automaticResizeOn=${settings.automaticResizeOn}
            |numberOfSimulcastStreams=${settings.numberOfSimulcastStreams}
            |lossNotification=${settings.capabilities.lossNotification}
        """.trimMargin())
        this.originalCallback = originalCallback
        this.settings = settings
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

    override fun encode(frame: VideoFrame, info: VideoEncoder.EncodeInfo): VideoCodecStatus {
        val adaptedFrame = when (val scale = config.scaleResolutionDownBy) {
            1.0 -> {
                SoraLogger.d(TAG, "encode: Scaling needed scale=$scale to ${settings.width}x${settings.height}")
                val buffer = frame.buffer
                val adaptedBuffer = buffer.cropAndScale(0, 0, buffer.width, buffer.height,
                        settings.width, settings.height)
                SoraLogger.w(TAG, "adaptedBuffer=$adaptedBuffer")
                val adaptedFrame = VideoFrame(adaptedBuffer, frame.rotation, frame.timestampNs)
                //  adaptedBuffer.release()
                adaptedFrame
            }
            else ->{
                SoraLogger.d(TAG, "encode: Scaling needed scale=$scale to ${settings.width}x${settings.height}")
                val buffer = frame.buffer
                val adaptedBuffer = buffer.cropAndScale(0, 0, buffer.width, buffer.height,
                        settings.width, settings.height)
                SoraLogger.w(TAG, "adaptedBuffer=$adaptedBuffer")
                val adaptedFrame = VideoFrame(adaptedBuffer, frame.rotation, frame.timestampNs)
                //  adaptedBuffer.release()
                adaptedFrame
            }
        }
        return encoder.encode(adaptedFrame, info)
    }
}