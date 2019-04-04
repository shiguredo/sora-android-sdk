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