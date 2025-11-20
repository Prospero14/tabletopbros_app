package com.fts.ttbros

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.data.repository.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private val auth by lazy { Firebase.auth }
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        lifecycleScope.launch {
            delay(SPLASH_DELAY)
            val user = auth.currentUser
            if (user == null) {
                navigate<LoginActivity>()
                return@launch
            }

            val profile = userRepository.ensureProfile(user)
            if (profile.teamId.isNullOrBlank()) {
                navigate<GroupActivity>()
            } else {
                navigate<MainActivity>()
            }
        }
    }

    private inline fun <reified T> navigate() {
        startActivity(Intent(this, T::class.java))
        finish()
    }

    companion object {
        private const val SPLASH_DELAY = 1200L
    }
}