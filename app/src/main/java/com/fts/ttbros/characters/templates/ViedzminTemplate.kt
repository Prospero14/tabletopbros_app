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
        items.add(FormItem.TextField("race", "Race", data["race"] as? String ?: "")) // Need localization later
        items.add(FormItem.TextField("profession", "Profession", data["profession"] as? String ?: ""))

        // Attributes (Generic for now, can be refined)
        items.add(FormItem.Section(r.getString(R.string.section_attributes)))
        
        items.add(FormItem.Header("Stats"))
        items.add(createDots("reflexes", "Reflexes", data))
        items.add(createDots("dexterity", "Dexterity", data))
        items.add(createDots("body", "Body", data))
        items.add(createDots("intelligence", "Intelligence", data))
        items.add(createDots("willpower", "Willpower", data))
        items.add(createDots("empathy", "Empathy", data))
        items.add(createDots("craft", "Craft", data))
        items.add(createDots("luck", "Luck", data))

        // Skills
        items.add(FormItem.Section(r.getString(R.string.section_skills)))
        items.add(createDots("swordsmanship", "Swordsmanship", data))
        items.add(createDots("archery", "Archery", data))
        items.add(createDots("magic", "Magic", data))
        items.add(createDots("alchemy", "Alchemy", data))
        
        // Other
        items.add(FormItem.Section(r.getString(R.string.section_other_traits)))
        items.add(FormItem.TextField("inventory", "Inventory", data["inventory"] as? String ?: "", isMultiline = true))

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
