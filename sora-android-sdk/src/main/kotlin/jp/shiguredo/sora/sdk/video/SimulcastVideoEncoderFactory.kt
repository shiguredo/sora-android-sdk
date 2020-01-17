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

    private val videoEncoderFactory = SoraHardwareVideoEncoderFactory(
            eglContext, enableIntelVp8Encoder, enableH264HighProfile)

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        return videoEncoderFactory.supportedCodecs
    }

    override fun createEncoder(videoCodecInfo: VideoCodecInfo): VideoEncoder? {
        return SimulcastTrackVideoEncoder(videoEncoderFactory, videoCodecInfo)
    }
}


class SimulcastTrackVideoEncoder (
        private val encoderFactory: VideoEncoderFactory,
        private val videoCodecInfo: VideoCodecInfo
) : VideoEncoder {
    companion object {
        val TAG = SimulcastTrackVideoEncoder::class.simpleName!!
    }

    private lateinit var trackSettings: VideoEncoder.Settings

    private val streamEncoders: MutableList<SimulcastStreamVideoEncoder> = mutableListOf()

    init {
        SoraLogger.d(TAG, "init($this): codec=${videoCodecInfo.name} params=${videoCodecInfo.params}")
    }

    override fun initEncode(trackSettings: VideoEncoder.Settings,
                            originalCallback: VideoEncoder.Callback)
            : VideoCodecStatus {
        this.trackSettings = trackSettings
        SoraLogger.i(TAG, """initEncode() trackSettings:
            |numberOfCores=${trackSettings.numberOfCores}
            |width=${trackSettings.width}
            |height=${trackSettings.height}
            |startBitrate=${trackSettings.startBitrate}
            |maxFramerate=${trackSettings.maxFramerate}
            |automaticResizeOn=${trackSettings.automaticResizeOn}
            |numberOfSimulcastStreams=${trackSettings.numberOfSimulcastStreams}
            |lossNotification=${trackSettings.capabilities.lossNotification}
        """.trimMargin())

        val maxSimulcastIndex = trackSettings.numberOfSimulcastStreams - 1
        for (simulcastIndex in 0..maxSimulcastIndex) {
            val simulcastStream = trackSettings.simulcastStreams[simulcastIndex]
            val streamSettings = VideoEncoder.Settings(
                    this.trackSettings.numberOfCores,
                    simulcastStream.width.toInt(),
                    simulcastStream.height.toInt(),
                    simulcastStream.targetBitrate, // TODO(shino): 本当は startBitrate だがいいのか?
                    simulcastStream.maxFramerate.toInt(),
                    0, emptyList(),                // numberOfSimulcastStreams, simulcastStreams
                    this.trackSettings.automaticResizeOn,
                    this.trackSettings.capabilities
            )

            val streamEncoder = SimulcastStreamVideoEncoder(
                    encoderFactory, videoCodecInfo, simulcastIndex)
            streamEncoders.add(simulcastIndex, streamEncoder)

            val status = streamEncoder.initEncode(streamSettings, originalCallback)
            if (status != VideoCodecStatus.OK) {
                return status
            }
        }
        return VideoCodecStatus.OK
    }

    override fun setRateAllocation(allocation: VideoEncoder.BitrateAllocation, framerate: Int): VideoCodecStatus {
        // SoraLogger.i(TAG, "setRateAllocation(): framerate=${framerate}, bitrate sum=${allocation.sum}")
        // allocation.bitratesBbs.mapIndexed { simulcastIndex, bitrateBbs ->
        //     SoraLogger.i(TAG, "setRateAllocation(): simulcastIndex=$simulcastIndex, " +
        //             "bitrate sum=${bitrateBbs.sum()}, bitrateBps=${bitrateBbs.joinToString(", ")}")
        // }

        // BitrateAllocation.bitrateBbs は simulcast stream 数よりも多いことがある (個数は固定っぽい)
        // 明示的にきまっているわけではないのでここは保守的に書いておく。
        streamEncoders.forEach { streamEncoder ->
            val simulcastIndex = streamEncoder.simulcastIndex
            val streamAllocation = if (allocation.bitratesBbs.size <= simulcastIndex) {
                // stream encoder に対応するアロケーションが無かった。おそらくここを通ることはないが
                // ゼロを渡しておく
                VideoEncoder.BitrateAllocation(arrayOf(IntArray(0)))
            } else {
                VideoEncoder.BitrateAllocation(arrayOf(allocation.bitratesBbs[simulcastIndex]))
            }

            // 対応するビットレートアロケーションを stream encoder に渡す
            val status = streamEncoder.setRateAllocation(streamAllocation,
                    streamEncoder.streamSettings.maxFramerate)
            if (status != VideoCodecStatus.OK) {
                return status
            }
        }
        return VideoCodecStatus.OK
    }

    override fun getImplementationName(): String {
        return "$TAG (${trackSettings.simulcastStreams})"
    }

    override fun getScalingSettings(): VideoEncoder.ScalingSettings {
        return streamEncoders[0].scalingSettings
    }

    override fun release(): VideoCodecStatus {
        SoraLogger.d(TAG, "release($this)")
        // エラーに関わらずすべての stream encoder をリリースする
        val statusList = streamEncoders.map { it.release() }
        streamEncoders.clear()
        return statusList.find { it != VideoCodecStatus.OK }
                ?: VideoCodecStatus.OK
    }

    override fun encode(frame: VideoFrame, info: VideoEncoder.EncodeInfo): VideoCodecStatus {
        // SoraLogger.i(TAG, "encode: frameTypes size=${info.frameTypes.size}, " +
        //         "frameTypes=${info.frameTypes.map { it.name }.joinToString(separator = ", ")}")
        // SoraLogger.i(TAG, "encode: frame.timestampNs=${frame.timestampNs}, buffer=${frame.buffer}")

        val statusList = info.frameTypes.mapIndexed { simulcastIndex, frameType ->
            val streamEncodeInfo = VideoEncoder.EncodeInfo(arrayOf(frameType))
            val status = streamEncoders[simulcastIndex].encode(frame, streamEncodeInfo)
            status
        }
        return statusList.firstOrNull { status -> status != VideoCodecStatus.OK } ?: VideoCodecStatus.OK
    }

    override fun isHardwareEncoder() = true
}


