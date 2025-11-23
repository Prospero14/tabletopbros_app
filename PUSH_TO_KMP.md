# Инструкция по отправке изменений в KMP ветку

## Текущее состояние:
- Переключились на ветку `multiplatform_app_ttb`
- Все изменения готовы к коммиту

## Команды для выполнения:

### 1. Проверить статус:
```bash
git status
```

### 2. Добавить все изменения:
```bash
git add .
```

### 3. Создать коммит:
```bash
git commit -m "Обновление KMP ветки: добавлена структура, дизайн изменения, документация по iOS тестированию"
```

### 4. Отправить в удаленный репозиторий:
```bash
# Если ветка еще не существует на удаленном репозитории:
git push -u origin multiplatform_app_ttb

# Если ветка уже существует:
git push origin multiplatform_app_ttb
```

## Что будет закоммичено:

1. **KMP конфигурация:**
   - settings.gradle.kts
   - gradle/libs.versions.toml
   - shared/build.gradle.kts
   - app/build.gradle.kts

2. **Дизайн изменения:**
   - app/src/main/res/drawable/bg_nav_header_rounded.xml
   - app/src/main/res/drawable/bg_drawer_rounded.xml
   - app/src/main/res/values/colors.xml
   - app/src/main/res/layout/item_chat_message.xml
   - app/src/main/java/com/fts/ttbros/chat/ui/ChatAdapter.kt

3. **KMP структура:**
   - shared/src/commonMain/kotlin/com/fts/ttbros/Platform.kt
   - shared/src/androidMain/kotlin/com/fts/ttbros/Platform.kt
   - shared/src/iosMain/kotlin/com/fts/ttbros/Platform.kt

4. **Документация:**
   - IOS_TESTING_GUIDE.md
   - QUICK_IOS_SETUP.md
   - KMP_BRANCH_STATUS.md
   - KMP_BRANCH_UPDATE.md
   - COMMIT_KMP_CHANGES.md
   - PUSH_TO_KMP.md

## Если нужно обновить существующую ветку:

Если ветка `multiplatform_app_ttb` уже существует на удаленном репозитории и нужно обновить её:

```bash
# Получить последние изменения
git fetch origin

# Если есть конфликты, разрешить их
git pull origin multiplatform_app_ttb

# Отправить изменения
git push origin multiplatform_app_ttb
```

## Проверка после отправки:

```bash
# Проверить удаленные ветки
git branch -r

# Проверить статус
git status
```

