package jp.shiguredo.sora.sdk

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.shiguredo.sora.sdk.channel.option.SoraChannelRole
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.message.ConnectMessage
import jp.shiguredo.sora.sdk.channel.signaling.message.MessageConverter
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
        val message =
            roundtrip(
                hashMapOf(
                    "foo" to 1,
                    "bar" to "baz",
                    "baz" to listOf("ham", "eggs", "bacon"),
                ),
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

    // ここから下は MessageConverter.buildConnectMessage の metadata に関するテスト

    // SoraMediaChannel の signalingMetadata を未設定にした場合は metadata を送信しないこと
    // SoraMediaChannel のデフォルト値は空文字であるため、空文字を渡すことで未設定を再現する
    @Test
    fun `buildConnectMessage で metadata 未設定なら metadata を含まないこと`() {
        val message = buildConnectMessage(metadata = "")
        assertFalse(message.has("metadata"))
    }

    // buildConnectMessage で metadata に null を指定した場合は metadata を送信しないこと
    @Test
    fun `buildConnectMessage で metadata が null なら metadata を含まないこと`() {
        val message = buildConnectMessage(metadata = null)
        assertFalse(message.has("metadata"))
    }

    // buildConnectMessage で metadata に空文字を明示的に指定した場合も metadata を送信しないこと
    @Test
    fun `buildConnectMessage で metadata が空文字なら metadata を含まないこと`() {
        val message = buildConnectMessage(metadata = "")
        assertFalse(message.has("metadata"))
    }

    // buildConnectMessage で metadata に空文字以外を指定した場合は metadata を送信すること
    @Test
    fun `buildConnectMessage で metadata に文字列を指定した場合は metadata を含むこと`() {
        val message = buildConnectMessage(metadata = "str")
        assertTrue(message.has("metadata"))
        assertEquals("str", message["metadata"]?.asString)
    }

    private fun buildConnectMessage(metadata: Any?): JsonObject {
        val serialized =
            MessageConverter.buildConnectMessage(
                role = SoraChannelRole.SENDRECV,
                channelId = "sora",
                dataChannelSignaling = null,
                ignoreDisconnectWebSocket = null,
                mediaOption = SoraMediaOption(),
                metadata = metadata,
            )
        return JsonParser.parseString(serialized).asJsonObject
    }

    private fun roundtrip(metadata: Any?): ConnectMessage {
        val original =
            ConnectMessage(
                role = "sendonly",
                channelId = "sora",
                sdp = "",
                metadata = metadata,
            )
        val serialized = gson.toJson(original)
        return gson.fromJson(serialized, ConnectMessage::class.java)
    }
}
