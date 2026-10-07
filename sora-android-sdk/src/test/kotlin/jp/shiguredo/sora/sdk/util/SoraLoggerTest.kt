package jp.shiguredo.sora.sdk.util

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoraLoggerTest {
    @Test
    fun `libwebrtcLogEnabled のデフォルト値は false であること`() {
        assertFalse(SoraLogger.libwebrtcLogEnabled)
    }

    // libjingleEnabled は libwebrtcLogEnabled の旧名であり、読み書きが libwebrtcLogEnabled へ
    // 転送される。旧名と新名が同じ値を共有することを検証する。
    @Suppress("DEPRECATION")
    @Test
    fun `libjingleEnabled が libwebrtcLogEnabled へ読み書きを転送すること`() {
        try {
            // 旧名で true にすると新しい名前も true になる
            SoraLogger.libjingleEnabled = true
            assertTrue(SoraLogger.libwebrtcLogEnabled)

            // 新しい名前で false にすると旧名も false になる
            SoraLogger.libwebrtcLogEnabled = false
            assertFalse(SoraLogger.libjingleEnabled)
        } finally {
            // テストは companion object の共有状態を変更するため、既定値へ戻す
            SoraLogger.libwebrtcLogEnabled = false
        }
    }
}
