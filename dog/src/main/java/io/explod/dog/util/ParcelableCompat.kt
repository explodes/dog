package io.explod.dog.util

import android.content.Intent
import android.os.Build

inline fun <reified T> Intent.getParcelableExtraCompat(extra: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(extra, T::class.java)
    } else {
        getParcelableExtra(extra)
    }
}
