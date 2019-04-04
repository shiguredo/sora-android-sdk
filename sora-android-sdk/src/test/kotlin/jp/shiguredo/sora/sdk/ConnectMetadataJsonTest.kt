package jp.shiguredo.sora.sdk

import com.google.gson.Gson
import jp.shiguredo.sora.sdk.channel.option.SoraChannelRole
import jp.shiguredo.sora.sdk.channel.signaling.message.ConnectMessage
import jp.shiguredo.sora.sdk.channel.signaling.message.MessageConverter
import org.junit.Test
import kotlin.test.assertEquals


class ConnectMetadataJsonTest {
    val gson = Gson()

    @Test
    fun noMetadata() {
        val message = roundtrip(null)
        assertEquals(message.metadata, null)
    }

    @Test
    fun stringifiedMetadata() {
        // sora-android-sdk 1.8.0 までは String? で受けていた。
        // JSON 文字列をもらったら JSON 文字列に戻る必要がある。
        val message1 = roundtrip("str")
        assertEquals(message1.metadata, "str")

        val message2 = roundtrip("{\"foo\": 1, \"bar\": \"baz\"}")
        assertEquals(message2.metadata, "{\"foo\": 1, \"bar\": \"baz\"}")

        val message3 = roundtrip("[1, 2, 3, \"DAAAAAAAA!!!!\"]")
        assertEquals(message3.metadata, "[1, 2, 3, \"DAAAAAAAA!!!!\"]")
    }

    private fun roundtrip(metadata: String?) : ConnectMessage {
        val original = ConnectMessage(
                role = "upstream",
                channelId = "sora",
                sdp         = "",
                metadata = metadata
        )
        val serialized = gson.toJson(original)
        return gson.fromJson(serialized, ConnectMessage::class.java)
    }
}