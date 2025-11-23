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
        items.add(FormItem.TextField("class", r.getString(R.string.label_class), data["class"] as? String ?: ""))
        items.add(FormItem.TextField("level", r.getString(R.string.label_level), data["level"] as? String ?: ""))
        items.add(FormItem.TextField("race", r.getString(R.string.label_race), data["race"] as? String ?: ""))
        items.add(FormItem.TextField("background", "Background", data["background"] as? String ?: ""))
        items.add(FormItem.TextField("alignment", "Alignment", data["alignment"] as? String ?: ""))
        items.add(FormItem.TextField("xp", "Experience Points", data["xp"] as? String ?: ""))

        // Vitals
        items.add(FormItem.Section("Vitals"))
        items.add(FormItem.TextField("ac", "Armor Class", data["ac"] as? String ?: ""))
        items.add(FormItem.TextField("initiative", "Initiative", data["initiative"] as? String ?: ""))
        items.add(FormItem.TextField("speed", "Speed", data["speed"] as? String ?: ""))
        items.add(FormItem.TextField("hp_max", "Hit Points Max", data["hp_max"] as? String ?: ""))
        items.add(FormItem.TextField("hp_current", "Current Hit Points", data["hp_current"] as? String ?: ""))
        items.add(FormItem.TextField("hit_dice", "Hit Dice", data["hit_dice"] as? String ?: ""))

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
        items.add(createDots("animal_handling", "Animal Handling", data))
        items.add(createDots("arcana", "Arcana", data))
        items.add(createDots("athletics", r.getString(R.string.skill_athletics), data))
        items.add(createDots("deception", "Deception", data))
        items.add(createDots("history", "History", data))
        items.add(createDots("insight", r.getString(R.string.skill_insight), data))
        items.add(createDots("intimidation", "Intimidation", data))
        items.add(createDots("investigation", r.getString(R.string.skill_investigation), data))
        items.add(createDots("medicine", "Medicine", data))
        items.add(createDots("nature", "Nature", data))
        items.add(createDots("perception", r.getString(R.string.skill_perception), data))
        items.add(createDots("performance", "Performance", data))
        items.add(createDots("persuasion", "Persuasion", data))
        items.add(createDots("religion", "Religion", data))
        items.add(createDots("sleight_of_hand", "Sleight of Hand", data))
        items.add(createDots("stealth", r.getString(R.string.skill_stealth), data))
        items.add(createDots("survival", r.getString(R.string.skill_survival), data))
        
        // Other
        items.add(FormItem.Section(r.getString(R.string.section_other_traits)))
        items.add(FormItem.TextField("proficiencies", "Proficiencies & Languages", data["proficiencies"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("features", "Features & Traits", data["features"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("attacks", "Attacks & Spellcasting", data["attacks"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("equipment", "Equipment", data["equipment"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("inventory", r.getString(R.string.label_inventory), data["inventory"] as? String ?: "", isMultiline = true))

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
