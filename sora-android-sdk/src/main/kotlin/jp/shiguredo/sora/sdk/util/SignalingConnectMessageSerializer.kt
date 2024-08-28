package jp.shiguredo.sora.sdk.util

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import jp.shiguredo.sora.sdk.channel.signaling.message.ConnectMessage
import java.lang.reflect.Type

class SignalingConnectMessageSerializer : JsonSerializer<ConnectMessage> {
    override fun serialize(
        src: ConnectMessage?,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        // SignalingMetadata が Map<String, Any> の場合、値が null の項目を JSON に含めるようにする
        val jsonObject = JsonObject()

        // serializeNulls を有効にした gson
        // metadata が Map<String, Any> の場合、値が null の項目を JSON に含めるようにするために使う
        val gsonSerializeNulls = GsonBuilder().serializeNulls().create()

        // ConnectMessageの他のフィールドを通常通りシリアライズ
        jsonObject.add("type", context.serialize(src?.type))
        jsonObject.add("role", context.serialize(src?.role))
        jsonObject.add("channel_id", context.serialize(src?.channelId))
        jsonObject.add("client_id", context.serialize(src?.clientId))
        jsonObject.add("bundle_id", context.serialize(src?.bundleId))

        // metadataフィールドはnullを含める
        // val metadataObject = JsonObject()
        // metadata が Map の場合、値が null の項目を JSON に含めるようにする

        // TODO(zztkm): metadataJson をログに出力したときは、jsonObject に追加するときは省略されていないが、Json 文字列に書き出す時には省略されているので調査
        // アイデア:
        // 一旦、普通に toJson する (null の項目は省略される)
        // toJson した結果をデシリアライズする
        // デシリアライズした ConnectMessage の metadata にオリジナルの metadata をセットする
        // serializeNulls でシリアライズする (これで実質 metadata だけ null の項目を含めることができるのでは？)
        val metadataJson = gsonSerializeNulls.toJsonTree(src?.metadata)
        Log.d("kensaku", "metadataJson: $metadataJson") // ここでは省略されなかった
        jsonObject.add("metadata", metadataJson)

        // 他のフィールドを追加する
        jsonObject.add("signaling_notify_metadata", context.serialize(src?.signalingNotifyMetadata))
        jsonObject.add("multistream", context.serialize(src?.multistream))
        jsonObject.add("spotlight", context.serialize(src?.spotlight))
        jsonObject.add("spotlight_number", context.serialize(src?.spotlightNumber))
        jsonObject.add("spotlight_focus_rid", context.serialize(src?.spotlightFocusRid))
        jsonObject.add("spotlight_unfocus_rid", context.serialize(src?.spotlightUnfocusRid))
        jsonObject.add("simulcast", context.serialize(src?.simulcast))
        jsonObject.add("simulcast_rid", context.serialize(src?.simulcastRid))
        jsonObject.add("video", context.serialize(src?.video))
        jsonObject.add("audio", context.serialize(src?.audio))
        jsonObject.add("sora_client", context.serialize(src?.soraClient))
        jsonObject.add("libwebrtc", context.serialize(src?.libwebrtc))
        jsonObject.add("environment", context.serialize(src?.environment))
        jsonObject.add("sdp", context.serialize(src?.sdp))
        jsonObject.add("data_channel_signaling", context.serialize(src?.dataChannelSignaling))
        jsonObject.add("ignore_disconnect_websocket", context.serialize(src?.ignoreDisconnectWebsocket))
        jsonObject.add("data_channels", context.serialize(src?.dataChannels))
        jsonObject.add("audio_streaming_language_code", context.serialize(src?.audioStreamingLanguageCode))
        jsonObject.add("redirect", context.serialize(src?.redirect))
        jsonObject.add("forwarding_filter", context.serialize(src?.forwardingFilter))

        return jsonObject
    }
}
