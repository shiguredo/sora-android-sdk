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
        SoraLogger.d(TAG, "init($this): codec=${videoCodecInfo.name} params=${videoCodecInfo.params}")
    }

    override fun initEncode(settings: VideoEncoder.Settings,
                            originalCallback: VideoEncoder.Callback)
            : VideoCodecStatus {
        encoderSettings = settings
        SoraLogger.i(TAG, """initEncode():
            |numberOfCores=${settings.numberOfCores}
            |width=${settings.width}
            |height=${settings.height}
            |startBitrate=${settings.startBitrate}
            |maxFramerate=${settings.maxFramerate}
            |automaticResizeOn=${settings.automaticResizeOn}
            |numberOfSimulcastStreams=${settings.numberOfSimulcastStreams}
            |lossNotification=${settings.capabilities.lossNotification}
        """.trimMargin())
        settings.simulcastStreams.forEachIndexed { spatialIndex, simulcastStream ->
            val streamSettings = VideoEncoder.Settings(
                    encoderSettings.numberOfCores,
                    simulcastStream.width.toInt(),
                    simulcastStream.height.toInt(),
                    simulcastStream.targetBitrate, // TODO(shino): 本当は startBitrate だがいいのか?
                    simulcastStream.maxFramerate.toInt(),
                    1,                             // numberOfSimulcastStreams
                    listOf(simulcastStream),
                    encoderSettings.automaticResizeOn,
                    encoderSettings.capabilities
            )

            val streamEncoder = if(encoders.size <= spatialIndex) {
                val encoder = SingleStreamVideoEncoder(
                        encoderFactory.createEncoder(videoCodecInfo)!!, spatialIndex)
                encoders.add(spatialIndex, encoder)
                encoder
            } else {
                encoders[spatialIndex]
            }
            val initStatus = streamEncoder.initEncode(streamSettings, originalCallback)
            if (initStatus != VideoCodecStatus.OK) {
                return initStatus
            }
        }
        return VideoCodecStatus.OK
    }

    override fun setRateAllocation(allocation: VideoEncoder.BitrateAllocation, framerate: Int): VideoCodecStatus {
        SoraLogger.i(TAG, "setRateAllocation(): framerate=${framerate}, bitrate sum=${allocation.sum}")
        allocation.bitratesBbs.mapIndexed { spatialIndex, bitrateBbs ->
            SoraLogger.i(TAG, "setRateAllocation(): spatialIndex=$spatialIndex, " +
                    "bitrate sum=${bitrateBbs.sum()}, bitrateBps=${bitrateBbs.joinToString(", ")}")
        }

        // BitrateAllocation.bitrateBbs は simulcast stream 数よりも多いことがある (固定っぽい)
        // 明示的ではないので保守的に書いておく。
        encoders.forEach { encoder ->
            val spatialIndex = encoder.spatialIndex
            if (allocation.bitratesBbs.size <= spatialIndex) {
                return@forEach
            }
            val streamAllocation = VideoEncoder.BitrateAllocation(
                    arrayOf(allocation.bitratesBbs[spatialIndex]))
            val status = encoder.setRateAllocation(streamAllocation, framerate)
            if (status != VideoCodecStatus.OK) {
                return status
            }
        }
        return VideoCodecStatus.OK
    }

    override fun getImplementationName(): String {
        return "$TAG (${encoderSettings.simulcastStreams})"
    }

    override fun getScalingSettings(): VideoEncoder.ScalingSettings {
        return encoders[0].scalingSettings
    }

    override fun release(): VideoCodecStatus {
        SoraLogger.d(TAG, "release($this)")
        val statusList = encoders.map { it.release() }
        encoders.clear()
        return statusList.find { it != VideoCodecStatus.OK }
                ?: VideoCodecStatus.OK
    }

    override fun encode(frame: VideoFrame, info: VideoEncoder.EncodeInfo): VideoCodecStatus {
        // SoraLogger.i(TAG, "encode: frameTypes size=${info.frameTypes.size}, " +
        //         "frameTypes=${info.frameTypes.map { it.name }.joinToString(separator = ", ")}")
        if (encoders.size < info.frameTypes.size) {
            return VideoCodecStatus.ERR_PARAMETER
        }

        info.frameTypes.forEachIndexed { spatialIndex, frameType ->
            val streamEncodeInfo = VideoEncoder.EncodeInfo(arrayOf(frameType))
            val status = encoders[spatialIndex].encode(frame, streamEncodeInfo)
            if (status != VideoCodecStatus.OK) {
                return status
            }
        }
        return VideoCodecStatus.OK
    }
}


