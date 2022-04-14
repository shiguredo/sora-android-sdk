package jp.shiguredo.sora.sdk.codec

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoEncoder
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoFrame

internal class HardwareVideoEncoderFactoryWrapper(
    factory: HardwareVideoEncoderFactory,
    enableResolutionAdjustment: Boolean = true,
) : VideoEncoderFactory {

    private class HardwareEncoderWrapper(
        private val encoder: VideoEncoder,
        private val enableResolutionAdjustment: Boolean,
    ) : VideoEncoder {
        companion object {
            val TAG = HardwareEncoderWrapper::class.simpleName
        }

        var adjustedSettings: VideoEncoder.Settings? = null

        override fun initEncode(settings: VideoEncoder.Settings, callback: VideoEncoder.Callback?): VideoCodecStatus {
            if (!enableResolutionAdjustment) {
                return encoder.initEncode(settings, callback)
            }

            adjustedSettings = settings
            if (settings.height % 16 != 0 || settings.width % 16 != 0) {
                var cropX = 0
                var cropY = 0
                if (settings.width % 16 != 0) {
                    cropX = settings.width % 16
                    SoraLogger.i(TAG, "width: ${settings.width} => ${settings.width - cropX}")
                    settings.width - cropX
                }

                if (settings.height % 16 != 0) {
                    cropY = settings.height % 16
                    SoraLogger.i(TAG, "height: ${settings.height} => ${settings.height - cropY}")
                    settings.height - cropY
                }

                adjustedSettings = VideoEncoder.Settings(
                    settings.numberOfCores,
                    settings.width - cropX,
                    settings.height - cropY,
                    settings.startBitrate,
                    settings.maxFramerate,
                    settings.numberOfSimulcastStreams,
                    settings.automaticResizeOn,
                    settings.capabilities,
                )
            }

            return encoder.initEncode(adjustedSettings, callback)
        }

        override fun release(): VideoCodecStatus {
            return encoder.release()
        }

        override fun encode(frame: VideoFrame, encodeInfo: VideoEncoder.EncodeInfo?): VideoCodecStatus {
            if (!enableResolutionAdjustment) {
                return encoder.encode(frame, encodeInfo)
            }
            return adjustedSettings?.let {
                if (frame.buffer.width == it.width && frame.buffer.height == it.height) {
                    encoder.encode(frame, encodeInfo)
                } else {
                    var originalWidth = frame.buffer.width
                    var originalHeight = frame.buffer.height

                    var adjustedBuffer = frame.buffer.cropAndScale(
                        0, 0, originalWidth, originalHeight, it.width, it.height
                    )

                    val adjustedFrame = VideoFrame(adjustedBuffer, frame.rotation, frame.timestampNs)
                    val result = encoder.encode(adjustedFrame, encodeInfo)
                    adjustedBuffer.release()
                    result
                }
            } ?: run {
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

    private class HardwareVideoEncoderWrapperFactory(
        private val factory: VideoEncoderFactory,
        private val enableResolutionAdjustment: Boolean,
    ) : VideoEncoderFactory {
        override fun createEncoder(videoCodecInfo: VideoCodecInfo?): VideoEncoder? {
            val encoder = factory.createEncoder(videoCodecInfo)
            if (encoder == null) {
                return null
            }
            return HardwareEncoderWrapper(encoder, enableResolutionAdjustment)
        }

        override fun getSupportedCodecs(): Array<VideoCodecInfo> {
            return factory.supportedCodecs
        }
    }

    private val native: HardwareVideoEncoderWrapperFactory = HardwareVideoEncoderWrapperFactory(
        factory, enableResolutionAdjustment,
    )

    override fun createEncoder(info: VideoCodecInfo?): VideoEncoder? {
        return native.createEncoder(info)
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        return native.supportedCodecs
    }
}
