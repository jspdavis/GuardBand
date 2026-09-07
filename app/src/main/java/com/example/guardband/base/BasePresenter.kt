package com.example.guardband.base

/**
 * Base class for all Presenters.
 * Manages the view reference lifecycle to avoid memory leaks.
 *
 * @param V The View contract type this presenter drives.
 */
abstract class BasePresenter<V : BaseView> {

    /** Nullable view reference — always null-check before use. */
    protected var view: V? = null
        private set

    /** Called by the Activity/Fragment to bind the view. */
    fun attachView(view: V) {
        this.view = view
    }

    /** Called in onDestroy() to release the view reference. */
    fun detachView() {
        this.view = null
    }

    /** Convenience: returns true when a view is currently attached. */
    val isViewAttached: Boolean
        get() = view != null
}
