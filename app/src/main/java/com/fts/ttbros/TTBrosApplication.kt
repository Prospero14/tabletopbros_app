package com.fts.ttbros

import android.app.Application
import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.fts.ttbros.utils.LocaleHelper

class TTBrosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
    
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applySavedLanguage(base))
    }
}
