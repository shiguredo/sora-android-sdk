package jp.shiguredo.sora.sdk.channel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoraMediaChannelDefaultValueTest {
    private val appContext: Context = ApplicationProvider.getApplicationContext()

    // デフォルト値で SoraMediaChannel を生成するヘルパー
    private fun createChannel(): SoraMediaChannel =
        SoraMediaChannel(
            context = appContext,
            signalingEndpoint = "wss://sora.example.com/signaling",
            channelId = "sora",
            mediaOption = SoraMediaOption(),
            listener = null,
        )

    // リフレクションで private フィールドの値を読み取る
    private fun readField(
        target: Any,
        name: String,
    ): Any? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)
    }

    // signalingMetadata のデフォルト値は null であること
    // 未指定時は metadata を送信しないための前提となるデフォルト値
    @Test
    fun `signalingMetadata のデフォルト値は null であること`() {
        val channel = createChannel()
        assertEquals(null, readField(channel, "signalingMetadata"))
    }

    // signalingNotifyMetadata のデフォルト値は null であること
    @Test
    fun `signalingNotifyMetadata のデフォルト値は null であること`() {
        val channel = createChannel()
        assertEquals(null, readField(channel, "signalingNotifyMetadata"))
    }
}
