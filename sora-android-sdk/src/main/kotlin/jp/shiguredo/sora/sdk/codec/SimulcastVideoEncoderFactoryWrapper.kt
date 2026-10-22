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
    resolutionAdjustment: SoraVideoOption.ResolutionAdjustment,
) : VideoEncoderFactory {
    // ストリーム単位のエンコーダーをラップした上で以下を行うクラス。
    // - スレッドをひとつ起動する
    // - initEncode の width/height と frame buffer のそれが一致しない場合は事前にスケールする
    // - 内部のエンコーダーをつねにそのスレッド上で呼び出す
    private class StreamEncoderWrapper(
        private val encoder: VideoEncoder,
    ) : VideoEncoder {
        companion object {
            val TAG = StreamEncoderWrapper::class.simpleName
        }

        private fun currentThreadIdForLog(): Long =
            if (android.os.Build.VERSION.SDK_INT >= 36) {
                Thread.currentThread().threadId()
            } else {
                @Suppress("DEPRECATION")
                Thread.currentThread().id
            }

        // 単一スレッドで実行するための ExecutorService
        // 中にあるスレッドが終了しない限りは、つねに同じスレッド上で実行されることが保証されている。
        // release() で shutdown した後に initEncode() が再度呼ばれることがあるため、
        // Executor は initEncode() 時に再生成できるように nullable にしている。
        @Volatile
        private var executor: ExecutorService? = Executors.newSingleThreadExecutor()

        // getImplementationName() は Executor 停止後にも呼ばれるため、生成時にキャッシュする。
        private val implementationName: String =
            try {
                encoder.implementationName
            } catch (e: Exception) {
                SoraLogger.w(TAG, "getImplementationName failed: ${e.message}")
                ""
            }

        var streamSettings: VideoEncoder.Settings? = null

        // 現在の Executor を返す。停止済みまたは未生成の場合は null を返す。
        private fun liveExecutor(): ExecutorService? =
            synchronized(this) {
                executor?.takeUnless { it.isShutdown }
            }

        // Executor を返す。停止済みまたは未生成の場合は新しい Executor を作る。
        private fun ensureExecutor(): ExecutorService =
            synchronized(this) {
                executor?.takeUnless { it.isShutdown }
                    ?: Executors.newSingleThreadExecutor().also { executor = it }
            }

        override fun initEncode(
            settings: VideoEncoder.Settings,
            callback: VideoEncoder.Callback?,
        ): VideoCodecStatus {
            streamSettings = settings
            val currentExecutor = ensureExecutor()
            return try {
                currentExecutor
                    .submit(
                        Callable {
                            SoraLogger.i(
                                TAG,
                                """initEncode() thread=${Thread.currentThread().name} [tid=${currentThreadIdForLog()}]
                    |  encoder=$implementationName
                    |  streamSettings:
                    |    numberOfCores=${settings.numberOfCores}
                    |    width=${settings.width}
                    |    height=${settings.height}
                    |    startBitrate=${settings.startBitrate}
                    |    maxFramerate=${settings.maxFramerate}
                    |    automaticResizeOn=${settings.automaticResizeOn}
                    |    numberOfSimulcastStreams=${settings.numberOfSimulcastStreams}
                    |    lossNotification=${settings.capabilities.lossNotification}
                                """.trimMargin(),
                            )
                            return@Callable encoder.initEncode(settings, callback)
                        },
                    ).get()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                SoraLogger.w(TAG, "initEncode interrupted: ${e.message}")
                VideoCodecStatus.ERROR
            } catch (e: Exception) {
                SoraLogger.w(TAG, "initEncode failed: ${e.message}")
                VideoCodecStatus.ERROR
            }
        }

        override fun release(): VideoCodecStatus {
            val currentExecutor =
                synchronized(this) {
                    executor.also { executor = null }
                } ?: return VideoCodecStatus.OK
            if (currentExecutor.isShutdown) {
                return VideoCodecStatus.OK
            }
            return try {
                currentExecutor.submit(Callable { return@Callable encoder.release() }).get()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                SoraLogger.w(TAG, "release interrupted: ${e.message}")
                VideoCodecStatus.ERROR
            } catch (e: Exception) {
                SoraLogger.w(TAG, "release failed: ${e.message}")
                VideoCodecStatus.ERROR
            } finally {
                currentExecutor.shutdown()
            }
        }

        override fun encode(
            frame: VideoFrame,
            encodeInfo: VideoEncoder.EncodeInfo?,
        ): VideoCodecStatus {
            val currentExecutor = liveExecutor() ?: return VideoCodecStatus.ERROR
            return try {
                currentExecutor
                    .submit(
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
                                    val scaledBuffer =
                                        frame.buffer.cropAndScale(
                                            0,
                                            0,
                                            originalWidth,
                                            originalHeight,
                                            it.width,
                                            it.height,
                                        )
                                    try {
                                        val scaledFrame = VideoFrame(scaledBuffer, frame.rotation, frame.timestampNs)
                                        encoder.encode(scaledFrame, encodeInfo)
                                    } finally {
                                        scaledBuffer.release()
                                    }
                                }
                            } ?: run {
                                // streamSettings は null にならない想定だが force unwrap を防ぐためにこのように記述する
                                VideoCodecStatus.ERROR
                            }
                        },
                    ).get()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                SoraLogger.w(TAG, "encode interrupted: ${e.message}")
                VideoCodecStatus.ERROR
            } catch (e: Exception) {
                SoraLogger.w(TAG, "encode failed: ${e.message}")
                VideoCodecStatus.ERROR
            }
        }

        override fun setRateAllocation(
            allocation: VideoEncoder.BitrateAllocation?,
            frameRate: Int,
        ): VideoCodecStatus {
            val currentExecutor = liveExecutor() ?: return VideoCodecStatus.ERROR
            return try {
                currentExecutor
                    .submit(Callable { return@Callable encoder.setRateAllocation(allocation, frameRate) })
                    .get()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                SoraLogger.w(TAG, "setRateAllocation interrupted: ${e.message}")
                VideoCodecStatus.ERROR
            } catch (e: Exception) {
                SoraLogger.w(TAG, "setRateAllocation failed: ${e.message}")
                VideoCodecStatus.ERROR
            }
        }

        override fun getScalingSettings(): VideoEncoder.ScalingSettings {
            val currentExecutor = liveExecutor() ?: return VideoEncoder.ScalingSettings.OFF
            return try {
                currentExecutor
                    .submit(Callable { return@Callable encoder.scalingSettings })
                    .get()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                SoraLogger.w(TAG, "getScalingSettings interrupted: ${e.message}")
                VideoEncoder.ScalingSettings.OFF
            } catch (e: Exception) {
                SoraLogger.w(TAG, "getScalingSettings failed: ${e.message}")
                VideoEncoder.ScalingSettings.OFF
            }
        }

        override fun getImplementationName(): String = implementationName
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

        override fun getSupportedCodecs(): Array<VideoCodecInfo> = factory.supportedCodecs
    }

    private val primary: VideoEncoderFactory
    private val fallback: VideoEncoderFactory?
    private val native: SimulcastVideoEncoderFactory

    init {
        val hardwareVideoEncoderFactory =
            HardwareVideoEncoderFactory(
                sharedContext,
                enableIntelVp8Encoder,
                enableH264HighProfile,
            )

        val encoderFactory =
            if (resolutionAdjustment == SoraVideoOption.ResolutionAdjustment.NONE) {
                hardwareVideoEncoderFactory
            } else {
                HardwareVideoEncoderWrapperFactory(
                    hardwareVideoEncoderFactory,
                    resolutionAdjustment.value,
                )
            }

        primary = StreamEncoderWrapperFactory(encoderFactory)
        fallback = SoftwareVideoEncoderFactory()
        native = SimulcastVideoEncoderFactory(primary, fallback)
    }

    override fun createEncoder(info: VideoCodecInfo?): VideoEncoder? = native.createEncoder(info)

    override fun getSupportedCodecs(): Array<VideoCodecInfo> = native.supportedCodecs
}
