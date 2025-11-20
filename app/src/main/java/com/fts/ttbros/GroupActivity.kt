package com.fts.ttbros

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.data.model.UserRole
import com.fts.ttbros.data.repository.TeamRepository
import com.fts.ttbros.data.repository.UserRepository
import com.fts.ttbros.databinding.ActivityGroupBinding
import kotlinx.coroutines.launch

class GroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupBinding
    private val auth by lazy { Firebase.auth }
    private val userRepository = UserRepository()
    private val teamRepository = TeamRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.joinGroupButton.setOnClickListener { joinGroup() }
        binding.createGroupButton.setOnClickListener { createGroup() }
    }

    private fun joinGroup() {
        val code = binding.groupCodeEditText.text?.toString()?.trim().orEmpty().uppercase()
        if (code.length < 4) {
            Snackbar.make(binding.root, getString(R.string.error_group_code), Snackbar.LENGTH_SHORT).show()
            return
        }

        val user = auth.currentUser ?: run {
            navigateToLogin()
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val team = teamRepository.findTeamByCode(code)
                if (team == null) {
                    Snackbar.make(binding.root, getString(R.string.error_group_not_found), Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                teamRepository.addMember(team.id, user, UserRole.PLAYER)
                userRepository.updateTeamInfo(team.id, team.code, UserRole.PLAYER)

                Snackbar.make(binding.root, getString(R.string.success_joined_group), Snackbar.LENGTH_SHORT).show()
                navigateToMain()
            } catch (error: Exception) {
                Snackbar.make(binding.root, error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun createGroup() {
        val user = auth.currentUser ?: run {
            navigateToLogin()
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            try {
                val team = teamRepository.createTeam(user)
                userRepository.updateTeamInfo(team.id, team.code, UserRole.MASTER)
                showCodeDialog(team.code)
            } catch (error: Exception) {
                Snackbar.make(binding.root, error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showCodeDialog(code: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_team_code_title)
            .setMessage(getString(R.string.dialog_team_code_message, code))
            .setPositiveButton(R.string.action_copy) { dialog, _ ->
                copyCodeToClipboard(code)
                dialog.dismiss()
                navigateToSystemSelection()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                navigateToSystemSelection()
            }
            .setCancelable(false)
            .show()
    }

    private fun copyCodeToClipboard(code: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.dialog_team_code_title), code))
        Snackbar.make(binding.root, R.string.code_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressIndicator.isVisible = isLoading
        binding.joinGroupButton.isEnabled = !isLoading
        binding.createGroupButton.isEnabled = !isLoading
        binding.groupCodeEditText.isEnabled = !isLoading
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun navigateToSystemSelection() {
        startActivity(Intent(this, SystemSelectionActivity::class.java))
        finish()
    }
}