class SingleStreamVideoEncoder(
        private val encoder: VideoEncoder,
        val spatialIndex: Int) : VideoEncoder {
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

    init {
        SoraLogger.d(TAG, "init($this): spatialIndex=${spatialIndex}")
    }

    override fun setRateAllocation(allocation: VideoEncoder.BitrateAllocation,
                                   framerate: Int): VideoCodecStatus {
        SoraLogger.i(TAG, "setRateAllocation(): spatialIndex=${spatialIndex}, " +
                "framerate=${framerate}, bitrate sum=${allocation.sum}")
        allocation.bitratesBbs.forEach{ bitrateBbs ->
            SoraLogger.i(TAG, "setRateAllocation(): spatialIndex=$spatialIndex, " +
                    "bitrate sum=${bitrateBbs.sum()}, bitrateBps=${bitrateBbs.joinToString(", ")}")
        }
        return encoder.setRateAllocation(allocation, framerate)
    }

    override fun initEncode(settings: VideoEncoder.Settings,
                            originalCallback: VideoEncoder.Callback): VideoCodecStatus {
        SoraLogger.i(TAG, """initEncode() spatialIndex=$spatialIndex, settings:
            |numberOfCores=${settings.numberOfCores}
            |width=${settings.width}
            |height=${settings.height}
            |startBitrate=${settings.startBitrate}
            |maxFramerate=${settings.maxFramerate}
            |automaticResizeOn=${settings.automaticResizeOn}
            |numberOfSimulcastStreams=${settings.numberOfSimulcastStreams}
            |lossNotification=${settings.capabilities.lossNotification}
        """.trimMargin())
        val simulcastStream = settings.simulcastStreams[0]
        SoraLogger.i(TAG, """initEncode() spatialIndex=$spatialIndex, simulcastStream:
            |width=${simulcastStream.width}
            |height=${simulcastStream.height}
            |maxFramerate=${simulcastStream.maxFramerate}
            |numberOfTemporalLayers=${simulcastStream.numberOfTemporalLayers}
            |maxBitrate=${simulcastStream.maxBitrate}
            |targetBitrafe=${simulcastStream.targetBitrate}
            |minBitrafe=${simulcastStream.minBitrate}
            |qpMax=${simulcastStream.qpMax}
            |active=${simulcastStream.active}
        """.trimMargin())

        this.originalCallback = originalCallback
        this.settings = settings
        val status = encoder.initEncode(settings, encodeCallback)
        if (status != VideoCodecStatus.OK) {
            SoraLogger.e(TAG, "initEncode() failed: spatialIndex=$spatialIndex, status=${status.name}")
        }
        return status
    }

    override fun getImplementationName(): String {
        return "$TAG (spatialIndex=$spatialIndex, ${encoder.implementationName})"
    }

    override fun getScalingSettings(): VideoEncoder.ScalingSettings {
        return encoder.scalingSettings
    }

    override fun release(): VideoCodecStatus {
        SoraLogger.d(TAG, "release($this): spatialIndex=${spatialIndex}")
        return encoder.release()
    }

    override fun encode(frame: VideoFrame, info: VideoEncoder.EncodeInfo): VideoCodecStatus {
        // TODO(shino): ここでは width のみ確認する。アスペクト比はもっと前で確認を実装すべき。
        val adaptedFrame = if (frame.buffer.width == settings.width) {
            frame
        }else {
            val buffer = frame.buffer
            // SoraLogger.d(TAG, "encode: Scaling needed, spatialIndex=$spatialIndex, " +
            //         "${buffer.width}x${buffer.height} to ${settings.width}x${settings.height}")
            // TODO(shino): 最適化の余地あり??
            val adaptedBuffer = buffer.cropAndScale(0, 0, buffer.width, buffer.height,
                    settings.width, settings.height)
            val adaptedFrame = VideoFrame(adaptedBuffer, frame.rotation, frame.timestampNs)
            adaptedFrame
        }
        val status = encoder.encode(adaptedFrame, info)
        if (status != VideoCodecStatus.OK) {
            SoraLogger.e(TAG, "encode() failed: spatialIndex=$spatialIndex, status=${status.name}")
        }
        return status
    }
}