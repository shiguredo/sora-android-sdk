package jp.shiguredo.sora.sdk.channel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import jp.shiguredo.sora.sdk.TestPemFixtures
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoraMediaChannelCertificateTest {
    private val appContext: Context = ApplicationProvider.getApplicationContext()

    // 証明書・秘密鍵以外は共通のパラメータで SoraMediaChannel を生成するヘルパー
    private fun createChannel(
        caCertificate: String? = null,
        clientCertificate: String? = null,
        clientPrivateKey: String? = null,
    ): SoraMediaChannel =
        SoraMediaChannel(
            context = appContext,
            signalingEndpoint = "wss://sora.example.com/signaling",
            channelId = "sora",
            mediaOption = SoraMediaOption(),
            listener = null,
            caCertificate = caCertificate,
            clientCertificate = clientCertificate,
            clientPrivateKey = clientPrivateKey,
        )

    @Test
    fun `有効な CA 証明書 PEM を指定してインスタンスを生成できること`() {
        val channel = createChannel(caCertificate = TestPemFixtures.certificate1)
        assertNotNull(channel)
    }

    @Test
    fun `クライアント証明書チェーンと RSA 秘密鍵の PEM を指定してインスタンスを生成できること`() {
        val channel =
            createChannel(
                clientCertificate = TestPemFixtures.certificateChain,
                clientPrivateKey = TestPemFixtures.rsaPrivateKeyPkcs8,
            )
        assertNotNull(channel)
    }

    @Test
    fun `clientCertificate のみ指定すると IllegalArgumentException が送出されること`() {
        // 証明書と秘密鍵は対で指定する必要がある
        assertFailsWith<IllegalArgumentException> {
            createChannel(clientCertificate = TestPemFixtures.certificate1)
        }
    }

    @Test
    fun `clientPrivateKey のみ指定すると IllegalArgumentException が送出されること`() {
        // 証明書と秘密鍵は対で指定する必要がある
        assertFailsWith<IllegalArgumentException> {
            createChannel(clientPrivateKey = TestPemFixtures.rsaPrivateKeyPkcs8)
        }
    }

    @Test
    fun `不正な caCertificate PEM を指定すると IllegalArgumentException が送出されること`() {
        assertFailsWith<IllegalArgumentException> {
            createChannel(caCertificate = "not-a-pem")
        }
    }

    @Test
    fun `caCertificate に複数証明書を連結した PEM を指定すると IllegalArgumentException が送出されること`() {
        // caCertificate は単数前提のため、チェーンを渡した場合は例外を送出する
        assertFailsWith<IllegalArgumentException> {
            createChannel(caCertificate = TestPemFixtures.certificateChain)
        }
    }

    @Test
    fun `PKCS8 以外の clientPrivateKey PEM を指定すると IllegalArgumentException が送出されること`() {
        // PKCS#1 形式の秘密鍵は非対応
        assertFailsWith<IllegalArgumentException> {
            createChannel(
                clientCertificate = TestPemFixtures.certificate1,
                clientPrivateKey = TestPemFixtures.rsaPrivateKeyPkcs1,
            )
        }
    }

    @Test
    fun `caCertificate に空文字列を指定すると IllegalArgumentException が送出されること`() {
        // 空文字列は PEM として不正であるため null 扱いせず例外を送出する
        assertFailsWith<IllegalArgumentException> {
            createChannel(caCertificate = "")
        }
    }

    @Test
    fun `caCertificate に空白文字列を指定すると IllegalArgumentException が送出されること`() {
        // 空白のみの文字列も PEM として不正であるため例外を送出する
        assertFailsWith<IllegalArgumentException> {
            createChannel(caCertificate = "   ")
        }
    }

    @Test
    fun `clientCertificate に空文字列を指定すると IllegalArgumentException が送出されること`() {
        assertFailsWith<IllegalArgumentException> {
            createChannel(
                clientCertificate = "",
                clientPrivateKey = "",
            )
        }
    }
}
