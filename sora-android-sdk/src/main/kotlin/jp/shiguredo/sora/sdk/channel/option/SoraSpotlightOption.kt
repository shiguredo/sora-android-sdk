package jp.shiguredo.sora.sdk.channel.option

/**
 * スポットライト機能のオプションです。
 */
class SoraSpotlightOption {

    /**
     * サイマルキャストの rid
     */
    var simulcastRid: SoraVideoOption.SimulcastRid? = null

    /**
     * スポットライト機能のアクティブな配信数を指定します
     *
     * cf.
     * - Sora ドキュメントのスポットライト機能
     *   [](https://sora.shiguredo.jp/doc/SPOTLIGHT.html)
     */
    var spotlightNumber: Int? = null

}