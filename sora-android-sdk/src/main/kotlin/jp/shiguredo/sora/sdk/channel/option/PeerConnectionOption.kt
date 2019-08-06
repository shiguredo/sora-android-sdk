package jp.shiguredo.sora.sdk.channel.option

/**
 * PeerConnection に関するオプションをまとめるクラスです
 */
class PeerConnectionOption {

    /**
     * PeerConnection の getStats() 統計情報を取得するインターバルのミリ秒
      *
     * 0 の場合、統計情報を取得しません。
     *
     * cf.
     * - https://w3c.github.io/webrtc-stats/
     * - https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/getStats
     */
    var getStatsIntervalMSec: Long = 0

    /**
     * libwebrtc internal tracer を使うかどうかのフラグ
     *
     * このフラグはプロセス内で最初の SoraMediaChannel 生成時のみ意味を持ちます。
     * 二度目以降では無視されます。
     *
     * デフォルトは false です。
     */
    var useLibwebrtcInternalTracer = false
}
