# Firebase Storage Rules для загрузки документов

## Проблема
Ошибка "object does not exist in this location" при загрузке документов в Firebase Storage.

## Решение

### 1. Исправление кода (✅ Выполнено)
Изменён метод `uploadDocument()` в `DocumentRepository.kt`:
- Получение размера файла из результата загрузки вместо отдельного запроса метаданных
- Добавлено подробное логирование для отладки
- Улучшена обработка ошибок

### 2. Настройка Firebase Storage Rules

Откройте Firebase Console и установите следующие правила для Storage:

```javascript
rules_version = '2';

service firebase.storage {
  match /b/{bucket}/o {
    // Правила для документов команд
    match /teams/{teamId}/documents/{document} {
      // Разрешить чтение всем участникам команды
      allow read: if request.auth != null;
      
      // Разрешить запись (загрузку) только авторизованным пользователям
      allow write: if request.auth != null;
      
      // Разрешить удаление только авторизованным пользователям
      allow delete: if request.auth != null;
    }
    
    // Запретить доступ ко всем остальным файлам по умолчанию
    match /{allPaths=**} {
      allow read, write: if false;
    }
  }
}
```

### 3. Как применить правила

1. Откройте [Firebase Console](https://console.firebase.google.com/)
2. Выберите ваш проект **tabletopbros-b49c9**
3. Перейдите в раздел **Storage** → **Rules**
4. Скопируйте правила выше
5. Нажмите **Publish**

### 4. Проверка логов

После применения правил и установки обновлённого APK:

1. Откройте Android Studio → Logcat
2. Фильтр: `DocumentRepository`
3. Попробуйте загрузить документ
4. Проверьте логи:
   ```
   D/DocumentRepository: Uploading file to: teams/{teamId}/documents/{uuid}_{filename}
   D/DocumentRepository: File uploaded successfully
   D/DocumentRepository: Download URL obtained: https://...
   D/DocumentRepository: File size: {size} bytes
   D/DocumentRepository: Document metadata saved to Firestore
   ```

### 5. Возможные ошибки и решения

#### Ошибка: "Permission denied"
**Причина:** Неправильные правила Storage  
**Решение:** Проверьте, что правила опубликованы и пользователь авторизован

#### Ошибка: "Object does not exist"
**Причина:** Файл не был загружен или путь неверный  
**Решение:** Проверьте логи, убедитесь что путь правильный

#### Ошибка: "Network error"
**Причина:** Нет интернет-соединения  
**Решение:** Проверьте подключение к интернету

### 6. Тестирование

1. Войдите в приложение как мастер
2. Откройте раздел **Документы**
3. Нажмите кнопку **+**
4. Выберите PDF файл
5. Введите название
6. Нажмите **Upload**
7. Проверьте, что документ появился в списке
8. Проверьте Firebase Console → Storage → files → teams → {teamId} → documents

## Изменения в коде

### DocumentRepository.kt

```kotlin
suspend fun uploadDocument(...) {
    try {
        // 1. Upload to Storage
        val storageRef = storage.reference
            .child("teams/$teamId/documents/${UUID.randomUUID()}_$fileName")
        
        // Upload file and get result (вместо отдельного вызова metadata)
        val uploadTask = storageRef.putFile(uri).await()
        
        // Get download URL
        val downloadUrl = storageRef.downloadUrl.await().toString()
        
        // Get file size from upload task metadata
        val size = uploadTask.metadata?.sizeBytes ?: 0L
        
        // 2. Save to Firestore
        val docMap = hashMapOf(...)
        db.collection("teams")...add(docMap).await()
        
    } catch (e: Exception) {
        android.util.Log.e("DocumentRepository", "Upload error: ${e.message}", e)
        throw e
    }
}
```

## Статус
✅ Код исправлен и собран  
⚠️ Требуется настройка Firebase Storage Rules (см. выше)
