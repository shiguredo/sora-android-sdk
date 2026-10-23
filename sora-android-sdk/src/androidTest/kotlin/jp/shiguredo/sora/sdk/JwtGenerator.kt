package jp.shiguredo.sora.sdk

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// JWT (HS256) を生成するテスト用ユーティリティ。
// js-sdk の e2e テスト (e2e-tests/src/misc.ts の generateJwt) に相当する。
// サードパーティーライブラリは使わず、Java 標準 API (javax.crypto.Mac) と
// android.util.Base64 だけで実装する。
//
// テストサーバー側はこの JWT の private claims (rpc_methods 等) を検証し、
// 認証成功時の払い出しに反映する (js-sdk のテストサーバー固有機能)。
internal object JwtGenerator {
    private const val TAG = "JwtGenerator"
    private val gson = Gson()

    // JS SDK の generateJwt と同じ形式で HS256 JWT を生成する
    // (e2e-tests/src/misc.ts:6-24 に相当)
    fun generate(
        channelId: String,
        secretKey: String,
        privateClaims: Map<String, Any> = emptyMap(),
    ): String {
        // ヘッダー (HS256)
        val header = """{"alg":"HS256","typ":"JWT"}""".toByteArray(Charsets.UTF_8)
        val headerB64 = base64UrlEncode(header)

        // payload (channel_id + private claims)
        val payloadJson =
            JsonObject().apply {
                addProperty("channel_id", channelId)
                for ((key, value) in privateClaims) {
                    add(key, gson.toJsonTree(value))
                }
            }
        val payloadB64 = base64UrlEncode(payloadJson.toString().toByteArray(Charsets.UTF_8))

        // 署名 (HMAC-SHA256)
        val signingInput = "$headerB64.$payloadB64"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = base64UrlEncode(mac.doFinal(signingInput.toByteArray(Charsets.UTF_8)))

        return "$signingInput.$signature"
    }

    // base64url (パディングなし + URL 安全文字)
    private fun base64UrlEncode(data: ByteArray): String =
        Base64.encodeToString(data, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
}
