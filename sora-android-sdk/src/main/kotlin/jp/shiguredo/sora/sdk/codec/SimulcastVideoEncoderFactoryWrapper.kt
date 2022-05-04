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
    enableIntelVp8Encoder: Boolean = true,
    enableH264HighProfile: Boolean = false,
    videoCodec: SoraVideoOption.Codec,
    enableResolutionAdjustment: Boolean = true
) : VideoEncoderFactory {

    // ストリーム単位のエンコーダをラップした上で以下を行うクラス。
    // - スレッドをひとつ起動する
    // - initEncode の width/height と frame buffer のそれが一致しない場合は事前にスケールする
    // - 内部のエンコーダをつねにそのスレッド上で呼び出す
    private class StreamEncoderWrapper(
        private val encoder: VideoEncoder,
    ) : VideoEncoder {
        companion object {
            val TAG = StreamEncoderWrapper::class.simpleName
        }

        // 単一スレッドで実行するための ExecutorService
        // 中にあるスレッドが終了しない限りは、つねに同じスレッド上で実行されることが保証されている。
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        var streamSettings: VideoEncoder.Settings? = null

        override fun initEncode(settings: VideoEncoder.Settings, callback: VideoEncoder.Callback?): VideoCodecStatus {
            streamSettings = settings
            val future = executor.submit(
                Callable {
                    SoraLogger.i(
                        TAG,
                        """initEncode() thread=${Thread.currentThread().name} [${Thread.currentThread().id}]
                |  encoder=${encoder.implementationName}
                |  streamSettings:
                |    numberOfCores=${settings.numberOfCores}
                |    width=${settings.width}
                |    height=${settings.height}
                |    startBitrate=${settings.startBitrate}
                |    maxFramerate=${settings.maxFramerate}
                |    automaticResizeOn=${settings.automaticResizeOn}
                |    numberOfSimulcastStreams=${settings.numberOfSimulcastStreams}
                |    lossNotification=${settings.capabilities.lossNotification}
            """.trimMargin()
                    )
                    return@Callable encoder.initEncode(settings, callback)
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
                    return@Callable streamSettings?.let {
                        if (frame.buffer.width == it.width) {
                            // スケールが不要
                            encoder.encode(frame, encodeInfo)
                        } else {
                            // StreamEncoderWrapper ではスケールだけを行い
                            // 解像度の調整は HardwareVideoEncoderWrapper に任せる
                            val originalWidth = frame.buffer.width
                            val originalHeight = frame.buffer.height
                            val scaledBuffer = frame.buffer.cropAndScale(
                                0, 0, originalWidth, originalHeight,
                                it.width, it.height,
                            )
                            /*
                            SoraLogger.d(
                                TAG,
                                "scale: ${originalWidth}x$originalHeight => " +
                                    "${it.width}x${it.height}"
                            )
                             */
                            val scaledFrame = VideoFrame(scaledBuffer, frame.rotation, frame.timestampNs)
                            val result = encoder.encode(scaledFrame, encodeInfo)
                            scaledBuffer.release()
                            result
                        }
                    } ?: run {
                        // streamSettings は null にならない想定だが force unwrap を防ぐためにこのように記述する
                        VideoCodecStatus.ERROR
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
    ) : VideoEncoderFactory {
        override fun createEncoder(videoCodecInfo: VideoCodecInfo?): VideoEncoder? {
            val encoder = factory.createEncoder(videoCodecInfo)
            if (encoder == null) {
                return null
            }
            return StreamEncoderWrapper(encoder)
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

        primary = if (enableResolutionAdjustment) {
            StreamEncoderWrapperFactory(
                HardwareVideoEncoderWrapperFactory(hardwareVideoEncoderFactory)
            )
        } else {
            StreamEncoderWrapperFactory(hardwareVideoEncoderFactory)
        }

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
