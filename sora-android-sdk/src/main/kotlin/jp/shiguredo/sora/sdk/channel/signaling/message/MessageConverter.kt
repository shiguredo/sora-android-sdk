package jp.shiguredo.sora.sdk.channel.signaling.message

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import jp.shiguredo.sora.sdk.channel.option.SoraAudioOption
import jp.shiguredo.sora.sdk.channel.option.SoraChannelRole
import jp.shiguredo.sora.sdk.channel.option.SoraForwardingFilterOption
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.option.SoraVideoOption
import jp.shiguredo.sora.sdk.error.SoraDisconnectReason
import org.webrtc.RTCStats
import org.webrtc.RTCStatsReport

class SoraRTCStats(
    private val map: Map<String, Any>,
) : Map<String, Any> by map {
    constructor(stats: RTCStats) : this(
        mapOf(
            "id" to stats.id,
            "type" to stats.type,
            "timestamp" to stats.timestampUs,
        ) + stats.members,
    ) {}
}

class MessageConverter {
    companion object {
        val TAG = MessageConverter::class.simpleName

        val gson = Gson()
        private val gsonSerializeNulls = GsonBuilder().serializeNulls().create()!!

        @JvmOverloads
        fun buildConnectMessage(
            role: SoraChannelRole,
            channelId: String,
            dataChannelSignaling: Boolean?,
            ignoreDisconnectWebSocket: Boolean?,
            mediaOption: SoraMediaOption,
            metadata: Any?,
            sdp: String? = null,
            clientId: String? = null,
            bundleId: String? = null,
            signalingNotifyMetadata: Any? = null,
            dataChannels: List<Map<String, Any>>? = null,
            redirect: Boolean = false,
            forwardingFilterOption: SoraForwardingFilterOption? = null,
            forwardingFiltersOption: List<SoraForwardingFilterOption>? = null,
        ): String {
            val msg =
                ConnectMessage(
                    role = role.signaling,
                    channelId = channelId,
                    dataChannelSignaling = dataChannelSignaling,
                    ignoreDisconnectWebsocket = ignoreDisconnectWebSocket,
                    dataChannels = dataChannels,
                    metadata = metadata,
                    multistream = mediaOption.multistreamEnabled,
                    sdp = sdp,
                    clientId = clientId,
                    bundleId = bundleId,
                    signalingNotifyMetadata = signalingNotifyMetadata,
                    audioStreamingLanguageCode = mediaOption.audioStreamingLanguageCode,
                    forwardingFilter = forwardingFilterOption?.signaling,
                    forwardingFilters = forwardingFiltersOption?.map { it.signaling },
                )

            if (mediaOption.upstreamIsRequired) {
                // 配信者では audio, video は配信の設定
                if (mediaOption.audioUpstreamEnabled) {
                    if (!mediaOption.isDefaultAudioOption()) {
                        msg.audio =
                            AudioSetting().apply {
                                if (mediaOption.audioCodec != SoraAudioOption.Codec.DEFAULT) {
                                    codecType = mediaOption.audioCodec.toString()
                                }
                                mediaOption.audioBitrate?.let { bitRate = it }
                                mediaOption.audioOption.opusParams?.let { opusParams = it }
                            }
                    }
                } else {
                    msg.audio = false
                }

                if (mediaOption.videoUpstreamEnabled) {
                    // video 関連設定がすべてデフォルト値の場合は video フィールドの設定を省略する
                    if (!mediaOption.isDefaultVideoOption()) {
                        msg.video =
                            VideoSetting().apply {
                                if (mediaOption.videoCodec != SoraVideoOption.Codec.DEFAULT) {
                                    codecType = mediaOption.videoCodec.toString()
                                }
                                mediaOption.videoBitrate?.let { bitRate = it }
                                mediaOption.videoVp9Params?.let { vp9Params = it }
                                mediaOption.videoAv1Params?.let { av1Params = it }
                                mediaOption.videoH264Params?.let { h264Params = it }
                                mediaOption.videoH265Params?.let { h265Params = it }
                            }
                    }
                } else {
                    // ビデオを無効化したいため false を設定する
                    msg.video = false
                }
            } else {
                // 視聴者では audio, video は視聴の設定
                if (mediaOption.audioDownstreamEnabled) {
                    if (!mediaOption.isDefaultAudioOption()) {
                        msg.audio =
                            AudioSetting().apply {
                                if (mediaOption.audioCodec != SoraAudioOption.Codec.DEFAULT) {
                                    codecType = mediaOption.audioCodec.toString()
                                }
                                // TODO(shino): 視聴側の bit_rate 設定はサーバで無視される
                                mediaOption.audioBitrate?.let { bitRate = it }
                            }
                    }
                } else {
                    msg.audio = false
                }

                if (mediaOption.videoDownstreamEnabled) {
                    // video 関連設定がすべてデフォルト値の場合は video フィールドの設定を省略する
                    if (!mediaOption.isDefaultVideoOption()) {
                        msg.video =
                            VideoSetting().apply {
                                if (mediaOption.videoCodec != SoraVideoOption.Codec.DEFAULT) {
                                    codecType = mediaOption.videoCodec.toString()
                                }
                                // TODO(shino): 視聴側の bit_rate 設定はサーバで無視される
                                // TODO(zztkm): ビデオコーデック以外は配信者が設定できる項目で、視聴者は設定不要なので、設定不要な項目は省略する
                                mediaOption.videoBitrate?.let { bitRate = it }
                            }
                    }
                } else {
                    msg.video = false
                }
            }

            // サイマルキャストが有効な場合の設定
            if (mediaOption.simulcastEnabled) {
                msg.simulcast = mediaOption.simulcastEnabled

                // NOTE(zztkm): simulcastRid も本来は simulcastRequestRid 同様に
                // 受信するロールになっているかチェックすべきだが、ここの挙動を
                // 変えるのはユーザーに影響が出るため、そのままにしておく
                msg.simulcastRid = mediaOption.simulcastRid?.toString()
                if (role == SoraChannelRole.SENDRECV || role == SoraChannelRole.RECVONLY) {
                    msg.simulcastRequestRid = mediaOption.simulcastRequestRid?.toString()
                }
            }

            if (mediaOption.spotlightOption != null) {
                msg.spotlight = true
                msg.spotlightNumber = mediaOption.spotlightOption?.spotlightNumber
                msg.spotlightFocusRid = mediaOption.spotlightOption?.spotlightFocusRid?.toString()
                msg.spotlightUnfocusRid = mediaOption.spotlightOption?.spotlightUnfocusRid?.toString()
            }

            if (redirect) {
                msg.redirect = true
            }

            // 1部フィールドだけを null 許容して JSON 文字列にシリアライズするために以下の処理を行う
            // まず null 許容せずに JSON 文字列にシリアライズし、次に JsonObject にデシリアライズする
            // その後、デシリアライズした JsonObject の null 許容したいフィールドを設定し直し、SerializeNulls を有効にして JSON 文字列にシリアライズする
            // こうすることで、1部フィールドだけ null を許容した JSON 文字列を生成できる
            val jsonMsg = gson.toJson(msg)
            val connectMessageJsonObject = gson.fromJson(jsonMsg, JsonObject::class.java)
            // 未設定時と null 指定時だけでなく、空文字を指定した場合も metadata を送信しない。
            // 空文字の metadata を送信すると、認証ウェブフックに "metadata":"" が渡り、
            // アプリケーションサーバーの認証ロジックに影響する可能性があるためである。
            // gson.toJson(msg) の時点で metadata が JsonObject に含まれているため、
            // 条件の外で一度 remove してから、空文字以外の場合のみ追加し直す。
            // 空文字判定は String と JsonPrimitive にのみ適用する (Map や List は空でも送信する)。
            // JsonNull.INSTANCE は Kotlin の null ではなく JsonElement であるため、
            // null 相当として扱う防御的措置として除去対象に加える。
            // これは JsonElement 全般の入力を正式にサポートする意図ではない
            connectMessageJsonObject.remove("metadata")
            if (metadata != null && metadata !is JsonNull && !isMetadataEmpty(metadata)) {
                connectMessageJsonObject.add("metadata", gsonSerializeNulls.toJsonTree(metadata))
            }
            // signalingNotifyMetadata も metadata 側と同様に、null ・空文字・JsonNull ・
            // 空文字の JsonPrimitive を指定した場合は送信しない。
            // signaling_notify_metadata は他のクライアントの表示に使われるデータであり、
            // 空文字を送ると表示に問題が出る可能性があるためである。
            // キーは ConnectMessage の @SerializedName に合わせて snake_case で指定する
            connectMessageJsonObject.remove("signaling_notify_metadata")
            if (signalingNotifyMetadata != null && signalingNotifyMetadata !is JsonNull && !isMetadataEmpty(signalingNotifyMetadata)) {
                connectMessageJsonObject.add("signaling_notify_metadata", gsonSerializeNulls.toJsonTree(signalingNotifyMetadata))
            }
            return gsonSerializeNulls.toJson(connectMessageJsonObject)
        }

        fun buildPongMessage(stats: RTCStatsReport?): String =
            gson.toJson(
                PongMessage(
                    stats =
                        stats?.let {
                            stats.statsMap.values.map { stats -> SoraRTCStats(stats) }
                        },
                ),
            )

        fun buildUpdateAnswerMessage(sdp: String): String = gson.toJson(UpdateMessage(sdp = sdp))

        fun buildReAnswerMessage(sdp: String): String = gson.toJson(ReAnswerMessage(sdp = sdp))

        fun buildAnswerMessage(sdp: String): String = gson.toJson(AnswerMessage(sdp = sdp))

        fun buildCandidateMessage(sdp: String): String = gson.toJson(CandidateMessage(candidate = sdp))

        fun buildStatsMessage(reports: RTCStatsReport): String =
            gson.toJson(StatsMessage(reports = reports.statsMap.values.map { stats -> SoraRTCStats(stats) }))

        fun buildDisconnectMessage(disconnectReason: SoraDisconnectReason?): String =
            gson.toJson(DisconnectMessage(reason = disconnectReason?.value ?: null))

        fun parseType(text: String): String? {
            val part = gson.fromJson(text, MessageCommonPart::class.java)
            return part.type
        }

        fun parseOfferMessage(text: String): OfferMessage = gson.fromJson(text, OfferMessage::class.java)

        fun parseSwitchMessage(text: String): SwitchedMessage = gson.fromJson(text, SwitchedMessage::class.java)

        /**
         * Sora 2022.1.0 で廃止されたため、現在は利用していません。
         */
        fun parseUpdateMessage(text: String): UpdateMessage = gson.fromJson(text, UpdateMessage::class.java)

        fun parseReOfferMessage(text: String): ReOfferMessage = gson.fromJson(text, ReOfferMessage::class.java)

        fun parseCloseMessage(text: String): CloseMessage = gson.fromJson(text, CloseMessage::class.java)

        fun parseNotificationMessage(text: String): NotificationMessage = gson.fromJson(text, NotificationMessage::class.java)

        fun parsePushMessage(text: String): PushMessage = gson.fromJson(text, PushMessage::class.java)

        fun parsePingMessage(text: String): PingMessage = gson.fromJson(text, PingMessage::class.java)

        fun parseReqStatsMessage(text: String): ReqStatsMessage = gson.fromJson(text, ReqStatsMessage::class.java)

        fun parseRedirectMessage(text: String): RedirectMessage = gson.fromJson(text, RedirectMessage::class.java)

        // metadata が空文字相当かどうかを判定する。
        // String の空文字に加えて、JsonPrimitive の空文字 (JsonPrimitive("")) も対象とする。
        // JsonPrimitive("") は String ではないため、String 判定だけでは除去されない
        private fun isMetadataEmpty(metadata: Any?): Boolean =
            metadata is String && metadata.isEmpty() ||
                metadata is JsonPrimitive && metadata.isString && metadata.asString.isEmpty()
    }
}
