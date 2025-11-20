package com.fts.ttbros

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.fts.ttbros.data.model.UserProfile
import com.fts.ttbros.data.model.UserRole
import com.fts.ttbros.data.repository.UserRepository
import com.fts.ttbros.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val userRepository = UserRepository()
    private var userProfile: UserProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        navController = findNavController(R.id.fragmentContainer)
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
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val profile = userRepository.currentProfile()
            if (profile == null) {
                navigateTo(LoginActivity::class.java)
                return@launch
            }
            if (profile.teamId.isNullOrBlank()) {
                navigateTo(GroupActivity::class.java)
                return@launch
            }
            userProfile = profile
            binding.navigationView.menu.findItem(R.id.menu_show_code)?.isVisible =
                profile.role == UserRole.MASTER && !profile.teamCode.isNullOrBlank()
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
}