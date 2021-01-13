package jp.shiguredo.sora.sdk.channel.option

import jp.shiguredo.sora.sdk.channel.signaling.message.SimulcastRid

class SoraSpotlightOption {

    var simulcastRid: SimulcastRid? = null

    /**
     * スポットライト機能のアクティブな配信数を指定します
     *
     * cf.
     * - Sora ドキュメントのスポットライト機能
     *   [](https://sora.shiguredo.jp/doc/SPOTLIGHT.html)
     */
    var activeSpeakerLimit: Int? = null
    var legacyEnabled: Boolean = false

}