package com.fts.ttbros

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.data.model.UserProfile
import com.fts.ttbros.data.repository.UserRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var registerTextView: MaterialTextView
    private lateinit var progressIndicator: CircularProgressIndicator
    
    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        registerTextView = findViewById(R.id.registerTextView)
        progressIndicator = findViewById(R.id.progressIndicator)

        loginButton.setOnClickListener { authenticate(isRegistration = false) }
        registerTextView.setOnClickListener { authenticate(isRegistration = true) }
    }

    override fun onStart() {
        super.onStart()
        auth.currentUser?.let { firebaseUser ->
            lifecycleScope.launch {
                try {
                    val profile = userRepository.ensureProfile(firebaseUser)
                    navigateNext(profile)
                } catch (e: Exception) {
                    showError(e.localizedMessage ?: getString(R.string.error_unknown))
                }
            }
        }
    }

    private fun authenticate(isRegistration: Boolean) {
        var login = emailEditText.text?.toString()?.trim().orEmpty()
        val password = passwordEditText.text?.toString()?.trim().orEmpty()

        if (login.isBlank() || password.isBlank()) {
            showError("Заполните все поля")
            return
        }

        // Append dummy domain if login doesn't contain @
        if (!login.contains("@")) {
            login = "$login@ttbros.app"
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val authResult = if (isRegistration) {
                    auth.createUserWithEmailAndPassword(login, password).await()
                } else {
                    auth.signInWithEmailAndPassword(login, password).await()
                }

                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    showError(getString(R.string.error_unknown))
                    return@launch
                }

                val profile = userRepository.ensureProfile(firebaseUser)
                navigateNext(profile)

            } catch (e: Exception) {
                showError(e.localizedMessage ?: getString(R.string.error_unknown))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun navigateNext(profile: UserProfile) {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        progressIndicator.isVisible = isLoading
        loginButton.isEnabled = !isLoading
        emailEditText.isEnabled = !isLoading
        passwordEditText.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }
}