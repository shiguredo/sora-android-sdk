package jp.shiguredo.sora.sdk.ng

class MediaConstraints {

    var mandatory: MutableMap<String, Any> = mutableMapOf()
    var optional: MutableMap<String, Any> = mutableMapOf()

    fun toNative(): org.webrtc.MediaConstraints {
        var native = org.webrtc.MediaConstraints()
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