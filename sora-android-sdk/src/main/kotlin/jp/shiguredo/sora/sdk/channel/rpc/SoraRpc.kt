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
enum class SoraRpcErrorReason {
    NOT_AVAILABLE,
    DATA_CHANNEL_UNAVAILABLE,
    DATA_CHANNEL_CLOSED,
    PEER_UNAVAILABLE,
    SEND_FAILED,
    TIMEOUT,
    PARSE_ERROR,
}

/**
 * RPC 呼び出し時の例外.
 */
class SoraRpcException(
    val reason: SoraRpcErrorReason,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: reason.name, cause)

/**
 * JSON-RPC メッセージパーサー.
 */
class SoraRpcParser {
    fun parse(text: String): SoraRpcMessage? {
        return try {
            val json = JsonParser.parseString(text).asJsonObject
            if (json.get("jsonrpc")?.asString != "2.0") {
                return null
            }
            parseResponse(json) ?: parseError(json)
        } catch (e: Exception) {
            null
        }
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
        val errorObject = json.getAsJsonObject("error")
        val code = errorObject.get("code")?.asInt ?: return null
        val message = errorObject.get("message")?.asString ?: return null
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
