package jp.shiguredo.sora.sdk.codec

import jp.shiguredo.sora.sdk.channel.option.SoraVideoOption
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.EglBase
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.SimulcastVideoEncoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoEncoder
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoFrame
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class SimulcastVideoEncoderFactoryWrapper(
    sharedContext: EglBase.Context?,
    enableIntelVp8Encoder: Boolean,
    enableH264HighProfile: Boolean,
    videoCodec: SoraVideoOption.Codec,
    enableResolutionAdjustment: Boolean,
) : VideoEncoderFactory {

    // ストリーム単位のエンコーダをラップした上で以下を行うクラス。
    // - スレッドをひとつ起動する
    // - initEncode の width/height と frame buffer のそれが一致しない場合は事前にスケールする
    // - 内部のエンコーダをつねにそのスレッド上で呼び出す
    private class StreamEncoderWrapper(
        private val encoder: VideoEncoder,
        private val enableResolutionAdjustment: Boolean
    ) : VideoEncoder {
        companion object {
            val TAG = StreamEncoderWrapper::class.simpleName
        }

        // 単一スレッドで実行するための ExecutorService
        // 中にあるスレッドが終了しない限りは、つねに同じスレッド上で実行されることが保証されている。
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        var streamSettings: VideoEncoder.Settings? = null

        override fun initEncode(settings: VideoEncoder.Settings, callback: VideoEncoder.Callback?): VideoCodecStatus {
            streamSettings = if (enableResolutionAdjustment && (settings.height % 16 != 0 || settings.width % 16 != 0)) {
                var cropX = 0
                var cropY = 0
                if (settings.width % 16 != 0) {
                    cropX = settings.width % 16
                    SoraLogger.i(TAG, "width: ${settings.width} => ${settings.width - cropX}")
                }

                if (settings.height % 16 != 0) {
                    cropY = settings.height % 16
                    SoraLogger.i(TAG, "height: ${settings.height} => ${settings.height - cropY}")
                }

                VideoEncoder.Settings(
                    settings.numberOfCores,
                    settings.width - cropX,
                    settings.height - cropY,
                    settings.startBitrate,
                    settings.maxFramerate,
                    settings.numberOfSimulcastStreams,
                    settings.automaticResizeOn,
                    settings.capabilities,
                )
            } else {
                settings
            }

            if (streamSettings == null) {
                return VideoCodecStatus.ERROR
            }

            val future = executor.submit(
                Callable {
                    SoraLogger.i(
                        TAG,
                        """initEncode() thread=${Thread.currentThread().name} [${Thread.currentThread().id}]
                |  encoder=${encoder.implementationName}
                |  streamSettings:
                |    numberOfCores=${streamSettings!!.numberOfCores}
                |    width=${streamSettings!!.width}
                |    height=${streamSettings!!.height}
                |    startBitrate=${streamSettings!!.startBitrate}
                |    maxFramerate=${streamSettings!!.maxFramerate}
                |    automaticResizeOn=${streamSettings!!.automaticResizeOn}
                |    numberOfSimulcastStreams=${streamSettings!!.numberOfSimulcastStreams}
                |    lossNotification=${streamSettings!!.capabilities.lossNotification}
            """.trimMargin()
                    )
                    return@Callable encoder.initEncode(streamSettings, callback)
                }
            )
            return future.get()
        }

        override fun release(): VideoCodecStatus {
            val future = executor.submit(Callable { return@Callable encoder.release() })
            return future.get()
        }

        override fun encode(frame: VideoFrame, encodeInfo: VideoEncoder.EncodeInfo?): VideoCodecStatus {
            val future = executor.submit(
                Callable {
                    // SoraLogger.d(
                    //     TAG,
                    //     "encode() buffer=${frame.buffer}, thread=${Thread.currentThread().name} " +
                    //         "[${Thread.currentThread().id}]"
                    // )
                    if (streamSettings == null) {
                        return@Callable encoder.encode(frame, encodeInfo)
                    } else if (frame.buffer.width == streamSettings!!.width && frame.buffer.height == streamSettings!!.height) {
                        return@Callable encoder.encode(frame, encodeInfo)
                    } else {
                        // 上がってきたバッファと initEncode() の設定が違うパターン、ここでスケールする必要がある
                        val originalBuffer = frame.buffer
                        // val ratio = originalBuffer.width / streamSettings!!.width
                        // SoraLogger.d(
                        //     TAG,
                        //     "encode: Scaling needed, " +
                        //         "${originalBuffer.width}x${originalBuffer.height} to ${streamSettings!!.width}x${streamSettings!!.height}, " +
                        //         "ratio=$ratio"
                        // )
                        // TODO(shino): へんなスケールファクタの場合に正しく動作するか?
                        val adaptedBuffer = originalBuffer.cropAndScale(
                            0, 0, originalBuffer.width, originalBuffer.height,
                            streamSettings!!.width, streamSettings!!.height
                        )
                        val adaptedFrame = VideoFrame(adaptedBuffer, frame.rotation, frame.timestampNs)
                        val result = encoder.encode(adaptedFrame, encodeInfo)
                        adaptedBuffer.release()
                        return@Callable result
                    }
                }
            )
            return future.get()
        }

        override fun setRateAllocation(allocation: VideoEncoder.BitrateAllocation?, frameRate: Int): VideoCodecStatus {
            val future = executor.submit(Callable { return@Callable encoder.setRateAllocation(allocation, frameRate) })
            return future.get()
        }

        override fun getScalingSettings(): VideoEncoder.ScalingSettings {
            val future = executor.submit(Callable { return@Callable encoder.scalingSettings })
            return future.get()
        }

        override fun getImplementationName(): String {
            val future = executor.submit(Callable { return@Callable encoder.implementationName })
            return future.get()
        }
    }

    private class StreamEncoderWrapperFactory(
        private val factory: VideoEncoderFactory,
        private val enableResolutionAdjustment: Boolean
    ) : VideoEncoderFactory {
        override fun createEncoder(videoCodecInfo: VideoCodecInfo?): VideoEncoder? {
            val encoder = factory.createEncoder(videoCodecInfo)
            if (encoder == null) {
                return null
            }
            return StreamEncoderWrapper(encoder, enableResolutionAdjustment)
        }

        override fun getSupportedCodecs(): Array<VideoCodecInfo> {
            return factory.supportedCodecs
        }
    }

    private val primary: VideoEncoderFactory
    private val fallback: VideoEncoderFactory?
    private val native: SimulcastVideoEncoderFactory

    init {
        val hardwareVideoEncoderFactory = HardwareVideoEncoderFactory(
            sharedContext, enableIntelVp8Encoder, enableH264HighProfile
        )
        primary = StreamEncoderWrapperFactory(hardwareVideoEncoderFactory, enableResolutionAdjustment)

        // H.264 のサイマルキャストを利用する場合は fallback に null を設定する
        // Sora Android SDK では SW の H.264 を無効化しているため fallback に設定できるものがない
        fallback = if (videoCodec != SoraVideoOption.Codec.H264) {
            SoftwareVideoEncoderFactory()
        } else {
            null
        }
        native = SimulcastVideoEncoderFactory(primary, fallback)
    }

    override fun createEncoder(info: VideoCodecInfo?): VideoEncoder? {
        return native.createEncoder(info)
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        return native.supportedCodecs
    }
}
