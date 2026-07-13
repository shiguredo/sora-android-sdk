package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.webrtc.EglBase
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoEncoder
import org.webrtc.VideoEncoderFactory
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RTCComponentFactoryTest {
    private val dummyEglContext: EglBase.Context =
        object : EglBase.Context {
            override fun getNativeEglContext(): Long = 0L
        }

    private fun createFactory(
        mediaOption: SoraMediaOption,
        simulcastEnabled: Boolean,
    ): RTCComponentFactory =
        RTCComponentFactory(
            mediaOption = mediaOption,
            simulcastEnabled = simulcastEnabled,
            insecure = false,
            caCertificate = null,
            listener = null,
        )

    @Test
    fun `映像送信なし simulcastEnabled=false の場合 DOWNSTREAM が選択されること`() {
        val mediaOption =
            SoraMediaOption().apply {
                enableVideoDownstream(dummyEglContext)
            }
        val factory = createFactory(mediaOption, simulcastEnabled = false)
        val type = factory.determineVideoEncoderFactoryType()
        assertEquals(RTCComponentFactory.VideoEncoderFactoryType.DOWNSTREAM, type)
    }

    @Test
    fun `映像送信あり simulcastEnabled=false の場合 UPSTREAM が選択されること`() {
        val mediaOption =
            SoraMediaOption().apply {
                enableVideoUpstream(dummyEglContext, SoraMediaOption.SoraCameraConfig())
            }
        val factory = createFactory(mediaOption, simulcastEnabled = false)
        val type = factory.determineVideoEncoderFactoryType()
        assertEquals(RTCComponentFactory.VideoEncoderFactoryType.UPSTREAM, type)
    }

    @Test
    fun `映像送信あり simulcastEnabled=true softwareVideoEncoderOnly=false の場合 SIMULCAST が選択されること`() {
        val mediaOption = SoraMediaOption()
        mediaOption.videoUpstreamEnabled = true
        val factory = createFactory(mediaOption, simulcastEnabled = true)
        val type = factory.determineVideoEncoderFactoryType()
        assertEquals(RTCComponentFactory.VideoEncoderFactoryType.SIMULCAST, type)
    }

    @Test
    fun `映像送信あり simulcastEnabled=true softwareVideoEncoderOnly=true の場合 SIMULCAST_SOFTWARE が選択されること`() {
        val mediaOption =
            SoraMediaOption().apply {
                softwareVideoEncoderOnly = true
            }
        mediaOption.videoUpstreamEnabled = true
        val factory = createFactory(mediaOption, simulcastEnabled = true)
        val type = factory.determineVideoEncoderFactoryType()
        assertEquals(RTCComponentFactory.VideoEncoderFactoryType.SIMULCAST_SOFTWARE, type)
    }

    @Test
    fun `映像送信なし simulcastEnabled=true の場合 Simulcast 関連が選択されないこと`() {
        val mediaOption =
            SoraMediaOption().apply {
                enableVideoDownstream(dummyEglContext)
            }
        val factory = createFactory(mediaOption, simulcastEnabled = true)
        val type = factory.determineVideoEncoderFactoryType()
        assertEquals(RTCComponentFactory.VideoEncoderFactoryType.DOWNSTREAM, type)
    }

    @Test
    fun `映像送信なし simulcastEnabled=true softwareVideoEncoderOnly=true の場合 Simulcast 関連が選択されないこと`() {
        val mediaOption =
            SoraMediaOption().apply {
                enableVideoDownstream(dummyEglContext)
                softwareVideoEncoderOnly = true
            }
        val factory = createFactory(mediaOption, simulcastEnabled = true)
        val type = factory.determineVideoEncoderFactoryType()
        assertEquals(RTCComponentFactory.VideoEncoderFactoryType.DOWNSTREAM, type)
    }

    @Test
    fun `映像送信なし simulcastEnabled=true で音声のみの場合 Simulcast 関連が選択されないこと`() {
        val mediaOption = SoraMediaOption()
        val factory = createFactory(mediaOption, simulcastEnabled = true)
        val type = factory.determineVideoEncoderFactoryType()
        assertEquals(RTCComponentFactory.VideoEncoderFactoryType.NULL, type)
    }

    @Test
    fun `カスタム VideoEncoderFactory が指定されている場合 CUSTOM が選択されること`() {
        val customFactory =
            object : VideoEncoderFactory {
                override fun createEncoder(info: VideoCodecInfo): VideoEncoder? = null

                override fun getSupportedCodecs(): Array<VideoCodecInfo> = emptyArray()
            }
        val mediaOption =
            SoraMediaOption().apply {
                videoEncoderFactory = customFactory
                enableVideoDownstream(dummyEglContext)
            }
        val factory = createFactory(mediaOption, simulcastEnabled = true)
        val type = factory.determineVideoEncoderFactoryType()
        assertEquals(RTCComponentFactory.VideoEncoderFactoryType.CUSTOM, type)
    }

    @Test
    fun `createVideoEncoderFactory がカスタム VideoEncoderFactory をそのまま返すこと`() {
        val customFactory =
            object : VideoEncoderFactory {
                override fun createEncoder(info: VideoCodecInfo): VideoEncoder? = null

                override fun getSupportedCodecs(): Array<VideoCodecInfo> = emptyArray()
            }
        val mediaOption =
            SoraMediaOption().apply {
                videoEncoderFactory = customFactory
            }
        val rtcFactory = createFactory(mediaOption, simulcastEnabled = true)
        val encoderFactory = rtcFactory.createVideoEncoderFactory()
        assertSame(customFactory, encoderFactory)
    }
}
