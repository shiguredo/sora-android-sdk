package jp.shiguredo.sora.sdk

import com.google.gson.Gson
import jp.shiguredo.sora.sdk.channel.signaling.message.ConnectMessage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ConnectMetadataJsonTest {
    val gson = Gson()

    @Test
    fun noMetadata() {
        val message = roundtrip(null)
        assertEquals(null, message.metadata)
    }

    // sora-android-sdk 1.8.0 までは String? で受けていた。
    // JSON 文字列をもらったら JSON 文字列に戻る必要がある。
    @Test
    fun stringifiedMetadata() {
        val message1 = roundtrip("str")
        assertEquals("str", message1.metadata)

        val message2 = roundtrip("{\"foo\": 1, \"bar\": \"baz\"}")
        assertEquals("{\"foo\": 1, \"bar\": \"baz\"}", message2.metadata)

        val message3 = roundtrip("[1, 2, 3, \"DAAAAAAAA!!!!\"]")
        assertEquals("[1, 2, 3, \"DAAAAAAAA!!!!\"]", message3.metadata)
    }

    @Test fun listMetadata1() {
        val message = roundtrip(listOf(1, 2, 3))
        if (message.metadata !is List<*>) {
            fail("metadata should be list: ${message.metadata}")
        }
        val metadata = message.metadata as List<*>
        assertEquals(3, metadata.size)
        assertEquals(1.0, metadata[0])
        assertEquals(2.0, metadata[1])
        assertEquals(3.0, metadata[2])
    }

    @Test fun listMetadata2() {
        val message = roundtrip(listOf("foo", "bar", "baz"))
        if (message.metadata !is List<*>) {
            fail("metadata should be list: ${message.metadata}")
        }
        val metadata = message.metadata as List<*>
        assertEquals(3, metadata.size)
        assertEquals("foo", metadata[0])
        assertEquals("bar", metadata[1])
        assertEquals("baz", metadata[2])
    }

    @Test fun mapMetadata() {
        val message = roundtrip(
            hashMapOf(
                "foo" to 1,
                "bar" to "baz",
                "baz" to listOf("ham", "eggs", "bacon")
            )
        )
        if (message.metadata !is Map<*, *>) {
            fail("metadata should be map: ${message.metadata}")
        }
        val metadata = message.metadata as Map<*, *>
        assertEquals(3, metadata.size)
        assertEquals(1.0, metadata["foo"])
        assertEquals("baz", metadata["bar"])
        assertEquals(listOf("ham", "eggs", "bacon"), metadata["baz"])
    }

    @Test fun setMetadata() {
        val message = roundtrip(setOf(1, 2, 3))
        // Set は List で返ってくる
        if (message.metadata !is List<*>) {
            fail("metadata should be set: ${message.metadata}")
        }
        val metadata = message.metadata as List<*>
        assertEquals(3, metadata.size)
        assertEquals(listOf(1.0, 2.0, 3.0), metadata)
    }

    private fun roundtrip(metadata: Any?): ConnectMessage {
        val original = ConnectMessage(
            role = "sendonly",
            channelId = "sora",
            sdp = "",
            metadata = metadata
        )
        val serialized = gson.toJson(original)
        return gson.fromJson(serialized, ConnectMessage::class.java)
    }
}
