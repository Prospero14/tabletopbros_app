# Быстрая настройка для тестирования на iOS

## Минимальные требования

- **Mac** с macOS 12.0 или новее
- **Xcode** 15.0 или новее (бесплатно из App Store)
- **Apple ID** (для симулятора, бесплатно)

## Быстрый старт (5 минут)

### Шаг 1: Установить Xcode
1. Открыть App Store на Mac
2. Найти "Xcode"
3. Нажать "Установить" (около 12GB)

### Шаг 2: Собрать shared модуль
```bash
cd /path/to/test1
./gradlew :shared:iosSimulatorArm64Binaries
```

### Шаг 3: Создать iOS проект в Xcode
1. Открыть Xcode
2. File → New → Project
3. Выбрать "App"
4. Название: `TTBros`
5. Interface: **SwiftUI** (проще для начала)
6. Language: **Swift**
7. Сохранить проект

### Шаг 4: Добавить shared framework
1. В Xcode: File → Add Files to "TTBros"
2. Перейти к: `shared/build/bin/iosSimulatorArm64/debugFramework/`
3. Выбрать `shared.framework`
4. Убедиться, что "Copy items if needed" **НЕ** отмечено
5. Нажать "Add"

### Шаг 5: Настроить Swift код
В `ContentView.swift` или `TTBrosApp.swift`:

```swift
import SwiftUI
import shared

@main
struct TTBrosApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        Text("TTBros KMP App")
            .padding()
    }
}
```

### Шаг 6: Запустить
1. Выбрать симулятор (например, iPhone 15)
2. Нажать ▶️ (Run)
3. Приложение откроется в симуляторе!

## Если нет Mac

### Вариант 1: Арендовать Mac в облаке
- **MacStadium** - от $99/месяц
- **MacinCloud** - от $30/месяц
- **AWS EC2 Mac** - почасовая оплата

### Вариант 2: Попросить коллегу
- Предоставить доступ к репозиторию
- Попросить собрать и протестировать

### Вариант 3: GitHub Actions
- Настроить автоматическую сборку
- Использовать macOS runner
- Загружать артефакты

## Полезные команды

```bash
# Собрать для симулятора
./gradlew :shared:iosSimulatorArm64Binaries

# Собрать для физического устройства
./gradlew :shared:iosArm64Binaries

# Очистить сборку
./gradlew clean

# Показать все задачи
./gradlew tasks
```

## Типичные проблемы

**"No such module 'shared'"**
→ Убедитесь, что shared.framework добавлен в проект и собран

**"Signing for 'TTBros' requires a development team"**
→ Xcode → Signing & Capabilities → Team → Add Account (Apple ID)

**"Firebase not found"**
→ Нужно реализовать iOS версии Firebase (см. IOS_TESTING_GUIDE.md)

## Следующие шаги

После успешного запуска в симуляторе:
1. Реализовать iOS Firebase (KMMFirebase или interop)
2. Создать полноценный UI через Compose Multiplatform
3. Протестировать на физическом устройстве
4. Настроить CI/CD для автоматической сборки

