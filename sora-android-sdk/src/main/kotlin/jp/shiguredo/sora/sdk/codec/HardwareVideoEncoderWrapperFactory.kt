package jp.shiguredo.sora.sdk.codec

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoEncoder
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoFrame

const val RESOLUTION_ALIGNMENT = 16

class VideoEncoderHelper(private val originalSettings: VideoEncoder.Settings) {

    companion object {
        val TAG = VideoEncoderHelper::class.simpleName
    }

    private var adjustedSettings: VideoEncoder.Settings? = null

    val isCropRequired: Boolean
        get() = adjustedSettings != null

    val settings: VideoEncoder.Settings
        get() {
            return if (isCropRequired) {
                adjustedSettings!!
            } else {
                originalSettings
            }
        }

    val width: Int
        get() = settings.width

    val height: Int
        get() = settings.height

    var cropX = 0
        private set
    var cropY = 0
        private set

    // 最新のフレームのサイズ
    private var lastFrameWidth: Int = 0
    private var lastFrameHeight: Int = 0

    init {
        SoraLogger.i(TAG, "$this init")

        lastFrameWidth = originalSettings.width
        lastFrameHeight = originalSettings.height

        calculate(originalSettings.width, originalSettings.height)
    }

    private fun calculate(width: Int, height: Int) {
        cropX = width % RESOLUTION_ALIGNMENT
        cropY = height % RESOLUTION_ALIGNMENT

        if (cropX != 0 || cropY != 0) {
            SoraLogger.i(
                TAG,
                "calculate: ${width}x$height => " +
                    "${width - cropX}x${height - cropY}"
            )
            adjustedSettings = VideoEncoder.Settings(
                originalSettings.numberOfCores,
                width - cropX,
                height - cropY,
                originalSettings.startBitrate,
                originalSettings.maxFramerate,
                originalSettings.numberOfSimulcastStreams,
                originalSettings.automaticResizeOn,
                originalSettings.capabilities,
            )
        }
    }

    // 動作確認をした限り、エンコーダーに渡されるフレームのサイズが変化する前に initEncode が呼ばれており、
    // recalculateIfNeeded は実行されなかったが念の為に残しておく
    //
    // この関数を削除する場合は、 VideoEncoderHelper と HardwareVideoEncoderWrapper の統合を検討すべき
    fun recalculateIfNeeded(frame: VideoFrame) {
        if (frame.buffer.width != lastFrameWidth || frame.buffer.height != lastFrameHeight) {
            SoraLogger.i(
                TAG,
                "frame size changed: ${lastFrameWidth}x$lastFrameHeight => ${frame.buffer.width}x${frame.buffer.height}"
            )
            lastFrameWidth = frame.buffer.width
            lastFrameHeight = frame.buffer.height

            calculate(frame.buffer.width, frame.buffer.height)
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

            if (!it.isCropRequired) {
                encoder.encode(frame, encodeInfo)
            } else {
                // JavaI420Buffer の cropAndScaleI420 はクロップ後のサイズとスケール後のサイズが等しい場合、
                // メモリー・コピーが発生しない
                // 参照: https://source.chromium.org/chromium/chromium/src/+/main:third_party/webrtc/sdk/android/api/org/webrtc/JavaI420Buffer.java;l=172-185;drc=02334e07c5c04c729dd3a8a279bb1fbe24ee8b7c
                val adjustedBuffer = frame.buffer.cropAndScale(
                    it.cropX / 2, it.cropY / 2, it.width, it.height, it.width, it.height
                )
                // SoraLogger.i(TAG, "crop: ${it.originalSettings.width}x${it.originalSettings.height} => ${it.width}x${it.height}")
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
