# Руководство по тестированию на iOS

## Варианты тестирования KMP приложения на iOS

### Вариант 1: Xcode + iOS Simulator (Рекомендуется для начала)

**Требования:**
- Mac с установленным Xcode
- Xcode 15.0 или новее
- CocoaPods (опционально, для зависимостей)

**Шаги:**

1. **Создать iOS проект в Xcode:**
   ```bash
   # В корне проекта
   mkdir iosApp
   cd iosApp
   ```

2. **Создать Xcode проект:**
   - Открыть Xcode
   - File → New → Project
   - Выбрать "App"
   - Название: `TTBros`
   - Interface: SwiftUI или UIKit
   - Language: Swift

3. **Настроить KMP модуль:**
   - В Xcode: File → Add Files to "TTBros"
   - Выбрать `shared/build/bin/iosSimulatorArm64/debugFramework/shared.framework`
   - Или использовать CocoaPods/Swift Package Manager

4. **Собрать shared модуль:**
   ```bash
   ./gradlew :shared:iosSimulatorArm64Binaries
   ```

5. **Запустить в симуляторе:**
   - Выбрать симулятор (iPhone 15, например)
   - Нажать Run

### Вариант 2: Compose Multiplatform для iOS

**Требования:**
- Mac с Xcode
- Kotlin Multiplatform Mobile plugin для Android Studio

**Шаги:**

1. **Установить KMM plugin в Android Studio:**
   - Settings → Plugins → Kotlin Multiplatform Mobile

2. **Создать iOS target:**
   - В Android Studio: Tools → Kotlin Multiplatform Mobile → Add iOS Target
   - Выбрать iOS версию (iOS 13.0+)

3. **Настроить iOS приложение:**
   - Создать `iosApp` модуль
   - Настроить Compose Multiplatform

4. **Собрать и запустить:**
   ```bash
   ./gradlew :iosApp:iosSimulatorArm64Binaries
   ```

### Вариант 3: Физическое устройство (iPhone/iPad)

**Требования:**
- Mac с Xcode
- iPhone/iPad с iOS 13.0+
- Apple Developer Account (для подписи)

**Шаги:**

1. **Подключить устройство:**
   - Подключить iPhone через USB
   - В Xcode: Window → Devices and Simulators
   - Доверить компьютер на устройстве

2. **Настроить подпись:**
   - В Xcode: Signing & Capabilities
   - Выбрать Team (Apple Developer Account)
   - Xcode автоматически создаст provisioning profile

3. **Собрать для устройства:**
   ```bash
   ./gradlew :shared:iosArm64Binaries
   ```

4. **Запустить:**
   - Выбрать устройство в Xcode
   - Нажать Run

### Вариант 4: TestFlight (Бета-тестирование)

**Требования:**
- Apple Developer Account ($99/год)
- App Store Connect аккаунт

**Шаги:**

1. **Создать App ID в Apple Developer:**
   - https://developer.apple.com
   - Certificates, Identifiers & Profiles
   - Создать App ID: `com.fts.ttbros`

2. **Собрать архив:**
   ```bash
   ./gradlew :shared:iosArm64Binaries
   # В Xcode: Product → Archive
   ```

3. **Загрузить в App Store Connect:**
   - Xcode: Window → Organizer
   - Выбрать архив → Distribute App
   - Upload to App Store Connect

4. **Настроить TestFlight:**
   - App Store Connect → TestFlight
   - Добавить тестеров
   - Отправить приглашения

### Вариант 5: Удаленная разработка (без Mac)

**Если нет Mac, можно использовать:**

1. **MacStadium / MacinCloud:**
   - Арендовать Mac в облаке
   - Подключиться через VNC/RDP
   - Использовать Xcode удаленно

2. **GitHub Actions / CI/CD:**
   - Настроить автоматическую сборку для iOS
   - Загружать артефакты
   - Тестировать через TestFlight

3. **Коллега с Mac:**
   - Предоставить доступ к репозиторию
   - Попросить собрать и протестировать

## Рекомендации

**Для начала:** Используйте iOS Simulator на Mac - это самый быстрый способ протестировать приложение.

**Для реального тестирования:** Используйте физическое устройство или TestFlight.

**Для CI/CD:** Настройте автоматическую сборку через GitHub Actions или другой CI сервис.

## Полезные команды

```bash
# Собрать для iOS симулятора
./gradlew :shared:iosSimulatorArm64Binaries

# Собрать для физического устройства
./gradlew :shared:iosArm64Binaries

# Очистить сборку
./gradlew clean

# Проверить структуру проекта
./gradlew tasks --all
```

## Проблемы и решения

**Проблема:** "No such module 'shared'"
**Решение:** Убедитесь, что shared модуль собран и добавлен в Xcode проект

**Проблема:** "Signing for 'TTBros' requires a development team"
**Решение:** Добавьте Apple Developer Team в Xcode → Signing & Capabilities

**Проблема:** "Firebase not found"
**Решение:** Реализуйте iOS версии Firebase через KMMFirebase или interop

