package com.fts.ttbros

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.data.model.UserProfile
import com.fts.ttbros.data.repository.UserRepository
import com.fts.ttbros.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener { authenticate(isRegistration = false) }
        binding.registerTextView.setOnClickListener { authenticate(isRegistration = true) }
    }

    override fun onStart() {
        super.onStart()
        auth.currentUser?.let { firebaseUser ->
            lifecycleScope.launch {
                try {
                    val profile = userRepository.ensureProfile(firebaseUser)
                    navigateNext(profile)
                } catch (e: Exception) {
                    showError(getString(R.string.error_unknown))
                }
            }
        }
    }

    private fun authenticate(isRegistration: Boolean) {
        val login = binding.emailEditText.text?.toString()?.trim().orEmpty()
        val email = if (login.contains("@")) login else "$login@ttbros.app"
        val password = binding.passwordEditText.text?.toString()?.trim().orEmpty()

        if (login.isBlank() || password.length < MIN_PASSWORD_LENGTH) {
            Snackbar.make(
                binding.root,
                getString(R.string.error_credentials),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val resultUser = if (isRegistration) {
                    auth.createUserWithEmailAndPassword(email, password).await().user
                } else {
                    auth.signInWithEmailAndPassword(email, password).await().user
                }

                if (resultUser == null) {
                    showError(getString(R.string.error_unknown))
                    return@launch
                }

                val profile = userRepository.ensureProfile(resultUser)
                navigateNext(profile)

            } catch (error: Exception) {
                showError(error.localizedMessage ?: getString(R.string.error_unknown))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun navigateNext(profile: UserProfile) {
        val destination = if (profile.teamId.isNullOrBlank()) {
            Intent(this, GroupActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(destination)
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressIndicator.isVisible = isLoading
        binding.loginButton.isEnabled = !isLoading
        binding.registerTextView.isEnabled = !isLoading
        binding.emailEditText.isEnabled = !isLoading
        binding.passwordEditText.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).setAnchorView(binding.loginButton).show()
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 6
    }
}