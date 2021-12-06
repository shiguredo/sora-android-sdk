package jp.shiguredo.sora.sdk

import com.google.gson.Gson
import jp.shiguredo.sora.sdk.channel.signaling.message.ConnectMessage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ConnectClientIdTest {
    val gson = Gson()

    // JSON としては client_id は入ってはいけない。
    // 入ると sora 19.04 より前ではコケる。
    @Test
    fun serializeNullClientIdShouldNotContainTheField() {
        val message = roundtrip(null)
        assertFalse { message.keys.contains("client_id") }
    }

    @Test
    fun serializeStringClientIdAsIs() {
        val message = roundtrip("It's me")
        assertEquals("It's me", message["client_id"])
    }

    private fun roundtrip(clientId: String?): Map<*, *> {
        val original = ConnectMessage(
            role = "upstream",
            channelId = "sora",
            sdp = "",
            clientId = clientId
        )
        val serialized = gson.toJson(original)
        return gson.fromJson(serialized, Map::class.java)
    }
}
