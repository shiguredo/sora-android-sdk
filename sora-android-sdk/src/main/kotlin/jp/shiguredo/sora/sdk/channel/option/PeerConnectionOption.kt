package jp.shiguredo.sora.sdk.channel.option

/**
 * PeerConnection に関するオプションをまとめるクラスです.
 */
class PeerConnectionOption {
    /**
     * PeerConnection の getStats() 統計情報を取得するインターバルのミリ秒.
     *
     * 0 の場合、統計情報を取得しません.
     *
     * cf.
     * - https://w3c.github.io/webrtc-stats/
     * - https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/getStats
     */
    var getStatsIntervalMSec: Long = 0
}
