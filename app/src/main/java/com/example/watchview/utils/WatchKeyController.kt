package com.example.watchview.utils

import android.util.Log
import android.view.Window
import android.view.WindowManager

/**
 * Helper for applying vendor key-behavior flags documented in
 * docs/手表按键定义及接入说明.pdf. Flags are encoded into the window title with the "miwear_"
 * prefix so that the system framework can opt-in to custom behavior.
 */
class WatchKeyController(
    private val windowProvider: () -> Window?
) {

    private var originalTitle: CharSequence? = null
    private var appliedFlags: Int = FLAG_NONE

    fun applyFlags(flags: Int) {
        appliedFlags = flags
        val window = windowProvider() ?: return
        val params = window.attributes ?: WindowManager.LayoutParams()
        if (originalTitle == null) {
            originalTitle = params.title
        }
        params.title = if (flags == FLAG_NONE) {
            originalTitle ?: params.title
        } else {
            FLAGS_PREFIX + flags
        }
        window.attributes = params
        Log.i(TAG, "Applied watch key flags=$flags")
    }

    fun clearFlags() {
        appliedFlags = FLAG_NONE
        val window = windowProvider() ?: return
        val params = window.attributes ?: WindowManager.LayoutParams()
        params.title = originalTitle ?: params.title
        window.attributes = params
        Log.i(TAG, "Cleared watch key flags")
    }

    companion object {
        private const val TAG = "WatchKeyController"
        private const val FLAGS_PREFIX = "miwear_"

        const val FLAG_NONE = 0x00000000
        const val FLAG_USE_POWER_KEY = 0x00000001
    const val FLAG_CONVERT_STEM_TO_FX = 0x00000002
        const val FLAG_IGNORE_STEM_KEY = 0x00000004
        const val FLAG_IGNORE_POWER_KEY = 0x00000008
        const val FLAG_USE_DEFAULT_STEM_KEY_LONG_PRESS = 0x00000010
        const val FLAG_CONVERT_STEM_TO_F1_ONLY = 0x00000020
    }
}
