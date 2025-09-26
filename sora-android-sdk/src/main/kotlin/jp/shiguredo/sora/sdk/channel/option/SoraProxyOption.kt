package jp.shiguredo.sora.sdk.channel.option

import jp.shiguredo.sora.sdk.util.SDKInfo
import org.webrtc.ProxyType

/**
 * プロキシに関するオプションをまとめるクラスです.
 */
class SoraProxyOption {
    /** 種類 */
    var type = ProxyType.NONE

    /** エージェント */
    var agent: String = SDKInfo.sdkInfo()

    /** ホスト名 */
    var hostname: String = ""

    /** ポート */
    var port: Int = 0

    /** ユーザー名 */
    var username: String = ""

    /** パスワード */
    var password: String = ""

    override fun toString(): String {
        return "type=$type, hostname=$hostname, port=$port, username=$username, password=${"*".repeat(password.length)}, agent=$agent"
    }
}
