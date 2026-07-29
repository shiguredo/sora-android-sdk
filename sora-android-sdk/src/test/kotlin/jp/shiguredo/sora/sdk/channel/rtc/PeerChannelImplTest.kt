package jp.shiguredo.sora.sdk.channel.rtc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PeerChannelImplTest {
    private lateinit var peer: PeerChannelImpl

    @Before
    fun setup() {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val mediaOption =
            SoraMediaOption().apply {
                enableVideoDownstream(null)
                enableAudioDownstream()
            }
        val networkConfig =
            PeerNetworkConfig(
                serverConfig = null,
                mediaOption = mediaOption,
                insecure = false,
            )
        peer =
            PeerChannelImpl(
                appContext = appContext,
                networkConfig = networkConfig,
                mediaOption = mediaOption,
                insecure = false,
                listener = null,
            )
    }

    @Test
    fun `compress=false で返却される ByteBuffer が元のバッファと異なるインスタンスであること`() {
        val buffer = directBufferOf("hello")
        val result = peer.unzipBufferIfNeeded("test", buffer)
        assertNotSame(buffer, result)
    }

    @Test
    fun `compress=false で返却される ByteBuffer が元のバッファと同じ内容を持つこと`() {
        val original = directBufferOf("hello")
        val result = peer.unzipBufferIfNeeded("test", original)

        val expectedBytes = ByteArray(original.remaining()).also { original.duplicate().get(it) }
        val resultBytes = ByteArray(result.remaining()).also { result.get(it) }
        assertTrue(expectedBytes.contentEquals(resultBytes))
    }

    @Test
    fun `compress=false で返却される ByteBuffer がヒープバッファであること`() {
        val buffer = directBufferOf("hello")
        val result = peer.unzipBufferIfNeeded("test", buffer)
        assertFalse(result.isDirect)
    }

    private fun directBufferOf(s: String): ByteBuffer {
        val bytes = s.toByteArray()
        val buffer = ByteBuffer.allocateDirect(bytes.size)
        buffer.put(bytes)
        buffer.flip()
        return buffer
    }
}
