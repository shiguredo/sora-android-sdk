package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.message.IceServer
import jp.shiguredo.sora.sdk.channel.signaling.message.OfferConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class PeerNetworkConfigTest {

    @Test
    fun testGetTurnTlsHosts_withTurnsUrls() {
        val iceServers = listOf(
            IceServer(
                urls = listOf(
                    "turns:turn.example.com:443",
                    "turns:backup.example.com:5349?transport=tcp",
                    "turn:turn.example.com:3478"
                ),
                credential = "password",
                username = "user"
            )
        )
        val offerConfig = OfferConfig(
            iceServers = iceServers,
            iceTransportPolicy = "all"
        )
        val mediaOption = SoraMediaOption()
        val config = PeerNetworkConfig(offerConfig, mediaOption)

        val hosts = config.getTurnTlsHosts()

        assertEquals(2, hosts.size)
        assertTrue(hosts.contains("turn.example.com"))
        assertTrue(hosts.contains("backup.example.com"))
    }

    @Test
    fun testGetTurnTlsHosts_noTurnsUrls() {
        val iceServers = listOf(
            IceServer(
                urls = listOf(
                    "turn:turn.example.com:3478",
                    "stun:stun.example.com:3478"
                ),
                credential = "password",
                username = "user"
            )
        )
        val offerConfig = OfferConfig(
            iceServers = iceServers,
            iceTransportPolicy = "all"
        )
        val mediaOption = SoraMediaOption()
        val config = PeerNetworkConfig(offerConfig, mediaOption)

        val hosts = config.getTurnTlsHosts()

        assertTrue(hosts.isEmpty())
    }

    @Test
    fun testGetTurnTlsHosts_nullOfferConfig() {
        val mediaOption = SoraMediaOption()
        val config = PeerNetworkConfig(null, mediaOption)

        val hosts = config.getTurnTlsHosts()

        assertTrue(hosts.isEmpty())
    }

    @Test
    fun testGetTurnTlsHosts_multipleTurnsServers() {
        val iceServers = listOf(
            IceServer(
                urls = listOf("turns:server1.example.com:443"),
                credential = "password1",
                username = "user1"
            ),
            IceServer(
                urls = listOf("turns:server2.example.com:5349"),
                credential = "password2",
                username = "user2"
            )
        )
        val offerConfig = OfferConfig(
            iceServers = iceServers,
            iceTransportPolicy = "relay"
        )
        val mediaOption = SoraMediaOption()
        val config = PeerNetworkConfig(offerConfig, mediaOption)

        val hosts = config.getTurnTlsHosts()

        assertEquals(2, hosts.size)
        assertTrue(hosts.contains("server1.example.com"))
        assertTrue(hosts.contains("server2.example.com"))
    }
}
