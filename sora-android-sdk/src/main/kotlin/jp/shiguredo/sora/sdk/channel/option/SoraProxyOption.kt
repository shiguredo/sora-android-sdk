package jp.shiguredo.sora.sdk.channel.option

import org.webrtc.ProxyType

/**
 * プロキシーに関するオプションをまとめるクラスです.
 */
class SoraProxyOption {

    /** 種類 */
    var type = ProxyType.NONE

    /** エージェント */
    var agent: String = "Sora Android SDK"

    /** ホスト名 */
    var hostname: String = ""

    /** ポート */
    var port: Int = 0

    /** ユーザー名 */
    var username: String = ""

    /** パスワード */
    var password: String = ""

    override fun toString(): String {
        return "type=$type, agent=$agent, hostname=$hostname, port=$port, username=$username, password=${"*".repeat(password.length)}"
    }
}
