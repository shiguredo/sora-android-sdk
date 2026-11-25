package jp.shiguredo.sora.sdk

import org.junit.Test

// プロセスクラッシュ時の logcat を確認するための一時的なテスト。
// このブランチはマージしない。
class SoraFailureCheckE2ETest : SoraE2ETestBase() {
    @Test
    fun `失敗検査用にプロセスをクラッシュさせる`() {
        Thread {
            throw RuntimeException("失敗検査用のプロセスクラッシュ")
        }.start()
        Thread.sleep(5_000)
    }
}
