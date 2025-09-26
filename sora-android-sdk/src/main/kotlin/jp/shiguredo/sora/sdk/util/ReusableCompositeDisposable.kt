package jp.shiguredo.sora.sdk.util

import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

// the idea is borrowed from
// http://gfx.hatenablog.com/entry/2015/06/08/091656

class ReusableCompositeDisposable {
    private var compositeDisposable: CompositeDisposable? = null

    fun add(subscription: Disposable) {
        if (compositeDisposable != null) {
            compositeDisposable = CompositeDisposable()
        }
        compositeDisposable?.add(subscription)
    }

    fun dispose() {
        compositeDisposable?.dispose()
        compositeDisposable = null
    }
}
