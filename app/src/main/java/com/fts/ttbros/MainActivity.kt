package com.fts.ttbros

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.data.model.UserProfile
import com.fts.ttbros.data.model.UserRole
import com.fts.ttbros.data.repository.TeamRepository
import com.fts.ttbros.data.repository.UserRepository
import com.fts.ttbros.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val userRepository = UserRepository()
    private val teamRepository = TeamRepository()
    private val auth by lazy { Firebase.auth }
    private var userProfile: UserProfile? = null
    private var joinInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fragmentContainer) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.characterFragment,
                R.id.teamChatFragment,
                R.id.announcementChatFragment,
                R.id.masterPlayerChatFragment,
                R.id.notesFragment
            ),
            binding.drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navigationView.setupWithNavController(navController)
        binding.navigationView.setNavigationItemSelectedListener { item ->
            if (item.itemId == R.id.menu_show_code) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                showCodeDialog()
                true
            } else {
                val handled = NavigationUI.onNavDestinationSelected(item, navController)
                if (handled) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                handled
            }
        }
        setupGroupPrompt()
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile()
                if (profile == null) {
                    navigateTo(LoginActivity::class.java)
                    return@launch
                }
                if (profile.teamId.isNullOrBlank()) {
                    binding.groupPromptPanel.isVisible = true
                    binding.navigationView.menu.findItem(R.id.menu_show_code)?.isVisible = false
                    return@launch
                }
                userProfile = profile
                binding.navigationView.menu.findItem(R.id.menu_show_code)?.isVisible =
                    profile.role == UserRole.MASTER && !profile.teamCode.isNullOrBlank()
                binding.groupPromptPanel.isVisible = false
            } catch (error: Exception) {
                Snackbar.make(binding.root, error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun showCodeDialog() {
        val profile = userProfile
        if (profile == null || profile.teamCode.isNullOrBlank()) {
            Snackbar.make(binding.root, R.string.error_unknown, Snackbar.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_team_code_title)
            .setMessage(getString(R.string.dialog_team_code_message, profile.teamCode))
            .setPositiveButton(R.string.action_copy) { dialog, _ ->
                copyToClipboard(profile.teamCode)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun copyToClipboard(code: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.dialog_team_code_title), code))
        Snackbar.make(binding.root, R.string.code_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun navigateTo(destination: Class<*>) {
        startActivity(Intent(this, destination))
        finish()
    }

    private fun setupGroupPrompt() {
        binding.joinGroupButton.setOnClickListener {
            val code = binding.groupCodeEditText.text?.toString()?.trim().orEmpty().uppercase()
            if (code.length < 4) {
                Snackbar.make(binding.root, R.string.error_group_code, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            joinGroup(code)
        }
    }

    private fun joinGroup(code: String) {
        if (joinInProgress) return
        val user = auth.currentUser ?: run {
            navigateTo(LoginActivity::class.java)
            return
        }
        joinInProgress = true
        binding.joinGroupButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val team = teamRepository.findTeamByCode(code)
                if (team == null) {
                    Snackbar.make(binding.root, R.string.error_group_not_found, Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                teamRepository.addMember(team.id, user, UserRole.PLAYER)
                userRepository.updateTeamInfo(team.id, team.code, UserRole.PLAYER, team.system)
                binding.groupPromptPanel.isVisible = false
                binding.groupCodeEditText.text?.clear()
                userProfile = userRepository.currentProfile()
                binding.navigationView.menu.findItem(R.id.menu_show_code)?.isVisible = false
                Snackbar.make(binding.root, R.string.success_joined_group, Snackbar.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Snackbar.make(binding.root, error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
            } finally {
                joinInProgress = false
                binding.joinGroupButton.isEnabled = true
            }
        }
    }
}