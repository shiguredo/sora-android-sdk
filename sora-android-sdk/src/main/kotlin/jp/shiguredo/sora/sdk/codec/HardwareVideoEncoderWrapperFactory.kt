package jp.shiguredo.sora.sdk.codec

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoEncoder
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoFrame

internal class HardwareVideoEncoderWrapper(
    private val encoder: VideoEncoder,
    private val resolutionPixelAlignment: UInt,
) : VideoEncoder {
    class CropSizeCalculator(val originalSettings: VideoEncoder.Settings, private val resolutionPixelAlignment: UInt) {

        companion object {
            val TAG = CropSizeCalculator::class.simpleName
        }

        var croppedSettings: VideoEncoder.Settings? = null

        val isCropRequired: Boolean
            get() = croppedSettings != null

        val settings: VideoEncoder.Settings
            get() {
                return if (isCropRequired) {
                    croppedSettings!!
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

        // 最新のフレームの大きさ
        private var lastFrameWidth: Int = 0
        private var lastFrameHeight: Int = 0

        init {
            SoraLogger.i(TAG, "$this init: resolutionPixelAlignment=$resolutionPixelAlignment")

            lastFrameWidth = originalSettings.width
            lastFrameHeight = originalSettings.height

            calculate(originalSettings.width, originalSettings.height)
        }

        private fun calculate(width: Int, height: Int) {
            cropX = width % resolutionPixelAlignment.toInt()
            cropY = height % resolutionPixelAlignment.toInt()

            if (cropX != 0 || cropY != 0) {
                SoraLogger.i(
                    TAG,
                    "calculate: ${width}x$height => " +
                        "${width - cropX}x${height - cropY}"
                )
                croppedSettings = VideoEncoder.Settings(
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

        // HardwareVideoEncoder の encode に、フレームのサイズが途中で変化することを考慮した条件があったため実装した関数
        // 参照: https://source.chromium.org/chromium/chromium/src/+/master:third_party/webrtc/sdk/android/src/java/org/webrtc/HardwareVideoEncoder.java;l=353-362;drc=5a79d28eba61aea39558a492fb4c0ff4fef427ba
        //
        // 動作確認をした限り、ネットワーク帯域などに起因してダウンサイズが発生した場合は initEncode が呼ばれており、
        // この関数は実行されなかったが念の為に残しておく
        //
        // この関数を削除する場合は HardwareVideoEncoderWrapper との統合を検討すべき
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

    companion object {
        val TAG = HardwareVideoEncoderWrapper::class.simpleName
    }

    private var calculator: CropSizeCalculator? = null

    override fun initEncode(settings: VideoEncoder.Settings, callback: VideoEncoder.Callback?): VideoCodecStatus {
        // エンコーダーが利用している MediaCodec で例外が発生した際、 try, catch がないとフォールバックが動作しなかった
        return try {
            calculator = CropSizeCalculator(settings, resolutionPixelAlignment)
            val result = encoder.initEncode(calculator!!.settings, callback)

            if (result == VideoCodecStatus.FALLBACK_SOFTWARE && calculator!!.isCropRequired) { // && encoder.implementationName!!.contains("h264", ignoreCase = true)) {
                // 解像度調整ありで VideoCodecStatus.FALLBACK_SOFTWARE が発生した場合、
                // SW にフォールバックする前に解像度調整なしのパターンを試す
                throw Exception("initEncode failed. try to retry without resolution adjustment")
            }
            result
        } catch (e: Exception) {
            SoraLogger.e(TAG, "initEncode failed", e)

            if (calculator!!.isCropRequired) {
                SoraLogger.i(TAG, "retrying without resolution adjustment")
                val oldSettings = calculator!!.originalSettings
                calculator = CropSizeCalculator(oldSettings, 1u)
                return initEncode(settings, callback)
            }

            VideoCodecStatus.ERROR
        }
    }

    override fun release(): VideoCodecStatus {
        return encoder.release()
    }

    override fun encode(frame: VideoFrame, encodeInfo: VideoEncoder.EncodeInfo?): VideoCodecStatus {
        // エンコーダーが利用している MediaCodec で例外が発生した際、 try, catch がないとフォールバックが動作しなかった
        try {
            return calculator?.let {
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

                    if (result == VideoCodecStatus.FALLBACK_SOFTWARE && it.isCropRequired) { // && encoder.implementationName!!.contains("h264", ignoreCase = true)) {
                        // 解像度調整ありで VideoCodecStatus.FALLBACK_SOFTWARE が発生した場合、
                        // SW にフォールバックする前に解像度調整なしのパターンを試す
                        throw Exception("encode failed. try to retry without resolution adjustment")
                    }
                    result
                }
            } ?: run {
                // null にならない想定だが force unwrap を防ぐためにこのように記述する
                SoraLogger.e(TAG, "calculator is null")
                VideoCodecStatus.ERROR
            }
        } catch (e: Exception) {
            SoraLogger.e(TAG, "encode failed", e)

            if (calculator!!.isCropRequired) {
                SoraLogger.i(TAG, "retrying without resolution adjustment")
                val oldSettings = calculator!!.originalSettings
                calculator = CropSizeCalculator(oldSettings, 1u)
                return encode(frame, encodeInfo)
            }
            return VideoCodecStatus.ERROR
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
    private val resolutionPixelAlignment: UInt,
) : VideoEncoderFactory {
    companion object {
        val TAG = HardwareVideoEncoderWrapperFactory::class.simpleName
    }

    init {
        if (resolutionPixelAlignment == 0u) {
            throw java.lang.Exception("resolutionPixelAlignment should not be 0")
        }
    }

    override fun createEncoder(videoCodecInfo: VideoCodecInfo?): VideoEncoder? {
        try {
            val encoder = factory.createEncoder(videoCodecInfo)
            if (encoder == null) {
                return null
            }
            return HardwareVideoEncoderWrapper(encoder, resolutionPixelAlignment)
        } catch (e: Exception) {
            SoraLogger.e(TAG, "createEncoder failed", e)
            return null
        }
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        return factory.supportedCodecs
    }
}
