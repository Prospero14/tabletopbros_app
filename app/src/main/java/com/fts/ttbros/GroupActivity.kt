package com.fts.ttbros

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.data.model.UserRole
import com.fts.ttbros.data.repository.TeamRepository
import com.fts.ttbros.data.repository.UserRepository
import kotlinx.coroutines.launch

class GroupActivity : AppCompatActivity() {

    private lateinit var groupCodeEditText: TextInputEditText
    private lateinit var joinGroupButton: MaterialButton
    private lateinit var createGroupButton: MaterialButton
    private lateinit var progressIndicator: CircularProgressIndicator
    
    private val auth by lazy { Firebase.auth }
    private val userRepository = UserRepository()
    private val teamRepository = TeamRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group)

        groupCodeEditText = findViewById(R.id.groupCodeEditText)
        joinGroupButton = findViewById(R.id.joinGroupButton)
        createGroupButton = findViewById(R.id.createGroupButton)
        progressIndicator = findViewById(R.id.progressIndicator)

        joinGroupButton.setOnClickListener { joinGroup() }
        createGroupButton.setOnClickListener { chooseSystemAndCreateTeam() }
    }

    private fun joinGroup() {
        val code = groupCodeEditText.text?.toString()?.trim().orEmpty().uppercase()
        if (code.length < 4) {
            Snackbar.make(findViewById(android.R.id.content), getString(R.string.error_group_code), Snackbar.LENGTH_SHORT).show()
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
                    Snackbar.make(findViewById(android.R.id.content), getString(R.string.error_group_not_found), Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                teamRepository.addMember(team.id, user, UserRole.PLAYER)
                userRepository.addTeam(team.id, team.code, UserRole.PLAYER, team.system, "Team ${team.code}")

                Snackbar.make(findViewById(android.R.id.content), getString(R.string.success_joined_group), Snackbar.LENGTH_SHORT).show()
                navigateToMain()
            } catch (error: Exception) {
                Snackbar.make(findViewById(android.R.id.content), error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun chooseSystemAndCreateTeam() {
        val options = arrayOf(
            getString(R.string.vampire_masquerade),
            getString(R.string.dungeons_dragons),
            getString(R.string.viedzmin_2e),
            "Warhammer Fantasy Roleplay",
            "Warhammer 40k: Dark Heresy"
        )
        val values = arrayOf("vtm_5e", "dnd_5e", "viedzmin_2e", "whrp", "wh_darkheresy")
        var selectedIndex = 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_game_system)
            .setSingleChoiceItems(options, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(R.string.create_new_group) { _, _ ->
                createTeamWithSystem(values[selectedIndex])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createTeamWithSystem(system: String) {
        val user = auth.currentUser ?: run {
            navigateToLogin()
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            try {
                val team = teamRepository.createTeam(user, system)
                userRepository.addTeam(team.id, team.code, UserRole.MASTER, team.system, "Team ${team.code}")
                showCodeDialog(team.code)
            } catch (error: Exception) {
                Snackbar.make(findViewById(android.R.id.content), error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
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
                navigateToMain()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                navigateToMain()
            }
            .setCancelable(false)
            .show()
    }

    private fun copyCodeToClipboard(code: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.dialog_team_code_title), code))
        Snackbar.make(findViewById(android.R.id.content), R.string.code_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun setLoading(isLoading: Boolean) {
        progressIndicator.isVisible = isLoading
        joinGroupButton.isEnabled = !isLoading
        createGroupButton.isEnabled = !isLoading
        groupCodeEditText.isEnabled = !isLoading
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
