package com.fts.ttbros

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.data.repository.UserRepository
import com.fts.ttbros.databinding.ActivitySystemSelectionBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SystemSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySystemSelectionBinding
    private val auth by lazy { Firebase.auth }
    private val firestore by lazy { Firebase.firestore }
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySystemSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.vtmButton.setOnClickListener { selectSystem("vtm_5e") }
        binding.dndButton.setOnClickListener { selectSystem("dnd_5e") }
    }

    private fun selectSystem(systemId: String) {
        val user = auth.currentUser ?: return
        setLoading(true)

        lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile()
                if (profile == null || profile.teamId.isNullOrBlank()) {
                    showError(getString(R.string.error_unknown))
                    return@launch
                }

                // Update team document with selected system
                firestore.collection("teams").document(profile.teamId)
                    .update("system", systemId)
                    .await()

                navigateToMain()

            } catch (e: Exception) {
                showError(e.localizedMessage ?: getString(R.string.error_unknown))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressIndicator.isVisible = isLoading
        binding.vtmButton.isEnabled = !isLoading
        binding.dndButton.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
