package jp.shiguredo.sora.sdk

import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
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

    // ここから下は MessageConverter.buildConnectMessage の metadata と signaling_notify_metadata に関するテスト

    // buildConnectMessage で metadata に null を指定した場合 (未指定時) は metadata を送信しないこと
    // SoraMediaChannel の signalingMetadata のデフォルト値は null であり (SoraMediaChannel.kt 参照)、
    // 未指定の場合は null が buildConnectMessage へ渡るため、このテストは未設定経路を代表する
    @Test
    fun `buildConnectMessage で metadata が null なら metadata を含まないこと`() {
        val message = buildConnectMessage(metadata = null)
        assertCommonMessageFields(message)
        assertFalse(message.has("metadata"))
    }

    // buildConnectMessage で metadata に空文字を明示的に指定した場合は空文字のまま送信されること
    // 未指定時 (null) と区別するため、空文字を送りたい場合は明示的に空文字を指定する
    @Test
    fun `buildConnectMessage で metadata が空文字なら空文字のまま送信すること`() {
        val message = buildConnectMessage(metadata = "")
        assertCommonMessageFields(message)
        assertTrue(message.has("metadata"))
        assertEquals("", message["metadata"]?.asString)
    }

    // buildConnectMessage で metadata に空文字以外の文字列を指定した場合は metadata を送信すること
    @Test
    fun `buildConnectMessage で metadata に文字列を指定した場合は metadata を含むこと`() {
        val message = buildConnectMessage(metadata = "str")
        assertCommonMessageFields(message)
        assertTrue(message.has("metadata"))
        assertEquals("str", message["metadata"]?.asString)
    }

    // buildConnectMessage で metadata に JsonNull を指定した場合も metadata を送信しないこと
    // JsonNull.INSTANCE は Kotlin の null ではなく JsonElement のため、
    // null 指定時と同じ扱いにする必要がある
    @Test
    fun `buildConnectMessage で metadata が JsonNull なら metadata を含まないこと`() {
        val message = buildConnectMessage(metadata = JsonNull.INSTANCE)
        assertCommonMessageFields(message)
        assertFalse(message.has("metadata"))
    }

    // buildConnectMessage で metadata に JsonPrimitive の空文字を明示的に指定した場合も
    // 空文字のまま送信されること (String の空文字と同様に扱う)
    @Test
    fun `buildConnectMessage で metadata が JsonPrimitive の空文字なら空文字のまま送信すること`() {
        val message = buildConnectMessage(metadata = JsonPrimitive(""))
        assertCommonMessageFields(message)
        assertTrue(message.has("metadata"))
        assertEquals("", message["metadata"]?.asString)
    }

    // buildConnectMessage で metadata に数値の JsonPrimitive を指定した場合も metadata を送信すること
    // metadata は null と JsonNull のみ送信しないため、数値の JsonPrimitive は送信される
    @Test
    fun `buildConnectMessage で metadata が数値の JsonPrimitive なら metadata を含むこと`() {
        val message = buildConnectMessage(metadata = JsonPrimitive(123))
        assertCommonMessageFields(message)
        assertTrue(message.has("metadata"))
        assertEquals(123, message["metadata"]?.asInt)
    }

    // buildConnectMessage で metadata に Map を指定した場合は metadata を送信すること
    @Test
    fun `buildConnectMessage で metadata に Map を指定した場合は metadata を含むこと`() {
        val message = buildConnectMessage(metadata = mapOf("foo" to 1))
        assertCommonMessageFields(message)
        assertTrue(message.has("metadata"))
        assertEquals(1, message["metadata"]?.asJsonObject?.get("foo")?.asInt)
    }

    // buildConnectMessage で metadata に空の Map を指定した場合も metadata を送信すること
    @Test
    fun `buildConnectMessage で metadata が空の Map なら metadata を含むこと`() {
        val message = buildConnectMessage(metadata = mapOf<String, Any>())
        assertCommonMessageFields(message)
        assertTrue(message.has("metadata"))
    }

    // buildConnectMessage で signalingNotifyMetadata に null を含む Map を指定した場合、
    // 正しいキー (signaling_notify_metadata) でネスト null (Map の値に含まれる null) も送信されること
    // キーは ConnectMessage の @SerializedName に合わせて snake_case になるため、
    // camelCase のキー (signalingNotifyMetadata) が含まれないことも検証する
    @Test
    fun `buildConnectMessage で signalingNotifyMetadata に null を含む Map を指定した場合は正しいキーで送信すること`() {
        val message = buildConnectMessage(signalingNotifyMetadata = mapOf("foo" to null))
        assertCommonMessageFields(message)
        assertTrue(message.has("signaling_notify_metadata"))
        assertFalse(message.has("signalingNotifyMetadata"))
        val notifyMetadata = message["signaling_notify_metadata"]?.asJsonObject
        assertTrue(notifyMetadata?.has("foo") == true)
        assertTrue(notifyMetadata?.get("foo")?.isJsonNull == true)
    }

    // buildConnectMessage で signalingNotifyMetadata に空文字を明示的に指定した場合は、
    // 空文字のまま送信されること (metadata 側と同様に空文字は除去しない)
    @Test
    fun `buildConnectMessage で signalingNotifyMetadata が空文字なら空文字のまま送信すること`() {
        val message = buildConnectMessage(signalingNotifyMetadata = "")
        assertCommonMessageFields(message)
        assertTrue(message.has("signaling_notify_metadata"))
        assertEquals("", message["signaling_notify_metadata"]?.asString)
    }

    // buildConnectMessage で signalingNotifyMetadata に空文字の JsonPrimitive を指定した場合も
    // 空文字のまま送信されること (metadata 側と同様に空文字は除去しない)
    @Test
    fun `buildConnectMessage で signalingNotifyMetadata が空文字の JsonPrimitive なら空文字のまま送信すること`() {
        val message = buildConnectMessage(signalingNotifyMetadata = JsonPrimitive(""))
        assertCommonMessageFields(message)
        assertTrue(message.has("signaling_notify_metadata"))
        assertEquals("", message["signaling_notify_metadata"]?.asString)
    }

    // buildConnectMessage で signalingNotifyMetadata に数値の JsonPrimitive を指定した場合も送信すること
    @Test
    fun `buildConnectMessage で signalingNotifyMetadata が数値の JsonPrimitive なら signaling_notify_metadata を含むこと`() {
        val message = buildConnectMessage(signalingNotifyMetadata = JsonPrimitive(123))
        assertCommonMessageFields(message)
        assertTrue(message.has("signaling_notify_metadata"))
        assertEquals(123, message["signaling_notify_metadata"]?.asInt)
    }

    // buildConnectMessage で signalingNotifyMetadata に null を指定した場合 (未指定時) は送信しないこと
    @Test
    fun `buildConnectMessage で signalingNotifyMetadata が null なら signaling_notify_metadata を含まないこと`() {
        val message = buildConnectMessage(signalingNotifyMetadata = null)
        assertCommonMessageFields(message)
        assertFalse(message.has("signaling_notify_metadata"))
    }

    // buildConnectMessage で signalingNotifyMetadata に JsonNull を指定した場合は送信しないこと
    // metadata 側と同様に、JsonNull.INSTANCE は null 相当として扱い除去する
    @Test
    fun `buildConnectMessage で signalingNotifyMetadata が JsonNull なら signaling_notify_metadata を含まないこと`() {
        val message = buildConnectMessage(signalingNotifyMetadata = JsonNull.INSTANCE)
        assertCommonMessageFields(message)
        assertFalse(message.has("signaling_notify_metadata"))
    }

    private fun buildConnectMessage(
        metadata: Any? = null,
        signalingNotifyMetadata: Any? = null,
    ): JsonObject {
        val serialized =
            MessageConverter.buildConnectMessage(
                role = SoraChannelRole.SENDRECV,
                channelId = "sora",
                dataChannelSignaling = null,
                ignoreDisconnectWebSocket = null,
                mediaOption = SoraMediaOption(),
                metadata = metadata,
                signalingNotifyMetadata = signalingNotifyMetadata,
            )
        return JsonParser.parseString(serialized).asJsonObject
    }

    // metadata の有無にかかわらず、connect メッセージの主要キーが壊れていないことを検証する
    private fun assertCommonMessageFields(message: JsonObject) {
        assertEquals("connect", message["type"]?.asString)
        assertEquals("sendrecv", message["role"]?.asString)
        assertEquals("sora", message["channel_id"]?.asString)
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