class SimulcastStreamVideoEncoder(
        private val encoderFoctory: VideoEncoderFactory,
        private val videoCodecInfo: VideoCodecInfo,
        val simulcastIndex: Int) : VideoEncoder {
    companion object {
        val TAG = SimulcastStreamVideoEncoder::class.simpleName
    }

    private var originalEncoder: VideoEncoder? = null

    // initEncode() で初期化される
    lateinit var streamSettings: VideoEncoder.Settings
    private lateinit var originalCallback: VideoEncoder.Callback

    private var nextEncodeAt: Long = Long.MIN_VALUE
    private var frameInterval: Long = 0

    private val encodeCallback = VideoEncoder.Callback { originalEncodedImage, codecSpecificInfo ->
        // EncodedImage に spatial index を設定する、これが RTP で rid に読み替えられる
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
                .setSpatialIndex(simulcastIndex)  // 元と違うのはここだけ
        val encodedImage = builder.createEncodedImage()
        originalCallback.onEncodedFrame(encodedImage, codecSpecificInfo)
    }

    init {
        SoraLogger.d(TAG, "init($this): simulcastIndex=${simulcastIndex}")
    }

    override fun setRateAllocation(allocation: VideoEncoder.BitrateAllocation,
                                   framerate: Int): VideoCodecStatus {
        SoraLogger.d(TAG, "setRateAllocation(): simulcastIndex=${simulcastIndex}, " +
                "framerate=${framerate}, bitrate sum=${allocation.sum}")
        // allocation.bitratesBbs.forEach{ bitrateBbs ->
        //     SoraLogger.i(TAG, "setRateAllocation(): simulcastIndex=$simulcastIndex, " +
        //             "bitrate sum=${bitrateBbs.sum()}, bitrateBps=${bitrateBbs.joinToString(", ")}")
        // }
        return originalEncoder?.setRateAllocation(allocation, framerate) ?: VideoCodecStatus.OK
    }

    override fun initEncode(streamSettings: VideoEncoder.Settings,
                            originalCallback: VideoEncoder.Callback): VideoCodecStatus {
        this.originalCallback = originalCallback
        this.streamSettings = streamSettings
        frameInterval = 1000L / maxOf(streamSettings.maxFramerate, 1)

        SoraLogger.i(TAG, """initEncode() simulcastIndex=$simulcastIndex, frameInterval=$frameInterval, streamSettings:
            |numberOfCores=${streamSettings.numberOfCores}
            |width=${streamSettings.width}
            |height=${streamSettings.height}
            |startBitrate=${streamSettings.startBitrate}
            |maxFramerate=${streamSettings.maxFramerate}
            |automaticResizeOn=${streamSettings.automaticResizeOn}
            |numberOfSimulcastStreams=${streamSettings.numberOfSimulcastStreams}
            |lossNotification=${streamSettings.capabilities.lossNotification}
        """.trimMargin())

        if (streamSettings.maxFramerate == 0) {
            return VideoCodecStatus.OK
        }

        originalEncoder = encoderFoctory.createEncoder(videoCodecInfo)
        // TODO: ここでエンコーダが取れなかったら SW にフォールバックする?
        val status = originalEncoder!!.initEncode(streamSettings, encodeCallback)
        if (status != VideoCodecStatus.OK) {
            SoraLogger.e(TAG, "initEncode() failed: simulcastIndex=$simulcastIndex, status=${status.name}")
        }
        return status
    }

    override fun getImplementationName(): String {
        return "$TAG (simulcastIndex=$simulcastIndex, ${originalEncoder?.implementationName})"
    }

    override fun getScalingSettings(): VideoEncoder.ScalingSettings {
        return originalEncoder?.scalingSettings ?: VideoEncoder.ScalingSettings.OFF
    }

    override fun release(): VideoCodecStatus {
        SoraLogger.d(TAG, "release($this): simulcastIndex=${simulcastIndex}")
        return originalEncoder?.release() ?: VideoCodecStatus.OK
    }

    override fun encode(frame: VideoFrame, info: VideoEncoder.EncodeInfo): VideoCodecStatus {
        if ( streamSettings.maxFramerate == 0 ) {
            return VideoCodecStatus.OK
        }

        val isKeyframe = (info.frameTypes[0] == EncodedImage.FrameType.VideoFrameKey)
        val currentMillis = System.currentTimeMillis()

        // フレームレートを設定に合わせるため間引く
        if (nextEncodeAt == Long.MIN_VALUE) {
            // 最初のフレーム
            nextEncodeAt = currentMillis + frameInterval
        } else if (isKeyframe || (nextEncodeAt < currentMillis)) {
            // キーフレームは強制エンコード、それ以外はフレームレート依存
            nextEncodeAt += frameInterval
        } else {
            // スキップする
            return VideoCodecStatus.OK
        }

        // TODO(shino): ここでは width のみ確認する。アスペクト比が等しいことはもっと前で確認を実装すべき。
        val adaptedFrame = if (frame.buffer.width == streamSettings.width) {
            frame
        }else {
            val buffer = frame.buffer
            // SoraLogger.d(TAG, "encode: Scaling needed, simulcastIndex=$simulcastIndex, " +
            //         "${buffer.width}x${buffer.height} to ${streamSettings.width}x${streamSettings.height}")
            // TODO(shino): 最適化の余地あり??
            val adaptedBuffer = buffer.cropAndScale(0, 0, buffer.width, buffer.height,
                    streamSettings.width, streamSettings.height)
            val adaptedFrame = VideoFrame(adaptedBuffer, frame.rotation, frame.timestampNs)
            adaptedFrame
        }
        val status = originalEncoder?.encode(adaptedFrame, info) ?: VideoCodecStatus.OK
        if (status != VideoCodecStatus.OK) {
            SoraLogger.e(TAG, "encode() failed: simulcastIndex=$simulcastIndex, status=${status.name}")
        }
        return status
    }

    override fun isHardwareEncoder() = originalEncoder?.isHardwareEncoder ?: true
}
