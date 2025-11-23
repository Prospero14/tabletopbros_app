# Коммит изменений в KMP ветку

## Изменения для коммита:

1. **Конфигурация KMP:**
   - settings.gradle.kts - добавлен shared модуль
   - gradle/libs.versions.toml - добавлены KMP зависимости
   - shared/build.gradle.kts - создан KMP модуль
   - app/build.gradle.kts - добавлена зависимость на shared

2. **Дизайн изменения:**
   - bg_nav_header_rounded.xml - закругления drawer сверху
   - bg_drawer_rounded.xml - закругления drawer снизу
   - colors.xml - полупрозрачные цвета для чата
   - item_chat_message.xml - уменьшенные баблы сообщений
   - ChatAdapter.kt - обновлен для полупрозрачности

3. **Документация:**
   - IOS_TESTING_GUIDE.md - руководство по тестированию
   - QUICK_IOS_SETUP.md - быстрая настройка
   - KMP_BRANCH_STATUS.md - статус ветки

4. **Базовая KMP структура:**
   - shared/src/commonMain/kotlin/com/fts/ttbros/Platform.kt
   - shared/src/androidMain/kotlin/com/fts/ttbros/Platform.kt
   - shared/src/iosMain/kotlin/com/fts/ttbros/Platform.kt

