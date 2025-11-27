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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.fts.ttbros.utils.LocaleHelper
import com.fts.ttbros.utils.SnackbarHelper
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applySavedLanguage(newBase))
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fragmentContainer) as? NavHostFragment
            ?: throw IllegalStateException("NavHostFragment not found")
        navController = navHostFragment.navController

        // Reset navigation flag when destination changes
        navController.addOnDestinationChangedListener { _, _, _ ->
            isNavigating = false
        }

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
                R.id.menu_language -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                    showLanguageDialog()
                    true
                }
                R.id.menu_clear_cache -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                    showClearCacheDialog()
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
                        } else {
                             isNavigating = false
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
                            isNavigating = false
                        }
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
        setupSwipeGesture() // Ensure this is called!
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
                try {
                    if (e1 == null) return false

                    // Игнорировать свайп если уже идёт навигация
                    if (isNavigating) return false

                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y

                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) {
                                // Свайп вправо - предыдущий пункт меню (листаем назад)
                                navigateToPreviousMenuItem()
                            } else {
                                // Свайп влево - следующий пункт меню (листаем вперед)
                                navigateToNextMenuItem()
                            }
                            return true
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error in gesture handler: ${e.message}", e)
                }
                return false
            }
        })
        
        // Применяем жесты только к FragmentContainerView (не дублируем в dispatchTouchEvent)
        try {
            val fragmentContainer = binding.root.findViewById<View>(R.id.fragmentContainer)
            fragmentContainer?.setOnTouchListener { view, event ->
                try {
                    if (::gestureDetector.isInitialized) {
                        gestureDetector.onTouchEvent(event)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error in touch listener: ${e.message}", e)
                }
                false // Позволяем событию продолжить обработку
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting up swipe gesture: ${e.message}", e)
        }
    }
    
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Убрали обработку жестов отсюда, чтобы избежать двойной обработки
        return super.dispatchTouchEvent(ev)
    }

    private fun navigateToNextMenuItem() {
        // Проверка что не идёт другая навигация
        if (isNavigating) return
        
        try {
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
                    
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Swipe navigation error: ${e.message}", e)
                    isNavigating = false // Сбросить флаг при ошибке
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in navigateToNextMenuItem: ${e.message}", e)
            isNavigating = false
        }
    }

    private fun navigateToPreviousMenuItem() {
        // Проверка что не идёт другая навигация
        if (isNavigating) return
        
        try {
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
                    
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Swipe navigation error: ${e.message}", e)
                    isNavigating = false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in navigateToPreviousMenuItem: ${e.message}", e)
            isNavigating = false
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

                SnackbarHelper.showSuccessSnackbar(binding.root, getString(R.string.avatar_deleted))
            } catch (e: Exception) {
                SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_deleting_avatar))
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
                        SnackbarHelper.showWarningSnackbar(binding.root, getString(R.string.uploading_avatar))

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

                        SnackbarHelper.showSuccessSnackbar(binding.root, getString(R.string.avatar_uploaded))
                    } catch (e: Exception) {
                        SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_uploading_avatar, e.message ?: ""))
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
                SnackbarHelper.showErrorSnackbar(binding.root, error.localizedMessage ?: getString(R.string.error_unknown))
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
            SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_unknown))
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
                SnackbarHelper.showSuccessSnackbar(binding.root, getString(R.string.team_switched))
            } catch (e: Exception) {
                SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_switching_team, e.message ?: ""))
            }
        }
    }

    private fun copyToClipboard(code: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.dialog_team_code_title), code))
        SnackbarHelper.showSuccessSnackbar(binding.root, getString(R.string.code_copied))
    }

    private fun navigateTo(destination: Class<*>) {
        startActivity(Intent(this, destination))
        finish()
    }
    
    private fun showLanguageDialog() {
        val currentLanguage = LocaleHelper.getCurrentLanguage(this)
        val options = arrayOf(
            getString(R.string.language_russian),
            getString(R.string.language_english)
        )
        val selectedIndex = if (currentLanguage == "ru") 0 else 1
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.menu_language))
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val newLanguage = if (which == 0) "ru" else "en"
                if (newLanguage != currentLanguage) {
                    LocaleHelper.saveLanguage(this, newLanguage)
                    // Пересоздаем активность для применения языка
                    recreate()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_cloud_dialog))
            .show()
    }
    
    private fun showClearCacheDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_cache_title)
            .setMessage(R.string.clear_cache_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                clearCache()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun clearCache() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Очистка внутреннего кэша
                    val cacheDir = cacheDir
                    if (cacheDir.exists() && cacheDir.isDirectory) {
                        cacheDir.listFiles()?.forEach { file ->
                            try {
                                if (file.isDirectory) {
                                    file.deleteRecursively()
                                } else {
                                    file.delete()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Error deleting cache file: ${file.name}", e)
                            }
                        }
                    }
                    
                    // Очистка внешнего кэша
                    val externalCacheDir = externalCacheDir
                    if (externalCacheDir != null && externalCacheDir.exists() && externalCacheDir.isDirectory) {
                        externalCacheDir.listFiles()?.forEach { file ->
                            try {
                                if (file.isDirectory) {
                                    file.deleteRecursively()
                                } else {
                                    file.delete()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Error deleting external cache file: ${file.name}", e)
                            }
                        }
                    }
                }
                
                // Показываем сообщение об успехе
                SnackbarHelper.showSuccessSnackbar(binding.root, getString(R.string.cache_cleared))
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error clearing cache: ${e.message}", e)
                SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_clearing_cache, e.message ?: ""))
            }
        }
    }
    
    private fun logout() {
        auth.signOut()
        navigateTo(LoginActivity::class.java)
    }

    companion object {
        private const val REQUEST_CODE_PICK_IMAGE = 1001
    }
}