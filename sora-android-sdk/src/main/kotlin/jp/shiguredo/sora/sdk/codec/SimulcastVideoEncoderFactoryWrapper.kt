package jp.shiguredo.sora.sdk.codec

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*
import java.util.concurrent.*

internal class SimulcastVideoEncoderFactoryWrapper(
    sharedContext: EglBase.Context?,
    enableIntelVp8Encoder: Boolean,
    enableH264HighProfile: Boolean
) : VideoEncoderFactory {

    /*
     * ソフトウェアエンコーダーの利用を優先するファクトリーです。
     * 指定されたコーデックにソフトウェアエンコーダーが対応していない場合にハードウェアエンコーダーを利用します。
     * ただし、このクラスは libwebrtc の問題を回避するためのワークアラウンドで実質的に用途はありません。
     *
     * libwebrtc でサイマルキャストを扱うには SimulcastEncoderAdapter を利用します。
     * SimulcastEncoderAdapter はプライマリのエンコーダーとフォールバックのエンコーダー
     * (指定されたコーデックにプライマリのエンコーダーが対応していない場合に利用されます)
     * を持ちます。プライマリに HardwareVideoEncoderFactory 、
     * フォールバックに SoftwareVideoEncoderFactory を指定すると、
     * H.264 の使用時に libwebrtc がクラッシュします。
     * これは SoftwareVideoEncoderFactory が H.264 に非対応であり、
     * createEncoder() が null を返すのに対して libwebrtc 側が null 時の処理に対応していないためです。
     * createEncoder() はフォールバックの利用の有無に関わらず実行されるので、
     * null を回避するために HardwareVideoEncoderFactory に処理を委譲しています。
     * プライマリ・フォールバックの両方で HardwareVideoEncoderFactory を使うことになりますが、特に問題ありません。
     */
    private class Fallback(private val hardwareVideoEncoderFactory: VideoEncoderFactory) : VideoEncoderFactory {

        private val softwareVideoEncoderFactory: VideoEncoderFactory = SoftwareVideoEncoderFactory()

        override fun createEncoder(info: VideoCodecInfo): VideoEncoder? {
            val softwareEncoder = softwareVideoEncoderFactory.createEncoder(info)
            val hardwareEncoder = hardwareVideoEncoderFactory.createEncoder(info)
            return if (hardwareEncoder != null && softwareEncoder != null) {
                VideoEncoderFallback(hardwareEncoder, softwareEncoder)
            } else {
                softwareEncoder ?: hardwareEncoder
            }
        }

        override fun getSupportedCodecs(): Array<VideoCodecInfo> {
            val supportedCodecInfos: MutableList<VideoCodecInfo> = mutableListOf()
            supportedCodecInfos.addAll(softwareVideoEncoderFactory.supportedCodecs)
            supportedCodecInfos.addAll(hardwareVideoEncoderFactory.supportedCodecs)
            return supportedCodecInfos.toTypedArray()
        }
    }

    // ストリーム単位のエンコーダをラップした上で以下を行うクラス。
    // - スレッドをひとつ起動する
    // - initEncode の width/height と frame buffer のそれが一致しない場合は事前にスケールする
    // - 内部のエンコーダをつねにそのスレッド上で呼び出す
    private class StreamEncoderWrapper(private val encoder: VideoEncoder) : VideoEncoder {
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
                    // SoraLogger.d(TAG, "encode() buffer=${frame.buffer}, thread=${Thread.currentThread().name} "
                    //         + "[${Thread.currentThread().id}]")
                    if (streamSettings == null) {
                        return@Callable encoder.encode(frame, encodeInfo)
                    } else if (frame.buffer.width == streamSettings!!.width) {
                        return@Callable encoder.encode(frame, encodeInfo)
                    } else {
                        // 上がってきたバッファと initEncode() の設定が違うパターン、ここでスケールする必要がある
                        val originalBuffer = frame.buffer
                        // val ratio = originalBuffer.width / streamSettings!!.width
                        // SoraLogger.d(TAG, "encode: Scaling needed, " +
                        //         "${buffer.width}x${buffer.height} to ${streamSettings!!.width}x${streamSettings!!.height}, " +
                        //         "ratio=$ratio")
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

    private class StreamEncoderWrapperFactory(private val factory: VideoEncoderFactory) : VideoEncoderFactory {
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
    private val fallback: VideoEncoderFactory
    private val native: SimulcastVideoEncoderFactory

    init {
        val hardwareVideoEncoderFactory = HardwareVideoEncoderFactory(
            sharedContext, enableIntelVp8Encoder, enableH264HighProfile
        )
        primary = StreamEncoderWrapperFactory(hardwareVideoEncoderFactory)
        fallback = StreamEncoderWrapperFactory(Fallback(primary))
        native = SimulcastVideoEncoderFactory(primary, fallback)
    }

    override fun createEncoder(info: VideoCodecInfo?): VideoEncoder? {
        return native.createEncoder(info)
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        return native.supportedCodecs
    }
}
