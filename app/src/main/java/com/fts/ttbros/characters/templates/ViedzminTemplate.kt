package com.fts.ttbros.characters.templates

import com.fts.ttbros.characters.form.FormItem
import com.fts.ttbros.R

object ViedzminTemplate {
    fun generate(data: Map<String, Any>, context: android.content.Context): List<FormItem> {
        val items = mutableListOf<FormItem>()
        val r = context.resources

        // Header
        items.add(FormItem.Header(r.getString(R.string.viedzmin_2e)))

        // Basic Info
        items.add(FormItem.Section(r.getString(R.string.section_info)))
        items.add(FormItem.TextField("name", r.getString(R.string.label_name), data["name"] as? String ?: ""))
        items.add(FormItem.TextField("race", r.getString(R.string.label_race), data["race"] as? String ?: ""))
        items.add(FormItem.TextField("profession", r.getString(R.string.label_profession), data["profession"] as? String ?: ""))
        items.add(FormItem.TextField("gender", "Gender", data["gender"] as? String ?: ""))
        items.add(FormItem.TextField("age", "Age", data["age"] as? String ?: ""))

        // Vitals
        items.add(FormItem.Section("Vitals"))
        items.add(FormItem.TextField("hp", "Health Points", data["hp"] as? String ?: ""))
        items.add(FormItem.TextField("stamina", "Stamina", data["stamina"] as? String ?: ""))
        items.add(FormItem.TextField("resolve", "Resolve", data["resolve"] as? String ?: ""))
        items.add(FormItem.TextField("run", "Run", data["run"] as? String ?: ""))
        items.add(FormItem.TextField("leap", "Leap", data["leap"] as? String ?: ""))

        // Attributes (Generic for now, can be refined)
        items.add(FormItem.Section(r.getString(R.string.section_attributes)))
        
        items.add(FormItem.Header(r.getString(R.string.header_stats)))
        items.add(createDots("intelligence", r.getString(R.string.attr_intelligence), data))
        items.add(createDots("reflexes", r.getString(R.string.attr_reflexes), data))
        items.add(createDots("dexterity", r.getString(R.string.attr_dexterity), data))
        items.add(createDots("body", r.getString(R.string.attr_body), data))
        items.add(createDots("speed", "Speed", data))
        items.add(createDots("empathy", r.getString(R.string.attr_empathy), data))
        items.add(createDots("craft", r.getString(R.string.attr_craft), data))
        items.add(createDots("willpower", r.getString(R.string.attr_willpower), data))
        items.add(createDots("luck", r.getString(R.string.attr_luck), data))

        // Skills
        items.add(FormItem.Section(r.getString(R.string.section_skills)))
        items.add(createDots("awareness", "Awareness", data))
        items.add(createDots("deduction", "Deduction", data))
        items.add(createDots("monster_lore", "Monster Lore", data))
        items.add(createDots("social_etiquette", "Social Etiquette", data))
        items.add(createDots("persuasion", "Persuasion", data))
        items.add(createDots("swordsmanship", r.getString(R.string.skill_swordsmanship), data))
        items.add(createDots("archery", r.getString(R.string.skill_archery), data))
        items.add(createDots("brawling", "Brawling", data))
        items.add(createDots("dodge_escape", "Dodge/Escape", data))
        items.add(createDots("magic", r.getString(R.string.skill_magic), data))
        items.add(createDots("alchemy", r.getString(R.string.skill_alchemy), data))
        items.add(createDots("crafting", "Crafting", data))
        items.add(createDots("first_aid", "First Aid", data))
        
        // Other
        items.add(FormItem.Section(r.getString(R.string.section_other_traits)))
        items.add(FormItem.TextField("defining_skill", "Defining Skill", data["defining_skill"] as? String ?: "", isMultiline = true))
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
