package jp.shiguredo.sora.sdk.video

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*


class SimulcastVideoEncoderFactory (
        eglContext: EglBase.Context?,
        enableIntelVp8Encoder: Boolean = true,
        enableH264HighProfile: Boolean = false
) : VideoEncoderFactory {
    companion object {
        val TAG = SimulcastVideoEncoderFactory::class.simpleName
    }

    private val videoEncoderFactory = HardwareVideoEncoderFactory(
            eglContext, enableIntelVp8Encoder, enableH264HighProfile)

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        return videoEncoderFactory.supportedCodecs
    }

    override fun createEncoder(videoCodecInfo: VideoCodecInfo): VideoEncoder? {
        return SimulcastVideoEncoder(videoEncoderFactory, videoCodecInfo)
    }
}

class SimulcastVideoEncoder (
        private val encoderFactory: VideoEncoderFactory,
        private val videoCodecInfo: VideoCodecInfo
) : VideoEncoder {
    companion object {
        val TAG = SimulcastVideoEncoder::class.simpleName!!
    }

    private lateinit var encoderSettings: VideoEncoder.Settings

    private val encoders: MutableList<SingleStreamVideoEncoder> = mutableListOf()

    init {
        SoraLogger.d(TAG, "init: codec=${videoCodecInfo.name} params=${videoCodecInfo.params}")
    }

    override fun initEncode(settings: VideoEncoder.Settings, encodeCallback: VideoEncoder.Callback): VideoCodecStatus {
        encoderSettings = settings
        SoraLogger.i(TAG, """initEncode:
            |numberOfCores=${settings.numberOfCores}
            |width=${settings.width}
            |height=${settings.height}
            |maxFramerate=${settings.maxFramerate}
            |automaticResizeOn=${settings.automaticResizeOn}
            |numberOfSimulcastStreams=${settings.numberOfSimulcastStreams}
            |lossNotification=${settings.capabilities.lossNotification}
        """.trimMargin())
        settings.simulcastStreams.forEach {
            SoraLogger.i(TAG, """initEncode simulcastStream:
                |width=${it.width}
                |height=${it.height}
                |maxFramerate=${it.maxFramerate}
                |numberOfTemporalLayers=${it.numberOfTemporalLayers}
                |maxBitrate=${it.maxBitrate}
                |targetBitrafe=${it.targetBitrate}
                |minBitrafe=${it.minBitrate}
                |qpMax=${it.qpMax}
                |active=${it.active}
            """.trimMargin())
        }
        settings.simulcastStreams.forEachIndexed { spatialIndex, simulcastStream ->
            val streamSettings = VideoEncoder.Settings(
                    encoderSettings.numberOfCores,
                    simulcastStream.width.toInt(),
                    simulcastStream.height.toInt(),
                    simulcastStream.targetBitrate, // TODO(shino): 本当は startBitrate
                    simulcastStream.maxFramerate.toInt(),
                    1,                             // numberOfSimulcastStreams
                    emptyList(),
                    encoderSettings.automaticResizeOn,
                    encoderSettings.capabilities
            )
            val encoder = SingleStreamVideoEncoder(encoderFactory.createEncoder(videoCodecInfo)!!, spatialIndex)
            encoders.add(spatialIndex, encoder)
            val initStatus = encoder.initEncode(streamSettings, encodeCallback)
            if (initStatus != VideoCodecStatus.OK) {
                return initStatus
            }
        }
        return VideoCodecStatus.OK
    }

    override fun setRateAllocation(allocation: VideoEncoder.BitrateAllocation, frameRate: Int): VideoCodecStatus {
        // TODO(shino): ひとまず low に渡す
        return encoders[0].setRateAllocation(allocation, frameRate)
    }

    override fun getImplementationName(): String {
        return "$TAG (${encoderSettings.simulcastStreams})"
    }

    override fun getScalingSettings(): VideoEncoder.ScalingSettings {
        // TODO(shino): ひとまず low に渡す
        return encoders[0].scalingSettings
    }

    override fun release(): VideoCodecStatus {
        val statusList = encoders.map { it.release() }
        encoders.clear()
        return statusList.find { it != VideoCodecStatus.OK }
                ?: VideoCodecStatus.OK
    }

    override fun encode(frame: VideoFrame, info: VideoEncoder.EncodeInfo): VideoCodecStatus {
        SoraLogger.i(TAG, "encode: streams=${info.frameTypes.size}, " +
                "frameTypes=${info.frameTypes.map { it.name }.joinToString(separator = ", ")}")
        if (encoders.size < info.frameTypes.size) {
            return VideoCodecStatus.ERR_PARAMETER
        }

        info.frameTypes.forEachIndexed { index, frameType ->
            val streamEncodeInfo = VideoEncoder.EncodeInfo(arrayOf(frameType))
            val status = encoders[index].encode(frame, streamEncodeInfo)
            if (status != VideoCodecStatus.OK) {
                return status
            }
        }
        return VideoCodecStatus.OK
    }
}


class SingleStreamVideoEncoder(
        private val encoder: VideoEncoder,
        private val spatialIndex: Int) : VideoEncoder {
    companion object {
        val TAG = SingleStreamVideoEncoder::class.simpleName
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
        val status = encoder.initEncode(settings, encodeCallback)
        if (status != VideoCodecStatus.OK) {
            SoraLogger.e(TAG, "initEncode() failed: status=${status.name}, spatialIndex=$spatialIndex")
        }
        return status
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
        // TODO(shino): ここでは width のみ確認する。アスペクト比はもっと前で確認を実装すべき。
        val adaptedFrame = if (frame.buffer.width == settings.width) {
            frame
        }else {
            val buffer = frame.buffer
            SoraLogger.d(TAG, "encode: Scaling needed, spatialIndex=$spatialIndex, " +
                    "${buffer.width}x${buffer.height} to ${settings.width}x${settings.height}")
            // TODO(shino): 最適化の余地あり??
            val adaptedBuffer = buffer.cropAndScale(0, 0, buffer.width, buffer.height,
                    settings.width, settings.height)
            SoraLogger.w(TAG, "adaptedBuffer=$adaptedBuffer")
            val adaptedFrame = VideoFrame(adaptedBuffer, frame.rotation, frame.timestampNs)
            adaptedFrame
        }
        val status = encoder.encode(adaptedFrame, info)
        if (status != VideoCodecStatus.OK) {
            SoraLogger.e(TAG, "encode() failed: status=${status.name}, spatialIndex=$spatialIndex")
        }
        return status
    }
}