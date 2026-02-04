package jp.shiguredo.sora.sdk.channel.rpc

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * JSON-RPC の id を表す型.
 */
sealed class SoraRpcId {
    data object None : SoraRpcId()

    data class StringId(
        val value: String,
    ) : SoraRpcId()

    data class NumberId(
        val value: Long,
    ) : SoraRpcId()
}

/**
 * JSON-RPC エラー.
 */
data class SoraRpcError(
    val code: Int,
    val message: String,
    val data: String? = null,
)

/**
 * RPC 受信メッセージ.
 */
sealed class SoraRpcMessage {
    data class Response(
        val id: SoraRpcId,
        val result: JsonElement?,
    ) : SoraRpcMessage()

    data class Error(
        val id: SoraRpcId?,
        val error: SoraRpcError,
    ) : SoraRpcMessage()

    data class Notification(
        val method: String,
        val params: JsonElement?,
    ) : SoraRpcMessage()
}

/**
 * RPC 呼び出し結果.
 */
sealed class SoraRpcResult {
    data class Success(
        val id: SoraRpcId,
        val result: String?,
    ) : SoraRpcResult()

    data class Error(
        val id: SoraRpcId?,
        val error: SoraRpcError,
    ) : SoraRpcResult()
}

/**
 * RPC 呼び出し時のローカルエラー要因.
 */
enum class SoraRpcErrorReason(
    val message: String,
) {
    NOT_AVAILABLE("RPC is not available"),
    DATA_CHANNEL_UNAVAILABLE("RPC DataChannel is not available"),
    DATA_CHANNEL_CLOSED("RPC DataChannel is not open"),
    PEER_UNAVAILABLE("PeerChannel is not available"),
    SEND_FAILED("Failed to send RPC message"),
    TIMEOUT("RPC response timed out"),
    PARSE_ERROR("Failed to parse RPC message"),
}

/**
 * RPC 呼び出し時の例外.
 */
class SoraRpcException(
    val reason: SoraRpcErrorReason,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: reason.message, cause)

/**
 * JSON-RPC メッセージパーサー.
 */
object SoraRpcParser {
    fun parse(text: String): SoraRpcMessage? {
        val jsonElement =
            try {
                JsonParser.parseString(text)
            } catch (e: Exception) {
                return null
            }

        if (!jsonElement.isJsonObject) {
            return null
        }

        val json = jsonElement.asJsonObject
        val jsonrpc = json.get("jsonrpc")
        if (
            jsonrpc == null ||
            !jsonrpc.isJsonPrimitive ||
            !jsonrpc.asJsonPrimitive.isString ||
            jsonrpc.asString != "2.0"
        ) {
            return null
        }

        // JSON-RPC 2.0 の Response Object 種別は result/error のいずれかのキーを持つことで識別できるため、
        // result (success) -> error の順で判定し、最初に一致したものをパース結果として返す。
        // どちらにも該当しない場合は null を返す
        val parsed = parseResponse(json) ?: parseError(json)
        return parsed
    }

    private fun parseResponse(json: JsonObject): SoraRpcMessage.Response? {
        if (!json.has("result")) {
            return null
        }
        val id = parseId(json.get("id")) ?: return null
        return SoraRpcMessage.Response(id, json.get("result"))
    }

    private fun parseError(json: JsonObject): SoraRpcMessage.Error? {
        if (!json.has("error")) {
            return null
        }
        val errorElement = json.get("error")
        if (errorElement == null || !errorElement.isJsonObject) {
            return null
        }
        val errorObject = errorElement.asJsonObject
        val codeElement = errorObject.get("code")
        val messageElement = errorObject.get("message")
        if (
            codeElement == null ||
            !codeElement.isJsonPrimitive ||
            !codeElement.asJsonPrimitive.isNumber
        ) {
            return null
        }
        if (
            messageElement == null ||
            !messageElement.isJsonPrimitive ||
            !messageElement.asJsonPrimitive.isString
        ) {
            return null
        }
        val code = codeElement.asInt
        val message = messageElement.asString
        val data =
            errorObject.get("data")?.let { element ->
                if (element.isJsonNull) {
                    null
                } else {
                    element.toString()
                }
            }
        val id = parseId(json.get("id"))
        return SoraRpcMessage.Error(id, SoraRpcError(code = code, message = message, data = data))
    }

    private fun parseId(element: JsonElement?): SoraRpcId? {
        if (element == null || element.isJsonNull) {
            return null
        }

        return try {
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString ->
                    SoraRpcId.StringId(element.asString)
                element.isJsonPrimitive && element.asJsonPrimitive.isNumber ->
                    SoraRpcId.NumberId(element.asLong)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

internal fun SoraRpcId?.key(): String? =
    when (this) {
        is SoraRpcId.NumberId -> value.toString()
        is SoraRpcId.StringId -> value
        else -> null
    }
