package com.fts.ttbros.utils

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import com.google.android.material.snackbar.Snackbar
import android.widget.Toast
import android.content.Context

object SnackbarHelper {
    
    // Короткая длительность для информационных сообщений (1.5 секунды)
    private const val SHORT_DURATION = 1500
    
    // Средняя длительность для предупреждений (2.5 секунды)
    private const val MEDIUM_DURATION = 2500
    
    // Длительная для ошибок (4 секунды)
    private const val LONG_DURATION = 4000
    
    /**
     * Показывает быстрый Snackbar с fade out анимацией для успешных операций
     */
    fun showSuccessSnackbar(view: View, message: String) {
        try {
            if (view.parent == null) return
            
            val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
            snackbar.duration = SHORT_DURATION
            snackbar.show()
            
            // Добавляем fade out анимацию
            val snackbarView = snackbar.view
            snackbarView.postDelayed({
                val fadeOut = AlphaAnimation(1.0f, 0.0f)
                fadeOut.duration = 300
                fadeOut.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        snackbar.dismiss()
                    }
                    override fun onAnimationRepeat(animation: Animation?) {}
                })
                snackbarView.startAnimation(fadeOut)
            }, (SHORT_DURATION - 300).toLong())
        } catch (e: Exception) {
            android.util.Log.e("SnackbarHelper", "Error showing snackbar: ${e.message}", e)
        }
    }
    
    /**
     * Показывает Snackbar с предупреждением
     */
    fun showWarningSnackbar(view: View, message: String) {
        try {
            if (view.parent == null) return
            
            val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
            snackbar.duration = MEDIUM_DURATION
            snackbar.show()
            
            // Добавляем fade out анимацию
            val snackbarView = snackbar.view
            snackbarView.postDelayed({
                val fadeOut = AlphaAnimation(1.0f, 0.0f)
                fadeOut.duration = 300
                fadeOut.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        snackbar.dismiss()
                    }
                    override fun onAnimationRepeat(animation: Animation?) {}
                })
                snackbarView.startAnimation(fadeOut)
            }, (MEDIUM_DURATION - 300).toLong())
        } catch (e: Exception) {
            android.util.Log.e("SnackbarHelper", "Error showing snackbar: ${e.message}", e)
        }
    }
    
    /**
     * Показывает Snackbar с ошибкой и возвращает объект Snackbar для настройки действий
     */
    fun showErrorSnackbar(view: View, message: String): Snackbar? {
        try {
            if (view.parent == null) return null
            
            val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
            snackbar.duration = LONG_DURATION
            snackbar.show()
            
            // Добавляем fade out анимацию
            val snackbarView = snackbar.view
            snackbarView.postDelayed({
                val fadeOut = AlphaAnimation(1.0f, 0.0f)
                fadeOut.duration = 300
                fadeOut.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        snackbar.dismiss()
                    }
                    override fun onAnimationRepeat(animation: Animation?) {}
                })
                snackbarView.startAnimation(fadeOut)
            }, (LONG_DURATION - 300).toLong())
            
            return snackbar
        } catch (e: Exception) {
            android.util.Log.e("SnackbarHelper", "Error showing snackbar: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Безопасный Toast с обработкой ошибок
     */
    fun showToast(context: Context?, message: String, duration: Int = Toast.LENGTH_SHORT) {
        try {
            if (context == null) return
            Toast.makeText(context, message, duration).show()
        } catch (e: Exception) {
            android.util.Log.e("SnackbarHelper", "Error showing toast: ${e.message}", e)
        }
    }
}
