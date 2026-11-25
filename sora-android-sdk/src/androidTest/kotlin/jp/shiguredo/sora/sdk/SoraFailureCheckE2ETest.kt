package jp.shiguredo.sora.sdk

import org.junit.Assert.assertTrue
import org.junit.Test

// 失敗時 artifact を確認するための一時的なテスト。
// このブランチはマージしない。
class SoraFailureCheckE2ETest : SoraE2ETestBase() {
    @Test
    fun `失敗検査用に必ず失敗する`() {
        assertTrue("失敗検査用の意図的な失敗", false)
    }
}
