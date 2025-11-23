package com.fts.ttbros

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
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
                R.id.notesFragment,
                R.id.documentsFragment,
                R.id.charactersFragment
            ),
            binding.drawerLayout
        )
        
        binding.toolbar.setNavigationIcon(R.drawable.ic_menu)
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }

        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navigationView.setupWithNavController(navController)
        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_show_code -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                    showCodeDialog()
                    true
                }
                R.id.menu_logout -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                    logout()
                    true
                }
                else -> {
                    val currentId = navController.currentDestination?.id
                    if (currentId == item.itemId) {
                        // Pop to root of the current tab to reset state
                        navController.popBackStack(item.itemId, false)
                        binding.drawerLayout.closeDrawer(GravityCompat.END)
                        true
                    } else {
                        val handled = NavigationUI.onNavDestinationSelected(item, navController)
                        if (handled) {
                            binding.drawerLayout.closeDrawer(GravityCompat.END)
                        }
                        handled
                    }
                }
            }
        }
        
        setupGroupPrompt()
        setupHeader()
        setupFooter()
    }

    private fun setupFooter() {
        // Using findViewById because binding might not be updated until rebuild
        findViewById<View>(R.id.footerMenuButton)?.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }
    }

    private fun setupHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val teamContainer = headerView.findViewById<View>(R.id.navHeaderTeamContainer)
        teamContainer.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            showTeamSwitcherDialog()
        }
        
        val avatarView = headerView.findViewById<ImageView>(R.id.navHeaderAvatar)
        avatarView.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            showAvatarMenu()
        }
    }
    
    private fun showAvatarMenu() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Аватар")
            .setItems(arrayOf("Загрузить из галереи", "Удалить")) { _, which ->
                when (which) {
                    0 -> pickImageFromGallery()
                    1 -> removeAvatar()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
    }
    
    private fun removeAvatar() {
        val headerView = binding.navigationView.getHeaderView(0)
        val avatarView = headerView.findViewById<ImageView>(R.id.navHeaderAvatar)
        avatarView.setImageResource(android.R.drawable.sym_def_app_icon)
        // TODO: Remove from storage if saved
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            val imageUri: Uri? = data.data
            imageUri?.let {
                try {
                    val inputStream = contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    
                    val headerView = binding.navigationView.getHeaderView(0)
                    val avatarView = headerView.findViewById<ImageView>(R.id.navHeaderAvatar)
                    
                    // Make circular and center crop
                    val dimension = Math.min(bitmap.width, bitmap.height)
                    val thumbnail = android.media.ThumbnailUtils.extractThumbnail(bitmap, dimension, dimension)
                    val roundedBitmap = RoundedBitmapDrawableFactory.create(resources, thumbnail)
                    roundedBitmap.isCircular = true
                    avatarView.setImageDrawable(roundedBitmap)
                    
                    // TODO: Save to Firebase Storage
                } catch (e: Exception) {
                    Snackbar.make(binding.root, "Ошибка загрузки изображения", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateHeader(profile: UserProfile) {
        val headerView = binding.navigationView.getHeaderView(0)
        headerView.findViewById<TextView>(R.id.navHeaderUser).text = profile.displayName
        // Email is hidden as requested
        
        val currentTeam = profile.teams.find { it.teamId == profile.currentTeamId }
        val teamName = if (currentTeam != null) {
             currentTeam.teamName.ifBlank { "Team ${currentTeam.teamCode}" }
        } else {
             "No Team Selected"
        }
        val role = currentTeam?.role?.name ?: ""
        
        headerView.findViewById<TextView>(R.id.navHeaderTeamName).text = teamName
        headerView.findViewById<TextView>(R.id.navHeaderRole).text = role
        headerView.findViewById<TextView>(R.id.navHeaderRole).isVisible = role.isNotEmpty()
    }

    override fun onStart() {
        super.onStart()
        refreshProfile()
    }

    private fun refreshProfile() {
        lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile()
                if (profile == null) {
                    navigateTo(LoginActivity::class.java)
                    return@launch
                }
                userProfile = profile
                updateHeader(profile)

                if (profile.teams.isEmpty()) {
                    binding.groupPromptPanel.isVisible = true
                    binding.navigationView.menu.findItem(R.id.menu_show_code)?.isVisible = false
                } else {
                    binding.groupPromptPanel.isVisible = false
                    val currentTeam = profile.teams.find { it.teamId == profile.currentTeamId }
                    binding.navigationView.menu.findItem(R.id.menu_show_code)?.isVisible =
                        currentTeam?.role == UserRole.MASTER
                }
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
        val currentTeam = profile?.teams?.find { it.teamId == profile.currentTeamId }
        
        if (currentTeam == null || currentTeam.teamCode.isBlank()) {
            Snackbar.make(binding.root, R.string.error_unknown, Snackbar.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_team_code_title)
            .setMessage(getString(R.string.dialog_team_code_message, currentTeam.teamCode))
            .setPositiveButton(R.string.action_copy) { dialog, _ ->
                copyToClipboard(currentTeam.teamCode)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTeamSwitcherDialog() {
        val profile = userProfile ?: return
        val teams = profile.teams
        
        val items = teams.map { 
            val name = it.teamName.ifBlank { "Team ${it.teamCode}" }
            "$name (${it.role.name})" 
        }.toTypedArray() + arrayOf("Join Another Team", "Create New Team")

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Team")
            .setItems(items) { dialog, which ->
                when {
                    which < teams.size -> {
                        val selectedTeam = teams[which]
                        if (selectedTeam.teamId != profile.currentTeamId) {
                            switchTeam(selectedTeam.teamId)
                        }
                    }
                    which == teams.size -> showJoinTeamDialog()
                    which == teams.size + 1 -> chooseSystemAndCreateTeam()
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun switchTeam(teamId: String) {
        lifecycleScope.launch {
            try {
                userRepository.switchTeam(teamId)
                refreshProfile()
                val currentId = navController.currentDestination?.id
                if (currentId != null) {
                    navController.navigate(currentId)
                }
                Snackbar.make(binding.root, "Switched team", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error switching team: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun showJoinTeamDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(this)
        input.hint = getString(R.string.enter_group_code)
        
        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = resources.getDimensionPixelSize(R.dimen.activity_horizontal_margin)
        params.rightMargin = resources.getDimensionPixelSize(R.dimen.activity_horizontal_margin)
        input.layoutParams = params
        container.addView(input)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.join_group)
            .setView(container)
            .setPositiveButton(R.string.join_group) { _, _ ->
                val code = input.text?.toString()?.trim().orEmpty().uppercase()
                if (code.length >= 4) {
                    joinGroup(code)
                } else {
                    Snackbar.make(binding.root, R.string.error_group_code, Snackbar.LENGTH_SHORT).show()
                }
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
    
    private fun logout() {
        auth.signOut()
        navigateTo(LoginActivity::class.java)
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
        binding.createGroupButton.setOnClickListener {
            chooseSystemAndCreateTeam()
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
                
                userRepository.addTeam(team.id, team.code, UserRole.PLAYER, team.system, "Team ${team.code}")
                
                binding.groupPromptPanel.isVisible = false
                binding.groupCodeEditText.text?.clear()
                refreshProfile()
                Snackbar.make(binding.root, R.string.success_joined_group, Snackbar.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Snackbar.make(binding.root, error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
            } finally {
                joinInProgress = false
                binding.joinGroupButton.isEnabled = true
            }
        }
    }

    private fun chooseSystemAndCreateTeam() {
        val options = arrayOf(
            getString(R.string.vampire_masquerade),
            getString(R.string.dungeons_dragons),
            getString(R.string.viedzmin_2e)
        )
        val values = arrayOf("vtm_5e", "dnd_5e", "viedzmin_2e")
        var selectedIndex = 0
        
        // First dialog: select system
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_game_system)
            .setSingleChoiceItems(options, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showTeamNameDialog(values[selectedIndex])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun showTeamNameDialog(system: String) {
        val input = com.google.android.material.textfield.TextInputEditText(this)
        input.hint = "Название команды"
        
        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = resources.getDimensionPixelSize(R.dimen.activity_horizontal_margin)
        params.rightMargin = resources.getDimensionPixelSize(R.dimen.activity_horizontal_margin)
        input.layoutParams = params
        container.addView(input)

        MaterialAlertDialogBuilder(this)
            .setTitle("Название команды")
            .setView(container)
            .setPositiveButton(R.string.create_new_group) { _, _ ->
                val teamName = input.text?.toString()?.trim().orEmpty()
                createTeamWithSystem(system, teamName)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createTeamWithSystem(system: String, teamName: String) {
        val user = auth.currentUser ?: run {
            navigateTo(LoginActivity::class.java)
            return
        }
        joinInProgress = true
        binding.joinGroupButton.isEnabled = false
        binding.createGroupButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val team = teamRepository.createTeam(user, system)
                val finalTeamName = teamName.ifBlank { "Team ${team.code}" }
                userRepository.addTeam(team.id, team.code, UserRole.MASTER, team.system, finalTeamName)
                
                binding.groupPromptPanel.isVisible = false
                binding.groupCodeEditText.text?.clear()
                refreshProfile()
                
                // Automatically select the new team
                userRepository.switchTeam(team.id)
                refreshProfile()
                Snackbar.make(binding.root, "Team created and selected", Snackbar.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Snackbar.make(binding.root, error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
            } finally {
                joinInProgress = false
                binding.joinGroupButton.isEnabled = true
                binding.createGroupButton.isEnabled = true
            }
        }
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.END)
    }
    
    companion object {
        private const val REQUEST_CODE_PICK_IMAGE = 1001
    }
}