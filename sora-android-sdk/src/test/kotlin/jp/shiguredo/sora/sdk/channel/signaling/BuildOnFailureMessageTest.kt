package jp.shiguredo.sora.sdk.channel.signaling

import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildOnFailureMessageTest {
    // Throwable と Response から onError の message を組み立てる関数のテスト

    // response がない場合は例外の toString() のみが返ること
    // Wi-Fi 切断や URL 不正など、HTTP レスポンスが返らないケースを想定する
    @Test
    fun `response がない場合は例外の toString のみが返ること`() {
        val t = java.net.UnknownHostException("Unable to resolve host \"sora.example.com\"")
        val message = buildOnFailureMessage(t, null)
        assertEquals(t.toString(), message)
        assertTrue(message.startsWith("java.net.UnknownHostException"))
    }

    // response がある場合は例外の toString に HTTP ステータスコードと理由句が付与されること
    // HTTP エラー (404 など) のケースを想定する
    @Test
    fun `response がある場合は HTTP ステータスコードと理由句が付与されること`() {
        val t = IllegalStateException("expected HTTP 101 but was 404")
        val response = buildResponse(404, "Not Found")
        val message = buildOnFailureMessage(t, response)
        assertEquals("$t (HTTP 404 Not Found)", message)
        assertTrue(message.contains("(HTTP 404 Not Found)"))
    }

    // response のヘッダーや本文が message に含まれないこと
    // 機密情報 (Authorization ヘッダー等) や長大な本文が漏えいしないことを確認する
    @Test
    fun `response のヘッダーと本文は message に含まれないこと`() {
        val t = IllegalStateException("expected HTTP 101 but was 401")
        val response =
            Response
                .Builder()
                .code(401)
                .message("Unauthorized")
                .request(
                    okhttp3.Request
                        .Builder()
                        .url("https://sora.example.com/signaling")
                        .build(),
                ).protocol(okhttp3.Protocol.HTTP_1_1)
                .header("Authorization", "Bearer secret-token")
                .body(ResponseBody.create(null, "{\"error\":\"unauthorized\"}"))
                .build()
        val message = buildOnFailureMessage(t, response)
        assertTrue(!message.contains("secret-token"))
        assertTrue(!message.contains("unauthorized"))
    }

    private fun buildResponse(
        code: Int,
        message: String,
    ): Response =
        Response
            .Builder()
            .code(code)
            .message(message)
            .request(
                okhttp3.Request
                    .Builder()
                    .url("https://sora.example.com/signaling")
                    .build(),
            ).protocol(okhttp3.Protocol.HTTP_1_1)
            .build()
}
