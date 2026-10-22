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
    // - エンコーダー呼び出しを直列化する Executor を起動する
    // - initEncode の width/height と frame buffer のそれが一致しない場合は事前にスケールする
    // - initEncode / encode / setRateAllocation / getScalingSettings / release は、現在の Executor の
    //   スレッド上で呼び出す（release 後は、次の initEncode で新しい Executor を生成する）。
    //   getImplementationName は生成時に一度取得し、Executor の生存に依存しないようキャッシュする。
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

        // エンコーダー呼び出しを単一スレッドに直列化するための ExecutorService。
        // 同じ Executor の生存期間中は、つねに同じスレッド上で実行されることが保証されている。
        // release() で shutdown した後に initEncode() が再度呼ばれることがあるため、
        // Executor は initEncode() 時に再生成できるように nullable にしている。
        private var executor: ExecutorService? = Executors.newSingleThreadExecutor()

        // VideoEncoder の契約では getImplementationName() は任意のスレッドから呼ばれ得るため、
        // Executor の生存に依存しないよう、生成時に取得して保持する。
        private val implementationName: String =
            try {
                encoder.implementationName
            } catch (e: Exception) {
                SoraLogger.w(TAG, "getImplementationName failed", e)
                ""
            }

        private var streamSettings: VideoEncoder.Settings? = null

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

        // Executor 上でタスクを実行し、完了するまで待つ。
        // interrupt された場合もタスクの完了を待ち、呼び出し元へ戻る直前に interrupt を復元する。
        // タスクの完了前に戻ると、VideoFrame の解放後にタスクがフレームへ触れるため、待機は必須である。
        // そのためタイムアウトは設けず、タスクが完了するまで待ち続ける。
        private fun <T> submitAndAwait(
            executor: ExecutorService,
            operation: String,
            fallback: T,
            task: Callable<T>,
        ): T {
            val future =
                try {
                    executor.submit(task)
                } catch (e: Exception) {
                    SoraLogger.e(TAG, "$operation failed to submit", e)
                    return fallback
                }

            var interrupted = false
            try {
                while (true) {
                    try {
                        return future.get()
                    } catch (e: InterruptedException) {
                        interrupted = true
                        SoraLogger.w(TAG, "$operation interrupted; waiting for completion")
                    } catch (e: Exception) {
                        SoraLogger.e(TAG, "$operation failed", e.cause ?: e)
                        return fallback
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        override fun initEncode(
            settings: VideoEncoder.Settings,
            callback: VideoEncoder.Callback?,
        ): VideoCodecStatus {
            val currentExecutor = ensureExecutor()
            val status =
                submitAndAwait(
                    currentExecutor,
                    "initEncode",
                    VideoCodecStatus.ERROR,
                ) {
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
                    encoder.initEncode(settings, callback)
                }
            // initEncode が成功した場合だけ、encode() が参照する設定を更新する。
            if (status == VideoCodecStatus.OK) {
                streamSettings = settings
            }
            return status
        }

        override fun release(): VideoCodecStatus {
            // libwebrtc は release() と initEncode() を encoder queue 上で直列に呼ぶため、
            // 先に executor を null にしても、旧 Executor 上の release と新 Executor 上の
            // initEncode が並行して実行されることはない。
            val currentExecutor =
                synchronized(this) {
                    val current = executor
                    executor = null
                    current
                } ?: return VideoCodecStatus.OK
            if (currentExecutor.isShutdown) {
                return VideoCodecStatus.OK
            }
            return try {
                submitAndAwait(
                    currentExecutor,
                    "release",
                    VideoCodecStatus.ERROR,
                ) {
                    encoder.release()
                }
            } finally {
                currentExecutor.shutdown()
            }
        }

        override fun encode(
            frame: VideoFrame,
            encodeInfo: VideoEncoder.EncodeInfo?,
        ): VideoCodecStatus {
            val currentExecutor = liveExecutor() ?: return VideoCodecStatus.ERROR
            return submitAndAwait(
                currentExecutor,
                "encode",
                VideoCodecStatus.ERROR,
            ) {
                streamSettings?.let {
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
                } ?: VideoCodecStatus.ERROR
            }
        }

        override fun setRateAllocation(
            allocation: VideoEncoder.BitrateAllocation?,
            frameRate: Int,
        ): VideoCodecStatus {
            val currentExecutor = liveExecutor() ?: return VideoCodecStatus.ERROR
            return submitAndAwait(
                currentExecutor,
                "setRateAllocation",
                VideoCodecStatus.ERROR,
            ) {
                encoder.setRateAllocation(allocation, frameRate)
            }
        }

        override fun getScalingSettings(): VideoEncoder.ScalingSettings {
            val currentExecutor = liveExecutor() ?: return VideoEncoder.ScalingSettings.OFF
            return submitAndAwait(
                currentExecutor,
                "getScalingSettings",
                VideoEncoder.ScalingSettings.OFF,
            ) {
                encoder.scalingSettings
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
