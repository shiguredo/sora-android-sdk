package jp.shiguredo.sora.sdk.channel.tls

import jp.shiguredo.sora.sdk.TestPemFixtures
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PemDecoderTest {
    @Test
    fun `単一証明書の PEM を X509Certificate に変換できること`() {
        val certificate = PemDecoder.decodeCertificate(TestPemFixtures.certificate1)
        // subject の CN が生成時に指定したものと一致することを確認する
        assertTrue(certificate.subjectX500Principal.name.contains("sora-test-ca-1"))
    }

    @Test
    fun `証明書チェーンの PEM を複数の X509Certificate に変換できること`() {
        val certificates = PemDecoder.decodeCertificateChain(TestPemFixtures.certificateChain)
        // 連結した 2 証明書が順番どおりに変換されることを確認する
        assertEquals(2, certificates.size)
        assertTrue(certificates[0].subjectX500Principal.name.contains("sora-test-ca-1"))
        assertTrue(certificates[1].subjectX500Principal.name.contains("sora-test-ca-2"))
    }

    @Test
    fun `単一証明書を decodeCertificateChain で要素数 1 のリストに変換できること`() {
        val certificates = PemDecoder.decodeCertificateChain(TestPemFixtures.certificate1)
        assertEquals(1, certificates.size)
        assertTrue(certificates[0].subjectX500Principal.name.contains("sora-test-ca-1"))
    }

    @Test
    fun `複数証明書を連結した PEM を decodeCertificate に渡すと IllegalArgumentException が送出されること`() {
        // caCertificate は単数前提のため、チェーン (複数証明書) を渡した場合は
        // 先頭のみを黙って採用せず例外を送出する
        assertFailsWith<IllegalArgumentException> {
            PemDecoder.decodeCertificate(TestPemFixtures.certificateChain)
        }
    }

    @Test
    fun `PKCS8 の RSA 秘密鍵の PEM を PrivateKey に変換できること`() {
        val privateKey = PemDecoder.decodePkcs8PrivateKey(TestPemFixtures.rsaPrivateKeyPkcs8)
        assertEquals("RSA", privateKey.algorithm)
    }

    @Test
    fun `PKCS8 の EC 秘密鍵の PEM を PrivateKey に変換できること`() {
        val privateKey = PemDecoder.decodePkcs8PrivateKey(TestPemFixtures.ecPrivateKeyPkcs8)
        assertEquals("EC", privateKey.algorithm)
    }

    @Test
    fun `不正な証明書 PEM で IllegalArgumentException が送出されること`() {
        // 証明書ではない文字列を渡すと例外になることを確認する
        assertFailsWith<IllegalArgumentException> {
            PemDecoder.decodeCertificate("-----BEGIN CERTIFICATE-----\nnot-a-certificate\n-----END CERTIFICATE-----")
        }
    }

    @Test
    fun `証明書を含まない PEM を decodeCertificateChain に渡すと IllegalArgumentException が送出されること`() {
        // 証明書が 1 つも含まれない場合は例外になることを確認する
        assertFailsWith<IllegalArgumentException> {
            PemDecoder.decodeCertificateChain("")
        }
    }

    @Test
    fun `PKCS1 形式の秘密鍵で IllegalArgumentException が送出されること`() {
        // PKCS#8 以外のヘッダ (RSA PRIVATE KEY) は非対応として例外になることを確認する
        assertFailsWith<IllegalArgumentException> {
            PemDecoder.decodePkcs8PrivateKey(TestPemFixtures.rsaPrivateKeyPkcs1)
        }
    }

    @Test
    fun `Base64 本体が壊れた PKCS8 秘密鍵で IllegalArgumentException が送出されること`() {
        // ヘッダは PKCS#8 だが Base64 本体が不正な場合は例外になることを確認する
        assertFailsWith<IllegalArgumentException> {
            PemDecoder.decodePkcs8PrivateKey("-----BEGIN PRIVATE KEY-----\n!!!invalid!!!\n-----END PRIVATE KEY-----")
        }
    }

    @Test
    fun `PKCS8 ヘッダだが鍵として不正なバイト列で IllegalArgumentException が送出されること`() {
        // Base64 としては有効だが PKCS#8 の鍵ではない場合は例外になることを確認する
        assertFailsWith<IllegalArgumentException> {
            PemDecoder.decodePkcs8PrivateKey("-----BEGIN PRIVATE KEY-----\naGVsbG8=\n-----END PRIVATE KEY-----")
        }
    }
}
