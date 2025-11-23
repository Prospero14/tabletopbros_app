# Команды для отправки в KMP ветку

## Быстрый способ (использовать скрипт):

### Windows (PowerShell):
```powershell
.\push_to_kmp.ps1
```

### Windows (CMD):
```cmd
push_to_kmp.bat
```

## Ручной способ:

### 1. Переключиться на KMP ветку:
```bash
git checkout multiplatform_app_ttb
```

### 2. Добавить все изменения:
```bash
git add .
```

### 3. Создать коммит:
```bash
git commit -m "Обновление KMP ветки: добавлена структура, дизайн изменения, документация по iOS тестированию

- Добавлен shared модуль с KMP конфигурацией
- Обновлен дизайн drawer (закругления)
- Обновлен дизайн чата (уменьшенные баблы, полупрозрачность)
- Добавлена документация по тестированию на iOS
- Создана базовая KMP структура"
```

### 4. Отправить в удаленный репозиторий:
```bash
# Если ветка еще не существует на удаленном:
git push -u origin multiplatform_app_ttb

# Если ветка уже существует:
git push origin multiplatform_app_ttb
```

## Проверка:

```bash
# Проверить статус
git status

# Проверить удаленные ветки
git branch -r

# Посмотреть последний коммит
git log -1
```

## Если возникли проблемы:

### Конфликты:
```bash
git pull origin multiplatform_app_ttb
# Разрешить конфликты
git add .
git commit -m "Разрешение конфликтов"
git push origin multiplatform_app_ttb
```

### Ветка не существует на удаленном:
```bash
git push -u origin multiplatform_app_ttb
```

### Нужно обновить существующую ветку:
```bash
git fetch origin
git pull origin multiplatform_app_ttb
git push origin multiplatform_app_ttb
```

