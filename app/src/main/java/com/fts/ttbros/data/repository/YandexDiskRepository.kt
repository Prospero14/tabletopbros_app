package com.fts.ttbros.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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
        context: Context,
        isMaterial: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        try {
            // 1. Создать путь на Яндекс.Диске
            val remotePath = if (isMaterial) {
                "/TTBros/teams/$teamId/player_materials/$fileName"
            } else {
                "/TTBros/teams/$teamId/documents/$fileName"
            }
            Log.d("YandexDisk", "Uploading to: $remotePath")
            
            // 2. Создать папки если не существуют
            createFolderIfNeeded("/TTBros")
            createFolderIfNeeded("/TTBros/teams")
            createFolderIfNeeded("/TTBros/teams/$teamId")
            if (isMaterial) {
                createFolderIfNeeded("/TTBros/teams/$teamId/player_materials")
            } else {
                createFolderIfNeeded("/TTBros/teams/$teamId/documents")
            }
            
            // 3. Получить URL для загрузки
            val uploadUrl = getUploadUrl(remotePath)
            Log.d("YandexDisk", "Upload URL obtained")
            
            // 4. Загрузить файл (определяем MIME тип по расширению)
            val mimeType = detectMimeTypeFromFileName(fileName)
            uploadFileToUrl(uploadUrl, fileUri, context, mimeType)
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
    private fun uploadFileToUrl(uploadUrl: String, fileUri: Uri, context: Context, mimeType: String? = null) {
        // Копируем файл из URI в временный файл
        val tempFile = File(context.cacheDir, "temp_upload_${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(fileUri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        
        try {
            // Определяем MIME тип по расширению файла или используем переданный
            val detectedMimeType = mimeType ?: detectMimeType(fileUri, context)
            val requestBody = tempFile.asRequestBody(detectedMimeType.toMediaType())
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
    
    /**
     * Получить список файлов в папке
     */
    suspend fun listFiles(path: String, limit: Int = 100): List<YandexResource> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/resources?path=${Uri.encode(path)}&limit=$limit&sort=-created")
                .header("Authorization", "OAuth $oauthToken")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) return@withContext emptyList()
                throw Exception("Failed to list files: ${response.code} ${response.message}")
            }

            val body = response.body?.string() ?: throw Exception("Empty response")
            response.close()

            val resourceResponse = gson.fromJson(body, ResourceResponse::class.java)
            resourceResponse._embedded?.items ?: emptyList()
        } catch (e: Exception) {
            Log.e("YandexDisk", "List files error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Обновить пользовательские свойства (метаданные) ресурса
     */
    suspend fun patchResource(path: String, properties: Map<String, Any>) = withContext(Dispatchers.IO) {
        try {
            val bodyMap = mapOf("custom_properties" to properties)
            val jsonBody = gson.toJson(bodyMap)
            
            val request = Request.Builder()
                .url("$baseUrl/resources?path=${Uri.encode(path)}")
                .header("Authorization", "OAuth $oauthToken")
                .patch(jsonBody.asRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Failed to patch resource: ${response.code} ${response.message}")
            }
            response.close()
            Log.d("YandexDisk", "Resource patched successfully: $path")
        } catch (e: Exception) {
            Log.e("YandexDisk", "Patch resource error: ${e.message}", e)
            throw e
        }
    }

    /**
     * Определить MIME тип по имени файла
     */
    private fun detectMimeTypeFromFileName(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "json" -> "application/json"
            else -> {
                // Пытаемся определить через MimeTypeMap
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                mimeType ?: "application/octet-stream"
            }
        }
    }
    
    /**
     * Скачать содержимое файла по URL как байты
     */
    suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Failed to download content: ${response.code} ${response.message}")
            }
            
            response.body?.bytes() ?: ByteArray(0)
        } catch (e: Exception) {
            Log.e("YandexDisk", "Download bytes error: ${e.message}", e)
            throw e
        }
    }

    /**
     * Скачать содержимое файла по URL
     */
    suspend fun downloadContent(url: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Failed to download content: ${response.code} ${response.message}")
            }
            
            response.body?.string() ?: ""
        } catch (e: Exception) {
            Log.e("YandexDisk", "Download content error: ${e.message}", e)
            throw e
        }
    }

    /**
     * Загрузить содержимое (ByteArray) в файл
     */
    suspend fun uploadContent(path: String, content: ByteArray): String = withContext(Dispatchers.IO) {
        try {
            // 1. Получить URL для загрузки
            val uploadUrl = getUploadUrl(path)
            
            // 2. Загрузить
            val requestBody = content.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(uploadUrl)
                .put(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Content upload failed: ${response.code} ${response.message}")
            }
            response.close()
            
            // 3. Опубликовать (опционально, но полезно для получения ссылки)
            // Для JSON файлов нам может и не нужна публичная ссылка, если мы читаем через API
            // Но для единообразия пусть будет
            try {
                publishFile(path)
            } catch (e: Exception) {
                // Ignore if already published or error
                path // Return path if publish fails
            }
        } catch (e: Exception) {
            Log.e("YandexDisk", "Upload content error: ${e.message}", e)
            throw e
        }
    }
}

// Data classes for Yandex Disk API
data class ResourceResponse(
    val _embedded: ResourceList? = null
)

data class ResourceList(
    val items: List<YandexResource> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0
)

data class YandexResource(
    val name: String,
    val path: String,
    val created: String,
    val modified: String,
    val type: String, // "file" or "dir"
    val mime_type: String? = null,
    val size: Long = 0,
    val public_url: String? = null,
    val file: String? = null, // Direct download link
    val custom_properties: Map<String, Any>? = null
)
