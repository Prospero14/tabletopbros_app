package com.fts.ttbros.characters.templates

import com.fts.ttbros.characters.form.FormItem
import com.fts.ttbros.R

object DndTemplate {
    fun generate(data: Map<String, Any>, context: android.content.Context): List<FormItem> {
        val items = mutableListOf<FormItem>()
        val r = context.resources

        // Header
        items.add(FormItem.Header(r.getString(R.string.dungeons_dragons)))

        // Basic Info
        items.add(FormItem.Section(r.getString(R.string.section_info)))
        items.add(FormItem.TextField("name", r.getString(R.string.label_name), data["name"] as? String ?: ""))
        items.add(FormItem.TextField("player", "Игрок", data["player"] as? String ?: ""))
        items.add(FormItem.TextField("class", r.getString(R.string.label_class), data["class"] as? String ?: ""))
        items.add(FormItem.TextField("level", r.getString(R.string.label_level), data["level"] as? String ?: ""))
        items.add(FormItem.TextField("race", r.getString(R.string.label_race), data["race"] as? String ?: ""))
        items.add(FormItem.TextField("background", r.getString(R.string.label_background), data["background"] as? String ?: ""))
        items.add(FormItem.TextField("alignment", r.getString(R.string.label_alignment), data["alignment"] as? String ?: ""))
        items.add(FormItem.TextField("xp", r.getString(R.string.label_xp), data["xp"] as? String ?: ""))

        // Vitals
        items.add(FormItem.Section("Жизненные показатели"))
        items.add(FormItem.TextField("ac", r.getString(R.string.label_ac), data["ac"] as? String ?: ""))
        items.add(FormItem.TextField("initiative", r.getString(R.string.label_initiative), data["initiative"] as? String ?: ""))
        items.add(FormItem.TextField("speed", r.getString(R.string.label_speed), data["speed"] as? String ?: ""))
        items.add(FormItem.TextField("hp_max", r.getString(R.string.label_hp_max), data["hp_max"] as? String ?: ""))
        items.add(FormItem.TextField("hp_current", r.getString(R.string.label_hp_current), data["hp_current"] as? String ?: ""))
        items.add(FormItem.TextField("hp_temp", "Временные HP", data["hp_temp"] as? String ?: ""))
        items.add(FormItem.TextField("hit_dice", r.getString(R.string.label_hit_dice), data["hit_dice"] as? String ?: ""))
        items.add(FormItem.TextField("hit_dice_total", "Всего костей хитов", data["hit_dice_total"] as? String ?: ""))
        
        // Death Saves
        items.add(FormItem.Section("Спасброски от смерти"))
        items.add(FormItem.TextField("death_save_successes", "Успехи", data["death_save_successes"] as? String ?: ""))
        items.add(FormItem.TextField("death_save_failures", "Провалы", data["death_save_failures"] as? String ?: ""))
        
        // Inspiration & Proficiency
        items.add(FormItem.Section("Вдохновение и мастерство"))
        items.add(FormItem.TextField("inspiration", "Вдохновение", data["inspiration"] as? String ?: ""))
        items.add(FormItem.TextField("proficiency_bonus", "Бонус мастерства", data["proficiency_bonus"] as? String ?: ""))
        
        // Saving Throws
        items.add(FormItem.Section("Спасброски"))
        items.add(createDots("strength_save", "Сила (сброс)", data))
        items.add(createDots("dexterity_save", "Ловкость (сброс)", data))
        items.add(createDots("constitution_save", "Выносливость (сброс)", data))
        items.add(createDots("intelligence_save", "Интеллект (сброс)", data))
        items.add(createDots("wisdom_save", "Мудрость (сброс)", data))
        items.add(createDots("charisma_save", "Харизма (сброс)", data))

        // Attributes
        items.add(FormItem.Section(r.getString(R.string.section_attributes)))
        
        items.add(createDots("strength", r.getString(R.string.attr_strength), data))
        items.add(createDots("dexterity", r.getString(R.string.attr_dexterity), data))
        items.add(createDots("constitution", r.getString(R.string.attr_constitution), data))
        items.add(createDots("intelligence", r.getString(R.string.attr_intelligence), data))
        items.add(createDots("wisdom", r.getString(R.string.attr_wisdom), data))
        items.add(createDots("charisma", r.getString(R.string.attr_charisma), data))

        // Skills
        items.add(FormItem.Section(r.getString(R.string.section_skills)))
        items.add(createDots("acrobatics", r.getString(R.string.skill_acrobatics), data))
        items.add(createDots("animal_handling", r.getString(R.string.skill_animal_handling), data))
        items.add(createDots("arcana", r.getString(R.string.skill_arcana), data))
        items.add(createDots("athletics", r.getString(R.string.skill_athletics), data))
        items.add(createDots("deception", r.getString(R.string.skill_deception), data))
        items.add(createDots("history", r.getString(R.string.skill_history), data))
        items.add(createDots("insight", r.getString(R.string.skill_insight), data))
        items.add(createDots("intimidation", r.getString(R.string.skill_intimidation), data))
        items.add(createDots("investigation", r.getString(R.string.skill_investigation), data))
        items.add(createDots("medicine", r.getString(R.string.skill_medicine), data))
        items.add(createDots("nature", r.getString(R.string.skill_nature), data))
        items.add(createDots("perception", r.getString(R.string.skill_perception), data))
        items.add(createDots("performance", r.getString(R.string.skill_performance), data))
        items.add(createDots("persuasion", r.getString(R.string.skill_persuasion), data))
        items.add(createDots("religion", r.getString(R.string.skill_religion), data))
        items.add(createDots("sleight_of_hand", r.getString(R.string.skill_sleight_of_hand), data))
        items.add(createDots("stealth", r.getString(R.string.skill_stealth), data))
        items.add(createDots("survival", r.getString(R.string.skill_survival), data))
        
        // Spellcasting
        items.add(FormItem.Section("Заклинания"))
        items.add(FormItem.TextField("spellcasting_class", "Класс заклинателя", data["spellcasting_class"] as? String ?: ""))
        items.add(FormItem.TextField("spellcasting_ability", "Характеристика заклинаний", data["spellcasting_ability"] as? String ?: ""))
        items.add(FormItem.TextField("spell_save_dc", "Сложность спасброска", data["spell_save_dc"] as? String ?: ""))
        items.add(FormItem.TextField("spell_attack_bonus", "Бонус атаки заклинаниями", data["spell_attack_bonus"] as? String ?: ""))
        items.add(FormItem.TextField("spell_slots_1", "Ячейки 1-го уровня", data["spell_slots_1"] as? String ?: ""))
        items.add(FormItem.TextField("spell_slots_2", "Ячейки 2-го уровня", data["spell_slots_2"] as? String ?: ""))
        items.add(FormItem.TextField("spell_slots_3", "Ячейки 3-го уровня", data["spell_slots_3"] as? String ?: ""))
        items.add(FormItem.TextField("spell_slots_4", "Ячейки 4-го уровня", data["spell_slots_4"] as? String ?: ""))
        items.add(FormItem.TextField("spell_slots_5", "Ячейки 5-го уровня", data["spell_slots_5"] as? String ?: ""))
        items.add(FormItem.TextField("spell_slots_6", "Ячейки 6-го уровня", data["spell_slots_6"] as? String ?: ""))
        items.add(FormItem.TextField("spell_slots_7", "Ячейки 7-го уровня", data["spell_slots_7"] as? String ?: ""))
        items.add(FormItem.TextField("spell_slots_8", "Ячейки 8-го уровня", data["spell_slots_8"] as? String ?: ""))
        items.add(FormItem.TextField("spell_slots_9", "Ячейки 9-го уровня", data["spell_slots_9"] as? String ?: ""))
        items.add(FormItem.TextField("spells_known", "Известные заклинания", data["spells_known"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("spells_prepared", "Подготовленные заклинания", data["spells_prepared"] as? String ?: "", isMultiline = true))
        
        // Attacks & Weapons
        items.add(FormItem.Section("Атаки и оружие"))
        items.add(FormItem.TextField("attacks", r.getString(R.string.label_attacks), data["attacks"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("weapons", "Оружие", data["weapons"] as? String ?: "", isMultiline = true))
        
        // Equipment & Inventory
        items.add(FormItem.Section("Снаряжение"))
        items.add(FormItem.TextField("equipment", r.getString(R.string.label_equipment), data["equipment"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("inventory", r.getString(R.string.label_inventory), data["inventory"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("cp", "Медь (м)", data["cp"] as? String ?: ""))
        items.add(FormItem.TextField("sp", "Серебро (с)", data["sp"] as? String ?: ""))
        items.add(FormItem.TextField("ep", "Электрум (э)", data["ep"] as? String ?: ""))
        items.add(FormItem.TextField("gp", "Золото (з)", data["gp"] as? String ?: ""))
        items.add(FormItem.TextField("pp", "Платина (пп)", data["pp"] as? String ?: ""))
        
        // Other Traits
        items.add(FormItem.Section(r.getString(R.string.section_other_traits)))
        items.add(FormItem.TextField("proficiencies", r.getString(R.string.label_proficiencies), data["proficiencies"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("languages", "Языки", data["languages"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("features", r.getString(R.string.label_features), data["features"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("traits", "Черты", data["traits"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("personality_traits", "Черты личности", data["personality_traits"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("ideals", "Идеалы", data["ideals"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("bonds", "Привязанности", data["bonds"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("flaws", "Пороки", data["flaws"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("notes", "Заметки", data["notes"] as? String ?: "", isMultiline = true))

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
