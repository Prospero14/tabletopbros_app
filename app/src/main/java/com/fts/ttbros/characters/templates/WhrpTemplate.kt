package com.fts.ttbros.characters.templates

import com.fts.ttbros.characters.form.FormItem

object WhrpTemplate {
    fun generate(data: Map<String, Any>, context: android.content.Context): List<FormItem> {
        val items = mutableListOf<FormItem>()
        val r = context.resources

        // Header
        items.add(FormItem.Header("Warhammer Fantasy Roleplay"))

        // Basic Info
        items.add(FormItem.Section("Основная информация"))
        items.add(FormItem.TextField("name", "Имя", data["name"] as? String ?: ""))
        items.add(FormItem.TextField("species", "Вид/Раса", data["species"] as? String ?: ""))
        items.add(FormItem.TextField("career", "Профессия", data["career"] as? String ?: ""))
        items.add(FormItem.TextField("career_level", "Уровень профессии", data["career_level"] as? String ?: ""))
        items.add(FormItem.TextField("age", "Возраст", data["age"] as? String ?: ""))
        items.add(FormItem.TextField("height", "Рост", data["height"] as? String ?: ""))
        items.add(FormItem.TextField("hair", "Волосы", data["hair"] as? String ?: ""))
        items.add(FormItem.TextField("eyes", "Глаза", data["eyes"] as? String ?: ""))
        items.add(FormItem.TextField("distinguishing_marks", "Отличительные черты", data["distinguishing_marks"] as? String ?: ""))
        items.add(FormItem.TextField("birthplace", "Место рождения", data["birthplace"] as? String ?: ""))
        items.add(FormItem.TextField("star_sign", "Знак зодиака", data["star_sign"] as? String ?: ""))
        items.add(FormItem.TextField("siblings", "Братья и сестры", data["siblings"] as? String ?: ""))
        items.add(FormItem.TextField("motivation", "Мотивация", data["motivation"] as? String ?: "", isMultiline = true))

        // Attributes
        items.add(FormItem.Section("Характеристики"))
        items.add(createDots("weapon_skill", "Навык владения оружием (WS)", data))
        items.add(createDots("ballistic_skill", "Навык стрельбы (BS)", data))
        items.add(createDots("strength", "Сила (S)", data))
        items.add(createDots("toughness", "Выносливость (T)", data))
        items.add(createDots("initiative", "Инициатива (I)", data))
        items.add(createDots("agility", "Ловкость (Ag)", data))
        items.add(createDots("dexterity", "Интеллект (Dex)", data))
        items.add(createDots("intelligence", "Интеллект (Int)", data))
        items.add(createDots("willpower", "Сила воли (WP)", data))
        items.add(createDots("fellowship", "Общительность (Fel)", data))

        // Basic Skills
        items.add(FormItem.Section("Базовые навыки"))
        items.add(createDots("animal_care", "Уход за животными", data))
        items.add(createDots("art", "Искусство", data))
        items.add(createDots("athletics", "Атлетика", data))
        items.add(createDots("bribery", "Взятка", data))
        items.add(createDots("charm", "Обаяние", data))
        items.add(createDots("climb", "Лазание", data))
        items.add(createDots("consume_alcohol", "Употребление алкоголя", data))
        items.add(createDots("cool", "Хладнокровие", data))
        items.add(createDots("dodge", "Уклонение", data))
        items.add(createDots("drive", "Вождение", data))
        items.add(createDots("endurance", "Выносливость", data))
        items.add(createDots("entertain", "Развлечение", data))
        items.add(createDots("gamble", "Азартные игры", data))
        items.add(createDots("gossip", "Сплетни", data))
        items.add(createDots("haggle", "Торговля", data))
        items.add(createDots("heal", "Лечение", data))
        items.add(createDots("intimidate", "Запугивание", data))
        items.add(createDots("intuition", "Интуиция", data))
        items.add(createDots("language", "Язык", data))
        items.add(createDots("leadership", "Лидерство", data))
        items.add(createDots("melee", "Ближний бой", data))
        items.add(createDots("navigation", "Навигация", data))
        items.add(createDots("outdoor_survival", "Выживание", data))
        items.add(createDots("perception", "Восприятие", data))
        items.add(createDots("ride", "Верховая езда", data))
        items.add(createDots("row", "Гребля", data))
        items.add(createDots("stealth", "Скрытность", data))
        items.add(createDots("swim", "Плавание", data))

        // Advanced Skills
        items.add(FormItem.Section("Продвинутые навыки"))
        items.add(createDots("animal_training", "Дрессировка животных", data))
        items.add(createDots("artistic", "Художественное", data))
        items.add(createDots("channelling", "Канализация", data))
        items.add(createDots("charm_animal", "Очарование животных", data))
        items.add(createDots("command", "Командование", data))
        items.add(createDots("commerce", "Коммерция", data))
        items.add(createDots("evaluate", "Оценка", data))
        items.add(createDots("folklore", "Фольклор", data))
        items.add(createDots("guile", "Хитрость", data))
        items.add(createDots("lore", "Знание", data))
        items.add(createDots("perform", "Выступление", data))
        items.add(createDots("play", "Игра", data))
        items.add(createDots("pray", "Молитва", data))
        items.add(createDots("ranged", "Дальний бой", data))
        items.add(createDots("research", "Исследование", data))
        items.add(createDots("sail", "Парусное дело", data))
        items.add(createDots("secret_signs", "Тайные знаки", data))
        items.add(createDots("set_trap", "Установка ловушек", data))
        items.add(createDots("sleight_of_hand", "Ловкость рук", data))
        items.add(createDots("track", "Выслеживание", data))
        items.add(createDots("trade", "Ремесло", data))
        items.add(createDots("ventriloquism", "Чревовещание", data))

        // Stats
        items.add(FormItem.Section("Статистики"))
        items.add(FormItem.TextField("wounds", "Раны", data["wounds"] as? String ?: ""))
        items.add(FormItem.TextField("wounds_max", "Максимум ран", data["wounds_max"] as? String ?: ""))
        items.add(FormItem.TextField("fate_points", "Очки судьбы", data["fate_points"] as? String ?: ""))
        items.add(FormItem.TextField("fortune_points", "Очки удачи", data["fortune_points"] as? String ?: ""))
        items.add(FormItem.TextField("resilience", "Устойчивость", data["resilience"] as? String ?: ""))
        items.add(FormItem.TextField("resolve", "Решимость", data["resolve"] as? String ?: ""))
        items.add(FormItem.TextField("advancement_points", "Очки развития", data["advancement_points"] as? String ?: ""))
        items.add(FormItem.TextField("movement", "Движение", data["movement"] as? String ?: ""))

        // Armour
        items.add(FormItem.Section("Броня"))
        items.add(FormItem.TextField("armour_head", "Броня головы", data["armour_head"] as? String ?: ""))
        items.add(FormItem.TextField("armour_body", "Броня тела", data["armour_body"] as? String ?: ""))
        items.add(FormItem.TextField("armour_arms", "Броня рук", data["armour_arms"] as? String ?: ""))
        items.add(FormItem.TextField("armour_legs", "Броня ног", data["armour_legs"] as? String ?: ""))

        // Other
        items.add(FormItem.Section("Прочее"))
        items.add(FormItem.TextField("weapons", "Оружие", data["weapons"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("trappings", "Снаряжение", data["trappings"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("money", "Деньги", data["money"] as? String ?: ""))
        items.add(FormItem.TextField("social_standing", "Социальное положение", data["social_standing"] as? String ?: ""))
        items.add(FormItem.TextField("corruption", "Порча", data["corruption"] as? String ?: ""))
        items.add(FormItem.TextField("mutations", "Мутации", data["mutations"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("psychology", "Психология", data["psychology"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("talents", "Таланты", data["talents"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("traits", "Черты", data["traits"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("career_advances", "Развитие профессии", data["career_advances"] as? String ?: "", isMultiline = true))

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

