package jp.shiguredo.sora.sdk.util

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoraLoggerTest {
    // libjingleEnabled は libwebrtcLogEnabled の旧名であり、読み書きが libwebrtcLogEnabled へ
    // 転送される。旧名を参照する利用者のコードがそのまま動作することを保証する。
    @Suppress("DEPRECATION")
    @Test
    fun libjingleEnabledForwardsToLibwebrtcLogEnabled() {
        // 旧名で true にすると新しい名前も true になる
        SoraLogger.libjingleEnabled = true
        assertTrue(SoraLogger.libwebrtcLogEnabled)

        // 新しい名前で false にすると旧名も false になる
        SoraLogger.libwebrtcLogEnabled = false
        assertFalse(SoraLogger.libjingleEnabled)
    }
}
