package com.fts.ttbros.characters.templates

import com.fts.ttbros.characters.form.FormItem

object DarkHeresyTemplate {
    fun generate(data: Map<String, Any>, context: android.content.Context): List<FormItem> {
        val items = mutableListOf<FormItem>()
        val r = context.resources

        // Header
        items.add(FormItem.Header("Warhammer 40k: Dark Heresy"))

        // Basic Info
        items.add(FormItem.Section("Основная информация"))
        items.add(FormItem.TextField("name", "Имя", data["name"] as? String ?: ""))
        items.add(FormItem.TextField("home_world", "Родной мир", data["home_world"] as? String ?: ""))
        items.add(FormItem.TextField("background", "Происхождение", data["background"] as? String ?: ""))
        items.add(FormItem.TextField("role", "Роль", data["role"] as? String ?: ""))
        items.add(FormItem.TextField("rank", "Ранг", data["rank"] as? String ?: ""))
        items.add(FormItem.TextField("divination", "Дивинация", data["divination"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("age", "Возраст", data["age"] as? String ?: ""))
        items.add(FormItem.TextField("build", "Телосложение", data["build"] as? String ?: ""))
        items.add(FormItem.TextField("complexion", "Цвет лица", data["complexion"] as? String ?: ""))
        items.add(FormItem.TextField("hair", "Волосы", data["hair"] as? String ?: ""))
        items.add(FormItem.TextField("eyes", "Глаза", data["eyes"] as? String ?: ""))
        items.add(FormItem.TextField("quirk", "Причуда", data["quirk"] as? String ?: ""))
        items.add(FormItem.TextField("superstition", "Суеверие", data["superstition"] as? String ?: ""))

        // Attributes
        items.add(FormItem.Section("Характеристики"))
        items.add(createDots("weapon_skill", "Навык владения оружием (WS)", data))
        items.add(createDots("ballistic_skill", "Навык стрельбы (BS)", data))
        items.add(createDots("strength", "Сила (S)", data))
        items.add(createDots("toughness", "Выносливость (T)", data))
        items.add(createDots("agility", "Ловкость (Ag)", data))
        items.add(createDots("intelligence", "Интеллект (Int)", data))
        items.add(createDots("perception", "Восприятие (Per)", data))
        items.add(createDots("willpower", "Сила воли (WP)", data))
        items.add(createDots("fellowship", "Общительность (Fel)", data))

        // Basic Skills
        items.add(FormItem.Section("Базовые навыки"))
        items.add(createDots("awareness", "Внимательность", data))
        items.add(createDots("barter", "Торговля", data))
        items.add(createDots("carouse", "Пьянство", data))
        items.add(createDots("charm", "Обаяние", data))
        items.add(createDots("climb", "Лазание", data))
        items.add(createDots("concealment", "Скрытие", data))
        items.add(createDots("contortionist", "Акробатика", data))
        items.add(createDots("deceive", "Обман", data))
        items.add(createDots("disguise", "Маскировка", data))
        items.add(createDots("dodge", "Уклонение", data))
        items.add(createDots("drive", "Вождение", data))
        items.add(createDots("evaluate", "Оценка", data))
        items.add(createDots("gamble", "Азартные игры", data))
        items.add(createDots("inquiry", "Допрос", data))
        items.add(createDots("interrogation", "Допрос", data))
        items.add(createDots("intimidate", "Запугивание", data))
        items.add(createDots("logic", "Логика", data))
        items.add(createDots("scrutiny", "Осмотр", data))
        items.add(createDots("search", "Поиск", data))
        items.add(createDots("silent_move", "Тихий шаг", data))
        items.add(createDots("speak_language", "Языки", data))
        items.add(createDots("swim", "Плавание", data))
        items.add(createDots("trade", "Ремесло", data))

        // Advanced Skills
        items.add(FormItem.Section("Продвинутые навыки"))
        items.add(createDots("acrobatics", "Акробатика", data))
        items.add(createDots("blather", "Болтовня", data))
        items.add(createDots("chem_use", "Использование химии", data))
        items.add(createDots("ciphers", "Шифры", data))
        items.add(createDots("command", "Командование", data))
        items.add(createDots("commerce", "Коммерция", data))
        items.add(createDots("common_lore", "Общее знание", data))
        items.add(createDots("demolition", "Подрывное дело", data))
        items.add(createDots("forbidden_lore", "Запретное знание", data))
        items.add(createDots("literacy", "Грамотность", data))
        items.add(createDots("medicae", "Медицина", data))
        items.add(createDots("navigate", "Навигация", data))
        items.add(createDots("operate", "Управление", data))
        items.add(createDots("parry", "Парирование", data))
        items.add(createDots("performer", "Выступление", data))
        items.add(createDots("pilot", "Пилотирование", data))
        items.add(createDots("psyniscience", "Пси-наука", data))
        items.add(createDots("scholastic_lore", "Ученое знание", data))
        items.add(createDots("secret_tongue", "Тайный язык", data))
        items.add(createDots("security", "Безопасность", data))
        items.add(createDots("shadowing", "Выслеживание", data))
        items.add(createDots("sleight_of_hand", "Ловкость рук", data))
        items.add(createDots("survival", "Выживание", data))
        items.add(createDots("tech_use", "Использование техники", data))
        items.add(createDots("tracking", "Выслеживание", data))
        items.add(createDots("wrangling", "Управление животными", data))

        // Stats
        items.add(FormItem.Section("Статистики"))
        items.add(FormItem.TextField("wounds", "Раны", data["wounds"] as? String ?: ""))
        items.add(FormItem.TextField("wounds_max", "Максимум ран", data["wounds_max"] as? String ?: ""))
        items.add(FormItem.TextField("fate_points", "Очки судьбы", data["fate_points"] as? String ?: ""))
        items.add(FormItem.TextField("insanity_points", "Очки безумия", data["insanity_points"] as? String ?: ""))
        items.add(FormItem.TextField("corruption_points", "Очки порчи", data["corruption_points"] as? String ?: ""))
        items.add(FormItem.TextField("movement", "Движение", data["movement"] as? String ?: ""))
        items.add(FormItem.TextField("experience_points", "Очки опыта", data["experience_points"] as? String ?: ""))
        items.add(FormItem.TextField("spent_experience", "Потраченный опыт", data["spent_experience"] as? String ?: ""))
        items.add(FormItem.TextField("available_experience", "Доступный опыт", data["available_experience"] as? String ?: ""))

        // Psychic Powers (if Psyker)
        items.add(FormItem.Section("Психические силы"))
        items.add(FormItem.TextField("psychic_rating", "Рейтинг псионика", data["psychic_rating"] as? String ?: ""))
        items.add(FormItem.TextField("sanctioning", "Санкционирование", data["sanctioning"] as? String ?: ""))
        items.add(FormItem.TextField("psychic_powers", "Психические силы", data["psychic_powers"] as? String ?: "", isMultiline = true))

        // Armour
        items.add(FormItem.Section("Броня"))
        items.add(FormItem.TextField("armour_head", "Броня головы", data["armour_head"] as? String ?: ""))
        items.add(FormItem.TextField("armour_body", "Броня тела", data["armour_body"] as? String ?: ""))
        items.add(FormItem.TextField("armour_arms", "Броня рук", data["armour_arms"] as? String ?: ""))
        items.add(FormItem.TextField("armour_legs", "Броня ног", data["armour_legs"] as? String ?: ""))

        // Knowledge
        items.add(FormItem.Section("Знания"))
        items.add(FormItem.TextField("forbidden_lore_list", "Запретное знание", data["forbidden_lore_list"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("scholastic_lore_list", "Ученое знание", data["scholastic_lore_list"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("common_lore_list", "Общее знание", data["common_lore_list"] as? String ?: "", isMultiline = true))

        // Other
        items.add(FormItem.Section("Прочее"))
        items.add(FormItem.TextField("weapons", "Оружие", data["weapons"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("gear", "Снаряжение", data["gear"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("thrones", "Троны (деньги)", data["thrones"] as? String ?: ""))
        items.add(FormItem.TextField("talents", "Таланты", data["talents"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("traits", "Черты", data["traits"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("malignancies", "Злокачественности", data["malignancies"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("mutations", "Мутации", data["mutations"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("mental_disorders", "Психические расстройства", data["mental_disorders"] as? String ?: "", isMultiline = true))

        return items
    }

    private fun createDots(key: String, label: String, data: Map<String, Any>): FormItem.DotsField {
        val value = when (val v = data[key]) {
            is Int -> v
            is Long -> v.toInt()
            else -> 0
        }
        return FormItem.DotsField(key, label, value)
    }
}

