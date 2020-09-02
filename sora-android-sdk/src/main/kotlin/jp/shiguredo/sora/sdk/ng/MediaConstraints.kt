package jp.shiguredo.sora.sdk.ng

/**
 * メディアの制約を定義します。
 */
class MediaConstraints {

    /**
     * 必須のパラメーター
     */
    var mandatory: MutableMap<String, Any> = mutableMapOf()

    /**
     * 任意のパラメーター
     */
    var optional: MutableMap<String, Any> = mutableMapOf()

    internal fun toNative(): org.webrtc.MediaConstraints {
        val native = org.webrtc.MediaConstraints()
        for ((key, value) in mandatory) {
            val pair = org.webrtc.MediaConstraints.KeyValuePair(key, value.toString())
            native.mandatory.add(pair)
        }
        for ((key, value) in optional) {
            val pair = org.webrtc.MediaConstraints.KeyValuePair(key, value.toString())
            native.optional.add(pair)
        }
        return native
    }

}