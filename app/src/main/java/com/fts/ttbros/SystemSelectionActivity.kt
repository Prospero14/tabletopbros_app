package com.fts.ttbros

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.data.repository.UserRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SystemSelectionActivity : AppCompatActivity() {

    private lateinit var vtmButton: MaterialButton
    private lateinit var dndButton: MaterialButton
    private lateinit var progressIndicator: CircularProgressIndicator
    
    private val auth by lazy { Firebase.auth }
    private val firestore by lazy { Firebase.firestore }
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_selection)

        vtmButton = findViewById(R.id.vtmButton)
        dndButton = findViewById(R.id.dndButton)
        progressIndicator = findViewById(R.id.progressIndicator)

        vtmButton.setOnClickListener { selectSystem("vtm_5e") }
        dndButton.setOnClickListener { selectSystem("dnd_5e") }
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
        progressIndicator.isVisible = isLoading
        vtmButton.isEnabled = !isLoading
        dndButton.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }
}
