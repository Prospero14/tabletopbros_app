package com.fts.ttbros.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class YandexDiskRepository {
    
    private val oauthToken = "y0__xCbs5y6Axj26Dsg8cirqxXirRUThBMOfP9QPmPxX0_alMiVWA"
    private val baseUrl = "https://cloud-api.yandex.net/v1/disk"
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * Загрузить файл на Яндекс.Диск и получить публичную ссылку
     */
    suspend fun uploadFile(
        teamId: String,
        fileName: String,
        fileUri: Uri,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        try {
            // 1. Создать путь на Яндекс.Диске
            val remotePath = "/TTBros/teams/$teamId/documents/$fileName"
            Log.d("YandexDisk", "Uploading to: $remotePath")
            
            // 2. Создать папки если не существуют
            createFolderIfNeeded("/TTBros")
            createFolderIfNeeded("/TTBros/teams")
            createFolderIfNeeded("/TTBros/teams/$teamId")
            createFolderIfNeeded("/TTBros/teams/$teamId/documents")
            
            // 3. Получить URL для загрузки
            val uploadUrl = getUploadUrl(remotePath)
            Log.d("YandexDisk", "Upload URL obtained")
            
            // 4. Загрузить файл
            uploadFileToUrl(uploadUrl, fileUri, context)
            Log.d("YandexDisk", "File uploaded successfully")
            
            // 5. Опубликовать файл и получить публичную ссылку
            val publicUrl = publishFile(remotePath)
            Log.d("YandexDisk", "Public URL: $publicUrl")
            
            publicUrl
        } catch (e: Exception) {
            Log.e("YandexDisk", "Upload error: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Загрузить аватарку пользователя на Яндекс.Диск
     */
    suspend fun uploadAvatar(
        userId: String,
        imageUri: Uri,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        try {
            // 1. Создать путь на Яндекс.Диске
            val fileName = "avatar_${userId}_${System.currentTimeMillis()}.jpg"
            val remotePath = "/TTBros/avatars/$fileName"
            Log.d("YandexDisk", "Uploading avatar to: $remotePath")
            
            // 2. Создать папки если не существуют
            createFolderIfNeeded("/TTBros")
            createFolderIfNeeded("/TTBros/avatars")
            
            // 3. Оптимизировать изображение
            val optimizedFile = optimizeImage(imageUri, context)
            
            // 4. Получить URL для загрузки
            val uploadUrl = getUploadUrl(remotePath)
            Log.d("YandexDisk", "Upload URL obtained")
            
            // 5. Загрузить файл
            uploadOptimizedFile(uploadUrl, optimizedFile)
            Log.d("YandexDisk", "Avatar uploaded successfully")
            
            // 6. Опубликовать файл и получить публичную ссылку
            val publicUrl = publishFile(remotePath)
            Log.d("YandexDisk", "Public avatar URL: $publicUrl")
            
            // 7. Удалить временный файл
            optimizedFile.delete()
            
            publicUrl
        } catch (e: Exception) {
            Log.e("YandexDisk", "Avatar upload error: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Оптимизировать изображение для аватарки
     */
    private fun optimizeImage(imageUri: Uri, context: Context): File {
        val inputStream = context.contentResolver.openInputStream(imageUri)
        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        
        // Создать квадратную миниатюру 512x512
        val dimension = Math.min(bitmap.width, bitmap.height)
        val thumbnail = android.media.ThumbnailUtils.extractThumbnail(bitmap, dimension, dimension)
        val resized = android.graphics.Bitmap.createScaledBitmap(thumbnail, 512, 512, true)
        
        // Сохранить в JPEG с компрессией
        val tempFile = File(context.cacheDir, "avatar_temp_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { output ->
            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, output)
        }
        
        bitmap.recycle()
        thumbnail.recycle()
        resized.recycle()
        
        return tempFile
    }
    
    /**
     * Загрузить оптимизированный файл
     */
    private fun uploadOptimizedFile(uploadUrl: String, file: File) {
        val requestBody = file.asRequestBody("image/jpeg".toMediaType())
        val request = Request.Builder()
            .url(uploadUrl)
            .put(requestBody)
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("File upload failed: ${response.code} ${response.message}")
        }
        response.close()
    }
    
    /**
     * Создать папку на Яндекс.Диске если не существует
     */
    private fun createFolderIfNeeded(path: String) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/resources?path=${Uri.encode(path)}")
                .header("Authorization", "OAuth $oauthToken")
                .put(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful || response.code == 409) {
                // 409 = папка уже существует, это нормально
                Log.d("YandexDisk", "Folder created or exists: $path")
            } else {
                Log.w("YandexDisk", "Folder creation response: ${response.code}")
            }
            response.close()
        } catch (e: Exception) {
            Log.w("YandexDisk", "Folder creation error (may already exist): ${e.message}")
        }
    }
    
    /**
     * Получить URL для загрузки файла
     */
    private fun getUploadUrl(remotePath: String): String {
        val request = Request.Builder()
            .url("$baseUrl/resources/upload?path=${Uri.encode(remotePath)}&overwrite=true")
            .header("Authorization", "OAuth $oauthToken")
            .get()
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to get upload URL: ${response.code} ${response.message}")
        }
        
        val body = response.body?.string() ?: throw Exception("Empty response body")
        response.close()
        
        val json = gson.fromJson(body, Map::class.java)
        return json["href"] as? String ?: throw Exception("No upload URL in response")
    }
    
    /**
     * Загрузить файл по полученному URL
     */
    private fun uploadFileToUrl(uploadUrl: String, fileUri: Uri, context: Context) {
        // Копируем файл из URI в временный файл
        val tempFile = File(context.cacheDir, "temp_upload_${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(fileUri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        
        try {
            val requestBody = tempFile.asRequestBody("application/octet-stream".toMediaType())
            val request = Request.Builder()
                .url(uploadUrl)
                .put(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("File upload failed: ${response.code} ${response.message}")
            }
            response.close()
        } finally {
            tempFile.delete()
        }
    }
    
    /**
     * Загрузить PDF файл по полученному URL с правильным MIME типом
     */
    private fun uploadPdfFileToUrl(uploadUrl: String, pdfUri: Uri, context: Context) {
        // Копируем файл из URI в временный файл
        val tempFile = File(context.cacheDir, "temp_pdf_upload_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(pdfUri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        
        try {
            val requestBody = tempFile.asRequestBody("application/pdf".toMediaType())
            val request = Request.Builder()
                .url(uploadUrl)
                .put(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("PDF upload failed: ${response.code} ${response.message}")
            }
            response.close()
        } finally {
            tempFile.delete()
        }
    }
    
    /**
     * Опубликовать файл и получить прямую ссылку для скачивания
     */
    private fun publishFile(remotePath: String): String {
        // 1. Опубликовать файл
        val publishRequest = Request.Builder()
            .url("$baseUrl/resources/publish?path=${Uri.encode(remotePath)}")
            .header("Authorization", "OAuth $oauthToken")
            .put(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()
        
        val publishResponse = client.newCall(publishRequest).execute()
        if (!publishResponse.isSuccessful && publishResponse.code != 409) {
            // 409 = уже опубликован, это нормально
            throw Exception("Failed to publish file: ${publishResponse.code}")
        }
        publishResponse.close()
        
        // 2. Получить информацию о файле
        val getRequest = Request.Builder()
            .url("$baseUrl/resources?path=${Uri.encode(remotePath)}")
            .header("Authorization", "OAuth $oauthToken")
            .get()
            .build()
        
        val getResponse = client.newCall(getRequest).execute()
        if (!getResponse.isSuccessful) {
            throw Exception("Failed to get file info: ${getResponse.code}")
        }
        
        val body = getResponse.body?.string() ?: throw Exception("Empty response")
        getResponse.close()
        
        val json = gson.fromJson(body, Map::class.java) as Map<*, *>
        
        // Для изображений возвращаем прямую ссылку на файл
        // Для документов возвращаем публичную ссылку
        val fileUrl = json["file"] as? String
        val publicUrl = json["public_url"] as? String
        
        // Если есть прямая ссылка (file), используем её (для Glide)
        // Иначе используем публичную ссылку (для документов)
        return fileUrl ?: publicUrl ?: throw Exception("No download URL in response")
    }
    
    /**
     * Загрузить PDF лист персонажа на Яндекс.Диск
     */
    suspend fun uploadCharacterSheet(
        userId: String,
        pdfUri: Uri,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        try {
            // 1. Создать путь на Яндекс.Диске
            val fileName = "character_sheet_${userId}_${System.currentTimeMillis()}.pdf"
            val remotePath = "/TTBros/character_sheets/$userId/$fileName"
            Log.d("YandexDisk", "Uploading character sheet to: $remotePath")
            
            // 2. Создать папки если не существуют
            createFolderIfNeeded("/TTBros")
            createFolderIfNeeded("/TTBros/character_sheets")
            createFolderIfNeeded("/TTBros/character_sheets/$userId")
            
            // 3. Получить URL для загрузки
            val uploadUrl = getUploadUrl(remotePath)
            Log.d("YandexDisk", "Upload URL obtained")
            
            // 4. Загрузить файл с правильным MIME типом для PDF
            uploadPdfFileToUrl(uploadUrl, pdfUri, context)
            Log.d("YandexDisk", "Character sheet uploaded successfully")
            
            // 5. Опубликовать файл и получить публичную ссылку
            val publicUrl = publishFile(remotePath)
            Log.d("YandexDisk", "Public character sheet URL: $publicUrl")
            
            publicUrl
        } catch (e: Exception) {
            Log.e("YandexDisk", "Character sheet upload error: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Удалить файл с Яндекс.Диска
     */
    suspend fun deleteFile(remotePath: String) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/resources?path=${Uri.encode(remotePath)}&permanently=true")
                .header("Authorization", "OAuth $oauthToken")
                .delete()
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful && response.code != 404) {
                throw Exception("Delete failed: ${response.code}")
            }
            response.close()
            Log.d("YandexDisk", "File deleted: $remotePath")
        } catch (e: Exception) {
            Log.e("YandexDisk", "Delete error: ${e.message}", e)
            throw e
        }
    }
}
