package com.fts.ttbros

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.data.repository.UserRepository
import com.fts.ttbros.utils.LocaleHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context

class SplashActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applySavedLanguage(newBase))
    }

    private val auth by lazy { Firebase.auth }
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        lifecycleScope.launch {
            delay(SPLASH_DELAY)
            try {
                val user = auth.currentUser
                if (user == null) {
                    startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                    finish()
                    return@launch
                }

                userRepository.ensureProfile(user)
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            } catch (e: Exception) {
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                finish()
            }
        }
    }

    companion object {
        private const val SPLASH_DELAY = 1200L
    }
}