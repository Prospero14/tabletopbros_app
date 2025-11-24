package com.fts.ttbros


import android.view.GestureDetector
import android.view.MotionEvent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
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
import com.fts.ttbros.notifications.EventNotificationScheduler
import com.fts.ttbros.notifications.NotificationHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val userRepository = UserRepository()
    private val teamRepository = TeamRepository()

    private val yandexDisk = com.fts.ttbros.data.repository.YandexDiskRepository()
    private lateinit var gestureDetector: GestureDetector
    private var isNavigating = false // Защита от множественных навигаций

    // Порядок пунктов меню для свайпа
    private val menuOrder = listOf(
        R.id.teamsFragment,
        R.id.teamChatFragment,
        R.id.announcementChatFragment,
        R.id.masterPlayerChatFragment,
        R.id.notesFragment,
        R.id.calendarFragment,
        R.id.documentsFragment,
        R.id.charactersFragment
    )
    private val auth by lazy { Firebase.auth }
    private var userProfile: UserProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fragmentContainer) as? NavHostFragment
            ?: throw IllegalStateException("NavHostFragment not found")
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.teamsFragment,
                R.id.teamChatFragment,
                R.id.announcementChatFragment,
                R.id.masterPlayerChatFragment,
                R.id.notesFragment,
                R.id.calendarFragment,
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
            if (isNavigating) return@setNavigationItemSelectedListener false

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
                R.id.charactersFragment -> {
                    // Always pop to root of characters tab to show list
                    isNavigating = true
                    try {
                        if (navController.currentDestination?.id != R.id.charactersFragment) {
                            val navOptions = NavOptions.Builder()
                                .setEnterAnim(R.anim.slide_in_right)
                                .setExitAnim(R.anim.slide_out_left)
                                .setPopEnterAnim(R.anim.slide_in_left)
                                .setPopExitAnim(R.anim.slide_out_right)
                                .setPopUpTo(R.id.charactersFragment, false)
                                .build()
                            navController.navigate(R.id.charactersFragment, null, navOptions)
                        }
                    } catch (e: Exception) {
                        // If navigation fails, try simple navigate with animations
                        try {
                            val navOptions = androidx.navigation.NavOptions.Builder()
                                .setEnterAnim(R.anim.slide_in_right)
                                .setExitAnim(R.anim.slide_out_left)
                                .setPopEnterAnim(R.anim.slide_in_left)
                                .setPopExitAnim(R.anim.slide_out_right)
                                .build()
                            navController.navigate(R.id.charactersFragment, null, navOptions)
                        } catch (e2: Exception) {
                            android.util.Log.e("MainActivity", "Navigation error: ${e2.message}", e2)
                        }
                    } finally {
                        binding.root.postDelayed({
                            isNavigating = false
                        }, 500)
                    }
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                    true
                }
                else -> {
                    // Add smooth animations for menu transitions
                    isNavigating = true
                    val navOptions = NavOptions.Builder()
                        .setEnterAnim(R.anim.slide_in_right)
                        .setExitAnim(R.anim.slide_out_left)
                        .setPopEnterAnim(R.anim.slide_in_left)
                        .setPopExitAnim(R.anim.slide_out_right)
                        .build()
                    
                    try {
                        navController.navigate(item.itemId, null, navOptions)
                        binding.drawerLayout.closeDrawer(GravityCompat.END)
                        // Reset flag after delay
                        binding.root.postDelayed({
                            isNavigating = false
                        }, 500)
                        true
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Navigation error: ${e.message}", e)
                        try {
                            NavigationUI.onNavDestinationSelected(item, navController)
                        } catch (e2: Exception) {
                            android.util.Log.e("MainActivity", "NavigationUI error: ${e2.message}", e2)
                        }
                        binding.drawerLayout.closeDrawer(GravityCompat.END)
                        isNavigating = false
                        true
                    }
                }
            }
        }
        
        // Создать каналы уведомлений
        NotificationHelper.createNotificationChannels(this)
        
        // Перепланировать все уведомления о событиях при запуске
        EventNotificationScheduler.rescheduleAllEventNotifications(this)
        
        setupHeader()
        setupFooter()
    }

    private fun setupFooter() {
        // Используем binding для доступа к кнопке
        binding.root.findViewById<View>(R.id.footerMenuButton)?.setOnClickListener {
            try {
                binding.drawerLayout.openDrawer(GravityCompat.END)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error opening drawer: ${e.message}", e)
            }
        }
    }

    private fun setupHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        // Removed teamContainer setup as requested
        
        val avatarView = headerView.findViewById<ImageView>(R.id.navHeaderAvatar)
        avatarView.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            showAvatarMenu()
        }
    }
    
    private fun showAvatarMenu() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Аватар")
            .setItems(arrayOf("Выбрать из галереи", "Удалить")) { _, which ->
                when (which) {
                    0 -> pickImageFromGallery()
                    1 -> removeAvatar()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_cloud_dialog))
            .show()
    }
    
    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            // Игнорировать свайп если уже идёт навигация
            if (isNavigating) return false

            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        // Свайп вправо - следующий пункт меню
                        navigateToNextMenuItem()
                    } else {
                        // Свайп влево - предыдущий пункт меню
                        navigateToPreviousMenuItem()
                    }
                    return true
                }
            }
            return false
        }
    })
    }
    
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Жесты отключены из-за крашей
        if (::gestureDetector.isInitialized) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun navigateToNextMenuItem() {
        // Проверка что не идёт другая навигация
        if (isNavigating) return
        
        val currentDestination = navController.currentDestination?.id ?: return
        val currentIndex = menuOrder.indexOf(currentDestination)
        
        if (currentIndex != -1 && currentIndex < menuOrder.size - 1) {
            val nextDestination = menuOrder[currentIndex + 1]
            
            // Установить флаг навигации
            isNavigating = true
            
            try {
                val navOptions = NavOptions.Builder()
                    .setEnterAnim(R.anim.slide_in_right)
                    .setExitAnim(R.anim.slide_out_left)
                    .setPopEnterAnim(R.anim.slide_in_left)
                    .setPopExitAnim(R.anim.slide_out_right)
                    .build()
                navController.navigate(nextDestination, null, navOptions)
                
                // Сбросить флаг через небольшую задержку
                binding.root.postDelayed({
                    isNavigating = false
                }, 500) // 500ms задержка перед следующим свайпом
                
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Swipe navigation error: ${e.message}", e)
                isNavigating = false // Сбросить флаг при ошибке
            }
        }
    }

    private fun navigateToPreviousMenuItem() {
        // Проверка что не идёт другая навигация
        if (isNavigating) return
        
        val currentDestination = navController.currentDestination?.id ?: return
        val currentIndex = menuOrder.indexOf(currentDestination)
        
        if (currentIndex > 0) {
            val previousDestination = menuOrder[currentIndex - 1]
            
            // Установить флаг навигации
            isNavigating = true
            
            try {
                val navOptions = NavOptions.Builder()
                    .setEnterAnim(R.anim.slide_in_left)
                    .setExitAnim(R.anim.slide_out_right)
                    .setPopEnterAnim(R.anim.slide_in_right)
                    .setPopExitAnim(R.anim.slide_out_left)
                    .build()
                navController.navigate(previousDestination, null, navOptions)
                
                // Сбросить флаг через небольшую задержку
                binding.root.postDelayed({
                    isNavigating = false
                }, 500)
                
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Swipe navigation error: ${e.message}", e)
                isNavigating = false
            }
        }
    }

