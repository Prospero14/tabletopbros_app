# Настройка индексов Firestore

Для работы приложения необходимо создать составные индексы в Firebase Console.

## Инструкция:

1. Откройте [Firebase Console](https://console.firebase.google.com/)
2. Выберите проект `tabletopbros-b49c9`
3. Перейдите в Firestore Database → Indexes
4. Нажмите "Add Index" и создайте следующие индексы:

### Индекс для опросов (polls):
- Collection ID: `polls`
- Fields:
  - `teamId` (Ascending)
  - `chatType` (Ascending)
  - `createdAt` (Descending)

### Индекс для событий (events):
- Collection ID: `events`
- Fields:
  - `teamId` (Ascending)
  - `dateTime` (Ascending)

## Альтернативный способ (через firebase.json):

Если у вас настроен Firebase CLI, выполните:
```bash
firebase deploy --only firestore:indexes
```

Файл `firestore.indexes.json` уже создан в корне проекта.

## Важно:

После создания индексов нужно подождать несколько минут, пока они будут построены. После этого ошибки "index not found" должны исчезнуть.

