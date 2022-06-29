package jp.shiguredo.sora.sdk.codec

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoEncoder
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoFrame

internal class HardwareVideoEncoderWrapper(
    private val internalEncoder: VideoEncoder,
    private val alignment: UInt,
) : VideoEncoder {
    class CropSizeCalculator(
        alignment: UInt,
        private val originalWidth: Int,
        private val originalHeight: Int,
    ) {

        companion object {
            val TAG = CropSizeCalculator::class.simpleName
        }

        val cropX: Int = originalWidth % alignment.toInt()
        val cropY: Int = originalHeight % alignment.toInt()

        val croppedWidth: Int
            get() = originalWidth - cropX

        val croppedHeight: Int
            get() = originalHeight - cropY

        val isCropRequired: Boolean
            get() = cropX != 0 || cropY != 0

        init {
            // nullable を避けるために適当な値で初期化した場合は、初期化時のログに出力しない
            if (originalWidth != 0 && originalHeight != 0) {
                SoraLogger.i(
                    TAG,
                    "$this init(): alignment=$alignment" +
                        "" +
                        " size=${originalWidth}x$originalHeight => ${croppedWidth}x$croppedHeight"
                )
            }
        }

        fun hasFrameSizeChanged(nextWidth: Int, nextHeight: Int): Boolean {
            return if (originalWidth == nextWidth && originalHeight == nextHeight) {
                false
            } else {
                SoraLogger.i(
                    TAG,
                    "frame size has changed: " +
                        "${originalWidth}x$originalHeight => ${nextWidth}x$nextHeight"
                )
                true
            }
        }
    }

    companion object {
        val TAG = HardwareVideoEncoderWrapper::class.simpleName
    }

    // nullable を避けるために適当な値で初期化している
    private var calculator = CropSizeCalculator(1u, 0, 0)

    private fun retryWithoutCropping(width: Int, height: Int, retryFunc: () -> VideoCodecStatus): VideoCodecStatus {
        SoraLogger.i(TAG, "retrying without resolution adjustment")

        // alignment = 1u ... 解像度調整なし
        calculator = CropSizeCalculator(1u, width, height)

        return retryFunc()
    }

    override fun initEncode(originalSettings: VideoEncoder.Settings, callback: VideoEncoder.Callback?): VideoCodecStatus {
        calculator = CropSizeCalculator(alignment, originalSettings.width, originalSettings.height)

        if (!calculator.isCropRequired) {
            // crop なし
            return internalEncoder.initEncode(originalSettings, callback)
        } else {
            // crop あり
            val croppedSettings = VideoEncoder.Settings(
                originalSettings.numberOfCores,
                calculator.croppedWidth,
                calculator.croppedHeight,
                originalSettings.startBitrate,
                originalSettings.maxFramerate,
                originalSettings.numberOfSimulcastStreams,
                originalSettings.automaticResizeOn,
                originalSettings.capabilities,
            )

            // HardwareVideoEncoder が利用している MediaCodec で例外が発生した際、 try, catch がないとフォールバックが動作しなかった
            try {
                val result = internalEncoder.initEncode(croppedSettings, callback)
                return if (result == VideoCodecStatus.FALLBACK_SOFTWARE) {
                    // 解像度調整ありで VideoCodecStatus.FALLBACK_SOFTWARE が発生した場合、
                    // SW にフォールバックする前に解像度調整なしのパターンを試す
                    SoraLogger.e(TAG, "internalEncoder.initEncode() returned FALLBACK_SOFTWARE: croppedSettings $croppedSettings")
                    retryWithoutCropping(originalSettings.width, originalSettings.height) { internalEncoder.initEncode(originalSettings, callback) }
                } else {
                    // FALLBACK_SOFTWARE 以外はそのまま返す
                    result
                }
            } catch (e: Exception) {
                SoraLogger.e(TAG, "internalEncoder.initEncode() failed", e)

                // 解像度調整ありで例外が発生した場合、
                // SW にフォールバックする前に解像度調整なしのパターンを試す
                return retryWithoutCropping(originalSettings.width, originalSettings.height) { internalEncoder.initEncode(originalSettings, callback) }
            }
        }
    }

    override fun release(): VideoCodecStatus {
        return internalEncoder.release()
    }

    override fun encode(frame: VideoFrame, encodeInfo: VideoEncoder.EncodeInfo?): VideoCodecStatus {
        // 解像度が変化した場合は calculator を再度初期化する
        // HardwareVideoEncoder の encode ではフレーム・サイズの変化が考慮されていたため、この条件を実装した
        // 参照: https://source.chromium.org/chromium/chromium/src/+/master:third_party/webrtc/sdk/android/src/java/org/webrtc/HardwareVideoEncoder.java;l=353-362;drc=5a79d28eba61aea39558a492fb4c0ff4fef427ba
        //
        // shiguredo-webrtc-build/webrtc-build の 102.5005.7.6 6ff7318 で動作を確認した限り、
        // ネットワーク帯域などに起因してダウンサイズが発生した場合は、 encode の前に initEncode が呼ばれていた
        if (calculator.hasFrameSizeChanged(frame.buffer.width, frame.buffer.height)) {
            calculator = CropSizeCalculator(alignment, frame.buffer.width, frame.buffer.height)
        }

        if (!calculator.isCropRequired) {
            return internalEncoder.encode(frame, encodeInfo)
        } else {
            // crop のための計算
            // 補足: JavaI420Buffer の cropAndScaleI420 はクロップ後のサイズとスケール後のサイズが等しい場合、
            //       メモリー・コピーが発生しないため、それほど重い処理ではない
            // 参照: https://source.chromium.org/chromium/chromium/src/+/main:third_party/webrtc/sdk/android/api/org/webrtc/JavaI420Buffer.java;l=172-185;drc=02334e07c5c04c729dd3a8a279bb1fbe24ee8b7c
            val croppedWidth = calculator.croppedWidth
            val croppedHeight = calculator.croppedHeight
            val croppedBuffer = frame.buffer.cropAndScale(
                calculator.cropX / 2, calculator.cropY / 2,
                croppedWidth, croppedHeight, croppedWidth, croppedHeight
            )

            // SoraLogger.i(TAG, "crop: ${frame.buffer.width}x${frame.buffer.height} => ${width}x$height")
            val croppedFrame = VideoFrame(croppedBuffer, frame.rotation, frame.timestampNs)

            // HardwareVideoEncoder が利用している MediaCodec で例外が発生した際、 try, catch がないとフォールバックが動作しなかった
            try {
                val result = internalEncoder.encode(croppedFrame, encodeInfo)
                return if (result == VideoCodecStatus.FALLBACK_SOFTWARE) {
                    // 解像度調整ありで VideoCodecStatus.FALLBACK_SOFTWARE が発生した場合、
                    // SW にフォールバックする前に解像度調整なしのパターンを試す
                    SoraLogger.e(TAG, "internalEncoder.encode() returned FALLBACK_SOFTWARE")
                    retryWithoutCropping(frame.buffer.width, frame.buffer.height) { internalEncoder.encode(frame, encodeInfo) }
                } else {
                    result
                }
            } catch (e: Exception) {
                SoraLogger.e(TAG, "internalEncoder.encode() failed", e)
                return retryWithoutCropping(frame.buffer.width, frame.buffer.height) { internalEncoder.encode(frame, encodeInfo) }
            } finally {
                croppedBuffer.release()
            }
        }
    }

    override fun setRateAllocation(allocation: VideoEncoder.BitrateAllocation?, frameRate: Int): VideoCodecStatus {
        return internalEncoder.setRateAllocation(allocation, frameRate)
    }

    override fun getScalingSettings(): VideoEncoder.ScalingSettings {
        return internalEncoder.scalingSettings
    }

    override fun getImplementationName(): String {
        return internalEncoder.implementationName
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
            val encoder = factory.createEncoder(videoCodecInfo) ?: return null
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
