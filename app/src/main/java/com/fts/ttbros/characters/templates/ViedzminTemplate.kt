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
        items.add(FormItem.TextField("gender", r.getString(R.string.label_gender), data["gender"] as? String ?: ""))
        items.add(FormItem.TextField("age", r.getString(R.string.label_age), data["age"] as? String ?: ""))

        // Vitals
        items.add(FormItem.Section("Vitals"))
        items.add(FormItem.TextField("hp", r.getString(R.string.label_hp), data["hp"] as? String ?: ""))
        items.add(FormItem.TextField("stamina", r.getString(R.string.label_stamina), data["stamina"] as? String ?: ""))
        items.add(FormItem.TextField("resolve", r.getString(R.string.label_resolve), data["resolve"] as? String ?: ""))
        items.add(FormItem.TextField("run", r.getString(R.string.label_run), data["run"] as? String ?: ""))
        items.add(FormItem.TextField("leap", r.getString(R.string.label_leap), data["leap"] as? String ?: ""))

        // Attributes (Generic for now, can be refined)
        items.add(FormItem.Section(r.getString(R.string.section_attributes)))
        
        items.add(FormItem.Header(r.getString(R.string.header_stats)))
        items.add(createDots("intelligence", r.getString(R.string.attr_intelligence), data))
        items.add(createDots("reflexes", r.getString(R.string.attr_reflexes), data))
        items.add(createDots("dexterity", r.getString(R.string.attr_dexterity), data))
        items.add(createDots("body", r.getString(R.string.attr_body), data))
        items.add(createDots("speed", r.getString(R.string.label_speed), data))
        items.add(createDots("empathy", r.getString(R.string.attr_empathy), data))
        items.add(createDots("craft", r.getString(R.string.attr_craft), data))
        items.add(createDots("willpower", r.getString(R.string.attr_willpower), data))
        items.add(createDots("luck", r.getString(R.string.attr_luck), data))

        // Skills
        items.add(FormItem.Section(r.getString(R.string.section_skills)))
        items.add(createDots("awareness", r.getString(R.string.skill_awareness), data))
        items.add(createDots("deduction", r.getString(R.string.skill_deduction), data))
        items.add(createDots("monster_lore", r.getString(R.string.skill_monster_lore), data))
        items.add(createDots("social_etiquette", r.getString(R.string.skill_social_etiquette), data))
        items.add(createDots("persuasion", r.getString(R.string.skill_persuasion), data))
        items.add(createDots("swordsmanship", r.getString(R.string.skill_swordsmanship), data))
        items.add(createDots("archery", r.getString(R.string.skill_archery), data))
        items.add(createDots("brawling", r.getString(R.string.skill_brawling), data))
        items.add(createDots("dodge_escape", r.getString(R.string.skill_dodge_escape), data))
        items.add(createDots("magic", r.getString(R.string.skill_magic), data))
        items.add(createDots("alchemy", r.getString(R.string.skill_alchemy), data))
        items.add(createDots("crafting", r.getString(R.string.skill_crafting), data))
        items.add(createDots("first_aid", r.getString(R.string.skill_first_aid), data))
        
        // Other
        items.add(FormItem.Section(r.getString(R.string.section_other_traits)))
        items.add(FormItem.TextField("defining_skill", r.getString(R.string.label_defining_skill), data["defining_skill"] as? String ?: "", isMultiline = true))
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
