package jp.shiguredo.sora.sdk.util

import io.reactivex.disposables.Disposables
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReusableCompositeDisposableTest {
    @Test
    fun `add した購読が解除されていないこと`() {
        val sut = ReusableCompositeDisposable()
        val disposable = Disposables.fromRunnable {}

        sut.add(disposable)

        assertFalse(disposable.isDisposed)
    }

    @Test
    fun `dispose 後に登録済みの全購読が解除されること`() {
        val sut = ReusableCompositeDisposable()
        val disposable1 = Disposables.fromRunnable {}
        val disposable2 = Disposables.fromRunnable {}
        val disposable3 = Disposables.fromRunnable {}

        sut.add(disposable1)
        sut.add(disposable2)
        sut.add(disposable3)
        sut.dispose()

        assertTrue(disposable1.isDisposed)
        assertTrue(disposable2.isDisposed)
        assertTrue(disposable3.isDisposed)
    }

    @Test
    fun `dispose 後に再度 add と dispose ができること`() {
        val sut = ReusableCompositeDisposable()
        val first = Disposables.fromRunnable {}
        val second = Disposables.fromRunnable {}

        sut.add(first)
        sut.dispose()

        sut.add(second)
        assertFalse(second.isDisposed)

        sut.dispose()
        assertTrue(first.isDisposed)
        assertTrue(second.isDisposed)
    }

    @Test
    fun `add 未実行で dispose しても例外が発生しないこと`() {
        val sut = ReusableCompositeDisposable()
        sut.dispose()
    }
}
