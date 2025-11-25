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

// Data classes for Yandex.Disk API
data class YandexResourceList(
    val items: List<YandexResource>
)

data class YandexResourceResponse(
    val _embedded: YandexResourceList?
)

data class YandexResource(
    val name: String,
    val path: String,
    val created: String,
    val modified: String,
    val type: String, // "file" or "dir"
    val mime_type: String?,
    val size: Long?,
    val public_url: String?,
    val file: String?, // Direct link
    val custom_properties: Map<String, String>?
)

data class CharacterSheetUploadResult(
    val publicUrl: String,
    val remotePath: String,
    val fileName: String
)

data class CustomProperties(
    val custom_properties: Map<String, String>
)

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
        isMaterial: Boolean = false,
        metadata: Map<String, String>? = null
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
            
            // 6. Если есть метаданные, сохранить их
            if (metadata != null) {
                patchResource(remotePath, metadata)
                Log.d("YandexDisk", "Metadata saved")
            }
            
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
     * Получить ссылку на скачивание (публикует файл если нужно)
     */
    suspend fun getDownloadUrl(remotePath: String): String = withContext(Dispatchers.IO) {
        publishFile(remotePath)
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
        teamId: String,
        userId: String,
        pdfUri: Uri,
        context: Context
    ): CharacterSheetUploadResult = withContext(Dispatchers.IO) {
        try {
            // 1. Создать путь на Яндекс.Диске
            val fileName = "character_sheet_${userId}_${System.currentTimeMillis()}.pdf"
            val remotePath = "/TTBros/teams/$teamId/character_sheets/$fileName"
            Log.d("YandexDisk", "Uploading character sheet to: $remotePath")
            
            // 2. Создать папки если не существуют
            createFolderIfNeeded("/TTBros")
            createFolderIfNeeded("/TTBros/teams")
            createFolderIfNeeded("/TTBros/teams/$teamId")
            createFolderIfNeeded("/TTBros/teams/$teamId/character_sheets")
            
            // 3. Получить URL для загрузки
            val uploadUrl = getUploadUrl(remotePath)
            Log.d("YandexDisk", "Upload URL obtained")
            
            // 4. Загрузить файл с правильным MIME типом для PDF
            uploadPdfFileToUrl(uploadUrl, pdfUri, context)
            Log.d("YandexDisk", "Character sheet uploaded successfully")
            
            // 5. Опубликовать файл и получить публичную ссылку
            val publicUrl = publishFile(remotePath)
            Log.d("YandexDisk", "Public character sheet URL: $publicUrl")
            
            CharacterSheetUploadResult(
                publicUrl = publicUrl,
                remotePath = remotePath,
                fileName = fileName
            )
        } catch (e: Exception) {
            Log.e("YandexDisk", "Character sheet upload error: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Получить список ресурсов в папке
     */
    suspend fun listResources(path: String): List<YandexResource> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/resources?path=${Uri.encode(path)}&limit=100&fields=_embedded.items.name,_embedded.items.path,_embedded.items.created,_embedded.items.modified,_embedded.items.type,_embedded.items.mime_type,_embedded.items.size,_embedded.items.public_url,_embedded.items.file,_embedded.items.custom_properties")
                .header("Authorization", "OAuth $oauthToken")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) return@withContext emptyList()
                throw Exception("List resources failed: ${response.code} ${response.message}")
            }
            
            val body = response.body?.string() ?: return@withContext emptyList()
            response.close()
            
            val resourceResponse = gson.fromJson(body, YandexResourceResponse::class.java)
            resourceResponse._embedded?.items ?: emptyList()
        } catch (e: Exception) {
            Log.e("YandexDisk", "List resources error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getResource(path: String): YandexResource? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/resources?path=${Uri.encode(path)}&fields=name,path,created,modified,type,mime_type,size,public_url,file,custom_properties")
                .header("Authorization", "OAuth $oauthToken")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) return@withContext null
                throw Exception("Get resource failed: ${response.code} ${response.message}")
            }
            val body = response.body?.string() ?: return@withContext null
            response.close()
            gson.fromJson(body, YandexResource::class.java)
        } catch (e: Exception) {
            Log.e("YandexDisk", "Get resource error: ${e.message}", e)
            null
        }
    }

    /**
     * Загрузить JSON контент в файл по указанному пути
     */
    suspend fun uploadJson(remotePath: String, jsonContent: String, metadata: Map<String, String>? = null) = withContext(Dispatchers.IO) {
        try {
            // 1. Создать папки
            val parentPath = remotePath.substringBeforeLast("/")
            createFolderRecursive(parentPath)
            
            // 2. Получить URL для загрузки
            val uploadUrl = getUploadUrl(remotePath)
            
            // 3. Загрузить контент
            val request = Request.Builder()
                .url(uploadUrl)
                .put(jsonContent.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Upload failed: ${response.code} ${response.message}")
            }
            response.close()
            
            // 4. Опубликовать (не обязательно для JSON, но может пригодиться)
            // val publicUrl = publishFile(remotePath)
            
            // 5. Сохранить метаданные
            if (metadata != null) {
                patchResource(remotePath, metadata)
            }
        } catch (e: Exception) {
            Log.e("YandexDisk", "Upload JSON error: ${e.message}", e)
            throw e
        }
    }

    private fun createFolderRecursive(path: String) {
        if (path.isEmpty() || path == "/") return
        
        // Split path into components
        val parts = path.split("/").filter { it.isNotEmpty() }
        var currentPath = ""
        
        for (part in parts) {
            currentPath += "/$part"
            createFolderIfNeeded(currentPath)
        }
    }

    /**
     * Прочитать JSON файл и преобразовать в объект
     */
    suspend fun <T> readJson(remotePath: String, classOfT: Class<T>): T? = withContext(Dispatchers.IO) {
        try {
            // 1. Получить ссылку на скачивание
            val request = Request.Builder()
                .url("$baseUrl/resources/download?path=${Uri.encode(remotePath)}")
                .header("Authorization", "OAuth $oauthToken")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) return@withContext null
                throw Exception("Get download link failed: ${response.code} ${response.message}")
            }
            
            val body = response.body?.string() ?: return@withContext null
            response.close()
            
            val json = gson.fromJson(body, Map::class.java)
            val href = json["href"] as? String ?: return@withContext null
            
            // 2. Скачать файл
            val downloadRequest = Request.Builder()
                .url(href)
                .get()
                .build()
                
            val downloadResponse = client.newCall(downloadRequest).execute()
            if (!downloadResponse.isSuccessful) return@withContext null
            
            val jsonContent = downloadResponse.body?.string() ?: return@withContext null
            downloadResponse.close()
            
            gson.fromJson(jsonContent, classOfT)
        } catch (e: Exception) {
            Log.e("YandexDisk", "Read JSON error: ${e.message}", e)
            null
        }
    }

    /**
     * Обновить пользовательские свойства (метаданные) файла
     */
    suspend fun patchResource(path: String, properties: Map<String, String>) = withContext(Dispatchers.IO) {
        try {
            val body = CustomProperties(properties)
            val jsonBody = gson.toJson(body)
            
            val request = Request.Builder()
                .url("$baseUrl/resources?path=${Uri.encode(path)}")
                .header("Authorization", "OAuth $oauthToken")
                .patch(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w("YandexDisk", "Patch resource failed: ${response.code} ${response.message}")
            }
            response.close()
        } catch (e: Exception) {
            Log.e("YandexDisk", "Patch resource error: ${e.message}", e)
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
     * Копировать ресурс на Яндекс.Диске
     */
    suspend fun copyResource(fromPath: String, toPath: String) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/resources/copy?from=${Uri.encode(fromPath)}&path=${Uri.encode(toPath)}&overwrite=true")
                .header("Authorization", "OAuth $oauthToken")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                // Если ошибка, читаем тело ответа для деталей
                val errorBody = response.body?.string()
                Log.e("YandexDisk", "Copy failed: ${response.code} $errorBody")
                throw Exception("Copy failed: ${response.code}")
            }
            response.close()
            Log.d("YandexDisk", "Resource copied from $fromPath to $toPath")
        } catch (e: Exception) {
            Log.e("YandexDisk", "Copy error: ${e.message}", e)
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
            else -> {
                // Пытаемся определить через MimeTypeMap
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                mimeType ?: "application/octet-stream"
            }
        }
    }
    
    /**
     * Определить MIME тип по URI
     */
    private fun detectMimeType(fileUri: Uri, context: Context): String {
        // Сначала пытаемся получить из ContentResolver
        val mimeType = context.contentResolver.getType(fileUri)
        if (!mimeType.isNullOrBlank()) {
            return mimeType
        }
        
        // Если не получилось, определяем по имени файла
        val fileName = fileUri.lastPathSegment ?: ""
        return detectMimeTypeFromFileName(fileName)
    }
}
