package com.example.guardband.base

/**
 * Base interface that all View contracts must extend.
 * Provides common UI callbacks used across all screens.
 */
interface BaseView {
    fun showLoading()
    fun hideLoading()
    fun showError(message: String)
}
