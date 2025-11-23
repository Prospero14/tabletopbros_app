package com.fts.ttbros

import android.os.Build

actual class Platform actual constructor() {
    actual val platform: String = "Android ${Build.VERSION.SDK_INT}"
}

