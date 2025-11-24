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
     * Опубликовать файл и получить публичную ссылку
     */
    private fun publishFile(remotePath: String): String {
        // 1. Опубликовать файл
        val publishRequest = Request.Builder()
            .url("$baseUrl/resources/publish?path=${Uri.encode(remotePath)}")
            .header("Authorization", "OAuth $oauthToken")
            .put(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()
        
        val publishResponse = client.newCall(publishRequest).execute()
        if (!publishResponse.isSuccessful) {
            throw Exception("Failed to publish file: ${publishResponse.code}")
        }
        publishResponse.close()
        
        // 2. Получить публичную ссылку
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
        
        val json = gson.fromJson(body, Map::class.java)
        return json["public_url"] as? String ?: throw Exception("No public URL in response")
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
