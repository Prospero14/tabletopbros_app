package com.fts.ttbros

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class TTBrosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