private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
    }

    private fun removeAvatar() {
        lifecycleScope.launch {
            try {
                // Удалить URL из Firestore
                userRepository.updateAvatarUrl(null)

                // Обновить UI
                val headerView = binding.navigationView.getHeaderView(0)
                val avatarView = headerView.findViewById<ImageView>(R.id.navHeaderAvatar)
                avatarView.setImageResource(android.R.drawable.sym_def_app_icon)

                Snackbar.make(binding.root, "Аватар удалён", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Ошибка удаления аватара", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            val imageUri: Uri? = data.data
            imageUri?.let {
                lifecycleScope.launch {
                    try {
                        // Показать прогресс
                        Snackbar.make(binding.root, "Загрузка аватара...", Snackbar.LENGTH_LONG).show()

                        // Загрузить на Яндекс.Диск
                        val userId = auth.currentUser?.uid ?: return@launch
                        val avatarUrl = yandexDisk.uploadAvatar(userId, it, this@MainActivity)

                        // Сохранить URL в Firestore
                        userRepository.updateAvatarUrl(avatarUrl)

                        // Обновить UI
                        val headerView = binding.navigationView.getHeaderView(0)
                        val avatarView = headerView.findViewById<ImageView>(R.id.navHeaderAvatar)

                        // Загрузить с помощью Glide
                        com.bumptech.glide.Glide.with(this@MainActivity)
                            .load(avatarUrl)
                            .circleCrop()
                            .placeholder(android.R.drawable.sym_def_app_icon)
                            .into(avatarView)

                        Snackbar.make(binding.root, "Аватар загружен", Snackbar.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Snackbar.make(binding.root, "Ошибка загрузки аватара: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updateHeader(profile: UserProfile) {
        val headerView = binding.navigationView.getHeaderView(0)
        headerView.findViewById<TextView>(R.id.navHeaderUser).text = profile.displayName

        // Загрузить аватар если есть
        val avatarView = headerView.findViewById<ImageView>(R.id.navHeaderAvatar)
        if (!profile.avatarUrl.isNullOrBlank()) {
            com.bumptech.glide.Glide.with(this)
                .load(profile.avatarUrl)
                .circleCrop()
                .placeholder(android.R.drawable.sym_def_app_icon)
                .into(avatarView)
        } else {
            avatarView.setImageResource(android.R.drawable.sym_def_app_icon)
        }
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
                try {
                    updateHeader(profile)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error updating header: ${e.message}", e)
                }

                if (profile.teams.isEmpty()) {
                    binding.navigationView.menu.findItem(R.id.menu_show_code)?.isVisible = false
                } else {
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
            Snackbar.make(binding.root, getString(R.string.error_unknown), Snackbar.LENGTH_SHORT).show()
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
            .setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_cloud_dialog))
            .show()
    }

    private fun showTeamSwitcherDialog() {
        val profile = userProfile ?: return
        val teams = profile.teams
        
        val items = teams.map { 
            val name = it.teamName.ifBlank { "Team ${it.teamCode}" }
            "$name (${it.role.name})" 
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Team")
            .setItems(items) { dialog, which ->
                val selectedTeam = teams[which]
                if (selectedTeam.teamId != profile.currentTeamId) {
                    switchTeam(selectedTeam.teamId)
                }
                dialog.dismiss()
            }
            .setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_cloud_dialog))
            .show()
    }

    private fun switchTeam(teamId: String) {
        lifecycleScope.launch {
            try {
                userRepository.switchTeam(teamId)
                refreshProfile()
                // Don't navigate to current destination - just refresh the current fragment
                // Navigation to same destination can cause issues
                Snackbar.make(binding.root, "Switched team", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error switching team: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyToClipboard(code: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
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

    companion object {
        private const val REQUEST_CODE_PICK_IMAGE = 1001
    }
}