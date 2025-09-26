package jp.shiguredo.sora.sdk.channel.signaling.message

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import jp.shiguredo.sora.sdk.channel.option.SoraAudioOption
import jp.shiguredo.sora.sdk.channel.option.SoraChannelRole
import jp.shiguredo.sora.sdk.channel.option.SoraForwardingFilterOption
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.option.SoraVideoOption
import jp.shiguredo.sora.sdk.error.SoraDisconnectReason
import org.webrtc.RTCStats
import org.webrtc.RTCStatsReport

class SoraRTCStats(private val map: Map<String, Any>) : Map<String, Any> by map {
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

            if (mediaOption.simulcastEnabled) {
                msg.simulcast = mediaOption.simulcastEnabled
                msg.simulcastRid = mediaOption.simulcastRid?.toString()
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
            if (metadata != null) {
                connectMessageJsonObject.remove("metadata")
                connectMessageJsonObject.add("metadata", gsonSerializeNulls.toJsonTree(metadata))
            }
            if (signalingNotifyMetadata != null) {
                connectMessageJsonObject.remove("signalingNotifyMetadata")
                connectMessageJsonObject.add("signalingNotifyMetadata", gsonSerializeNulls.toJsonTree(signalingNotifyMetadata))
            }
            return gsonSerializeNulls.toJson(connectMessageJsonObject)
        }

        fun buildPongMessage(stats: RTCStatsReport?): String {
            return gson.toJson(
                PongMessage(
                    stats =
                        stats?.let {
                            stats.statsMap.values.map { stats -> SoraRTCStats(stats) }
                        },
                ),
            )
        }

        fun buildUpdateAnswerMessage(sdp: String): String {
            return gson.toJson(UpdateMessage(sdp = sdp))
        }

        fun buildReAnswerMessage(sdp: String): String {
            return gson.toJson(ReAnswerMessage(sdp = sdp))
        }

        fun buildAnswerMessage(sdp: String): String {
            return gson.toJson(AnswerMessage(sdp = sdp))
        }

        fun buildCandidateMessage(sdp: String): String {
            return gson.toJson(CandidateMessage(candidate = sdp))
        }

        fun buildStatsMessage(reports: RTCStatsReport): String {
            return gson.toJson(StatsMessage(reports = reports.statsMap.values.map { stats -> SoraRTCStats(stats) }))
        }

        fun buildDisconnectMessage(disconnectReason: SoraDisconnectReason?): String {
            return gson.toJson(DisconnectMessage(reason = disconnectReason?.value ?: null))
        }

        fun parseType(text: String): String? {
            val part = gson.fromJson(text, MessageCommonPart::class.java)
            return part.type
        }

        fun parseOfferMessage(text: String): OfferMessage {
            return gson.fromJson(text, OfferMessage::class.java)
        }

        fun parseSwitchMessage(text: String): SwitchedMessage {
            return gson.fromJson(text, SwitchedMessage::class.java)
        }

        /**
         * Sora 2022.1.0 で廃止されたため、現在は利用していません。
         */
        fun parseUpdateMessage(text: String): UpdateMessage {
            return gson.fromJson(text, UpdateMessage::class.java)
        }

        fun parseReOfferMessage(text: String): ReOfferMessage {
            return gson.fromJson(text, ReOfferMessage::class.java)
        }

        fun parseCloseMessage(text: String): CloseMessage {
            return gson.fromJson(text, CloseMessage::class.java)
        }

        fun parseNotificationMessage(text: String): NotificationMessage {
            return gson.fromJson(text, NotificationMessage::class.java)
        }

        fun parsePushMessage(text: String): PushMessage {
            return gson.fromJson(text, PushMessage::class.java)
        }

        fun parsePingMessage(text: String): PingMessage {
            return gson.fromJson(text, PingMessage::class.java)
        }

        fun parseReqStatsMessage(text: String): ReqStatsMessage {
            return gson.fromJson(text, ReqStatsMessage::class.java)
        }

        fun parseRedirectMessage(text: String): RedirectMessage {
            return gson.fromJson(text, RedirectMessage::class.java)
        }
    }
}
