package jp.shiguredo.sora.sdk.channel.rtc

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.webrtc.IceCandidateErrorEvent
import org.webrtc.ProxyType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PeerChannelIceCandidateErrorTest {
    // libwebrtc の SocketAddress::HostAsSensitiveURIString() は IPv4 の最終オクテットを
    // .x に置換する。127.0.0.0/8 はすべてループバックであるため、匿名化後も 127. で始まる
    @Test
    fun `匿名化された IPv4 ループバックはループバックと判定されること`() {
        assertTrue(PeerChannelImpl.isLoopbackSensitiveAddress("127.0.0.x"))
        assertTrue(PeerChannelImpl.isLoopbackSensitiveAddress("127.255.255.x"))
    }

    // libwebrtc の SocketAddress::HostAsSensitiveURIString() は IPv6 を
    // 先頭 48 bit だけ残した [%x:%x:%x:x:x:x:x:x] にする。
    // ::1 は先頭 48 bit が 0 のため [0:0:0:x:x:x:x:x] になる
    @Test
    fun `匿名化された IPv6 ループバックはループバックと判定されること`() {
        assertTrue(PeerChannelImpl.isLoopbackSensitiveAddress("[0:0:0:x:x:x:x:x]"))
        // ブラケットの有無に依存しないことを確認する
        assertTrue(PeerChannelImpl.isLoopbackSensitiveAddress("0:0:0:x:x:x:x:x"))
    }

    // Wi-Fi やセルラーのプライベートアドレスはループバックではないため、除外してはならない
    @Test
    fun `匿名化されたプライベートアドレスはループバックと判定されないこと`() {
        assertFalse(PeerChannelImpl.isLoopbackSensitiveAddress("192.168.40.x"))
        assertFalse(PeerChannelImpl.isLoopbackSensitiveAddress("10.0.105.x"))
        // 127. の近傍値が誤ってループバック扱いされないことを確認する
        assertFalse(PeerChannelImpl.isLoopbackSensitiveAddress("128.0.0.x"))
    }

    // グローバル IPv6 は先頭 48 bit が 0 ではないため、ループバックと判定してはならない
    @Test
    fun `匿名化されたグローバル IPv6 はループバックと判定されないこと`() {
        assertFalse(PeerChannelImpl.isLoopbackSensitiveAddress("[2400:4051:1234:x:x:x:x:x]"))
    }

    // 3 番目のグループが 0 でない場合、先頭 48 bit は 0 ではないためループバックではない
    @Test
    fun `先頭 48 bit が 0 でない IPv6 はループバックと判定されないこと`() {
        assertFalse(PeerChannelImpl.isLoopbackSensitiveAddress("[0:0:1:x:x:x:x:x]"))
    }

    // TURN の TCP/TLS でサーバーがプライベート IP の場合など、address が空になることがある
    @Test
    fun `空文字はループバックと判定されないこと`() {
        assertFalse(PeerChannelImpl.isLoopbackSensitiveAddress(""))
    }

    // プロキシ未使用時は、匿名化されたループバック表現のイベントを出力しない
    @Test
    fun `プロキシ未使用時のループバックは出力しないこと`() {
        assertFalse(PeerChannelImpl.shouldLogIceCandidateError(ProxyType.NONE, "127.0.0.x"))
        assertFalse(PeerChannelImpl.shouldLogIceCandidateError(ProxyType.NONE, "[0:0:0:x:x:x:x:x]"))
    }

    // プロキシ経由の TURN/TCP ではローカルソケットが localhost に束縛されることがあるため、
    // プロキシ設定時はループバックの失敗も出力する
    @Test
    fun `プロキシ設定時のループバックは出力すること`() {
        assertTrue(PeerChannelImpl.shouldLogIceCandidateError(ProxyType.HTTPS, "127.0.0.x"))
        // SOCKS5 でも HTTPS と同じく、プロキシ設定時として扱われることを確認する
        assertTrue(PeerChannelImpl.shouldLogIceCandidateError(ProxyType.SOCKS5, "127.0.0.x"))
    }

    // プロキシ未使用でも、ループバック以外の失敗は出力する
    @Test
    fun `プロキシ未使用時の非ループバックは出力すること`() {
        assertTrue(PeerChannelImpl.shouldLogIceCandidateError(ProxyType.NONE, "192.168.40.x"))
    }

    // formatIceCandidateError のログが 5 つのフィールドを 1 行で含むこと
    // カンマと半角スペースの区切り、errorText が末尾であることを固定する
    @Test
    fun `formatIceCandidateError が 5 つのフィールドを 1 行で含むこと`() {
        val event =
            IceCandidateErrorEvent(
                "192.168.40.x",
                50000,
                "turn:sora.example.com:443?transport=tcp",
                701,
                "Failed to establish connection",
            )
        assertEquals(
            "[rtc] @onIceCandidateError address=192.168.40.x, port=50000," +
                " url=turn:sora.example.com:443?transport=tcp, errorCode=701," +
                " errorText=Failed to establish connection",
            PeerChannelImpl.formatIceCandidateError(event),
        )
    }

    // TURN の TCP/TLS でサーバーがプライベート IP の場合は address が空、port が 0 になる
    // その場合も key=value の書式が崩れないことを確認する
    @Test
    fun `address が空で port が 0 のイベントも書式どおり出力されること`() {
        val event =
            IceCandidateErrorEvent(
                "",
                0,
                "turns:sora.example.com:443?transport=tcp",
                701,
                "Failed to establish connection",
            )
        assertEquals(
            "[rtc] @onIceCandidateError address=, port=0," +
                " url=turns:sora.example.com:443?transport=tcp, errorCode=701," +
                " errorText=Failed to establish connection",
            PeerChannelImpl.formatIceCandidateError(event),
        )
    }

    // errorText はサーバー由来の自由文字列であり、改行を含むと 1 行のログが崩れるため
    // 空白に置換されることを確認する。CRLF は 1 つの空白になる
    @Test
    fun `errorText の改行は空白に置換されること`() {
        // errorText は末尾に置くため、カンマを含む値でもそのまま末尾に残る
        val errorTexts =
            mapOf(
                "first line\r\nsecond line" to "first line second line",
                "first line\rsecond line" to "first line second line",
                "first line\nsecond line" to "first line second line",
                "Failed, reason" to "Failed, reason",
            )
        for ((errorText, expected) in errorTexts) {
            val event =
                IceCandidateErrorEvent(
                    "192.168.40.x",
                    50000,
                    "turn:sora.example.com:443?transport=tcp",
                    701,
                    errorText,
                )
            assertEquals(
                "[rtc] @onIceCandidateError address=192.168.40.x, port=50000," +
                    " url=turn:sora.example.com:443?transport=tcp, errorCode=701," +
                    " errorText=$expected",
                PeerChannelImpl.formatIceCandidateError(event),
            )
        }
    }
}
