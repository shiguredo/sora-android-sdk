package jp.shiguredo.sora.sdk.codec

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoEncoder
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoFrame

class VideoEncoderHelper(private val originalSettings: VideoEncoder.Settings) {

    companion object {
        val TAG = VideoEncoderHelper::class.simpleName
    }

    private var adjustedSettings: VideoEncoder.Settings? = null

    val adjusted: Boolean
        get() = adjustedSettings != null

    val settings: VideoEncoder.Settings
        get() {
            return if (adjusted) {
                adjustedSettings!!
            } else {
                originalSettings
            }
        }

    val width: Int
        get() = settings.width

    val height: Int
        get() = settings.height

    // 最新のフレームのサイズ
    private var lastFrameWidth: Int = 0
    private var lastFrameHeight: Int = 0

    init {
        var cropX = originalSettings.width % 16
        var cropY = originalSettings.height % 16

        if (cropX != 0 || cropY != 0) {
            SoraLogger.i(
                TAG,
                "init: ${originalSettings.width}x${originalSettings.height} => " +
                    " ${originalSettings.width - cropX}x${originalSettings.height - cropY}"
            )

            adjustedSettings = VideoEncoder.Settings(
                originalSettings.numberOfCores,
                originalSettings.width - cropX,
                originalSettings.height - cropY,
                originalSettings.startBitrate,
                originalSettings.maxFramerate,
                originalSettings.numberOfSimulcastStreams,
                originalSettings.automaticResizeOn,
                originalSettings.capabilities,
            )
        }
    }

    fun recalculateIfNeeded(frame: VideoFrame) {
        /* 不要なはず
        if (lastFrameWidth == 0 && lastFrameHeight == 0) {
            lastFrameWidth = frame.buffer.width
            lastFrameHeight = frame.buffer.height
        }
         */

        if (frame.buffer.width != lastFrameWidth || frame.buffer.height != lastFrameHeight) {
            SoraLogger.i(
                TAG,
                "frame size changed: ${lastFrameWidth}x$lastFrameHeight => ${frame.buffer.width}x${frame.buffer.height}"
            )
            lastFrameWidth = frame.buffer.width
            lastFrameHeight = frame.buffer.height

            var cropX = frame.buffer.width % 16
            var cropY = frame.buffer.height % 16

            if (cropX != 0 || cropY != 0) {
                SoraLogger.i(
                    TAG,
                    "recalculate: ${frame.buffer.width}x${frame.buffer.height} => " +
                        " ${frame.buffer.width - cropX}x${frame.buffer.height - cropY}"
                )
                adjustedSettings = VideoEncoder.Settings(
                    originalSettings.numberOfCores,
                    frame.buffer.width - cropX,
                    frame.buffer.height - cropY,
                    originalSettings.startBitrate,
                    originalSettings.maxFramerate,
                    originalSettings.numberOfSimulcastStreams,
                    originalSettings.automaticResizeOn,
                    originalSettings.capabilities,
                )
            }
        }
    }
}

internal class HardwareVideoEncoderWrapper(
    private val encoder: VideoEncoder,
) : VideoEncoder {
    companion object {
        val TAG = HardwareVideoEncoderWrapper::class.simpleName
    }

    private var helper: VideoEncoderHelper? = null

    override fun initEncode(settings: VideoEncoder.Settings, callback: VideoEncoder.Callback?): VideoCodecStatus {
        helper = VideoEncoderHelper(settings)
        return encoder.initEncode(helper!!.settings, callback)
    }

    override fun release(): VideoCodecStatus {
        return encoder.release()
    }

    override fun encode(frame: VideoFrame, encodeInfo: VideoEncoder.EncodeInfo?): VideoCodecStatus {
        return helper?.let {
            it.recalculateIfNeeded(frame)

            if (!it.adjusted) {
                encoder.encode(frame, encodeInfo)
            } else {
                val originalWidth = frame.buffer.width
                val originalHeight = frame.buffer.height
                val adjustedBuffer = frame.buffer.cropAndScale(
                    0, 0, originalWidth, originalHeight, it.settings.width, it.settings.height
                )
                val adjustedFrame = VideoFrame(adjustedBuffer, frame.rotation, frame.timestampNs)
                val result = encoder.encode(adjustedFrame, encodeInfo)
                adjustedBuffer.release()
                result
            }
        } ?: run {
            // helper は null にならない想定だが force unwrap を防ぐためにこのように記述する
            VideoCodecStatus.ERROR
        }
    }

    override fun setRateAllocation(allocation: VideoEncoder.BitrateAllocation?, frameRate: Int): VideoCodecStatus {
        return encoder.setRateAllocation(allocation, frameRate)
    }

    override fun getScalingSettings(): VideoEncoder.ScalingSettings {
        return encoder.scalingSettings
    }

    override fun getImplementationName(): String {
        return encoder.implementationName
    }
}

internal class HardwareVideoEncoderWrapperFactory(
    private val factory: HardwareVideoEncoderFactory,
) : VideoEncoderFactory {
    override fun createEncoder(videoCodecInfo: VideoCodecInfo?): VideoEncoder? {
        val encoder = factory.createEncoder(videoCodecInfo)
        if (encoder == null) {
            return null
        }
        return HardwareVideoEncoderWrapper(encoder)
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        return factory.supportedCodecs
    }
}
