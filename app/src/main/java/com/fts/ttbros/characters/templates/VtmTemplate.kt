package com.fts.ttbros.characters.templates

import com.fts.ttbros.characters.form.FormItem

object VtmTemplate {
    fun generate(data: Map<String, Any>, context: android.content.Context): List<FormItem> {
        val items = mutableListOf<FormItem>()
        val r = context.resources

        // Header
        items.add(FormItem.Header(r.getString(com.fts.ttbros.R.string.vampire_masquerade)))

        // Basic Info
        items.add(FormItem.Section(r.getString(com.fts.ttbros.R.string.section_info)))
        items.add(FormItem.TextField("name", r.getString(com.fts.ttbros.R.string.label_name), data["name"] as? String ?: ""))
        items.add(FormItem.TextField("concept", r.getString(com.fts.ttbros.R.string.label_concept), data["concept"] as? String ?: ""))
        items.add(FormItem.TextField("chronicle", r.getString(com.fts.ttbros.R.string.label_chronicle), data["chronicle"] as? String ?: ""))
        items.add(FormItem.TextField("ambition", r.getString(com.fts.ttbros.R.string.label_ambition), data["ambition"] as? String ?: ""))
        items.add(FormItem.TextField("desire", r.getString(com.fts.ttbros.R.string.label_desire), data["desire"] as? String ?: ""))
        items.add(FormItem.TextField("predator", r.getString(com.fts.ttbros.R.string.label_predator), data["predator"] as? String ?: ""))
        items.add(FormItem.TextField("clan", r.getString(com.fts.ttbros.R.string.label_clan), data["clan"] as? String ?: ""))
        items.add(FormItem.TextField("generation", r.getString(com.fts.ttbros.R.string.label_generation), data["generation"] as? String ?: ""))
        items.add(FormItem.TextField("sire", r.getString(com.fts.ttbros.R.string.label_sire), data["sire"] as? String ?: ""))

        // Attributes
        items.add(FormItem.Section(r.getString(com.fts.ttbros.R.string.section_attributes)))
        
        items.add(FormItem.Header(r.getString(com.fts.ttbros.R.string.header_physical)))
        items.add(createDots("strength", r.getString(com.fts.ttbros.R.string.attr_strength), data))
        items.add(createDots("dexterity", r.getString(com.fts.ttbros.R.string.attr_dexterity), data))
        items.add(createDots("stamina", r.getString(com.fts.ttbros.R.string.attr_stamina), data))

        items.add(FormItem.Header(r.getString(com.fts.ttbros.R.string.header_social)))
        items.add(createDots("charisma", r.getString(com.fts.ttbros.R.string.attr_charisma), data))
        items.add(createDots("manipulation", r.getString(com.fts.ttbros.R.string.attr_manipulation), data))
        items.add(createDots("composure", r.getString(com.fts.ttbros.R.string.attr_composure), data))

        items.add(FormItem.Header(r.getString(com.fts.ttbros.R.string.header_mental)))
        items.add(createDots("intelligence", r.getString(com.fts.ttbros.R.string.attr_intelligence), data))
        items.add(createDots("wits", r.getString(com.fts.ttbros.R.string.attr_wits), data))
        items.add(createDots("resolve", r.getString(com.fts.ttbros.R.string.attr_resolve), data))

        // Skills
        items.add(FormItem.Section(r.getString(com.fts.ttbros.R.string.section_skills)))
        
        items.add(FormItem.Header(r.getString(com.fts.ttbros.R.string.header_physical)))
        items.add(createDots("athletics", r.getString(com.fts.ttbros.R.string.skill_athletics), data))
        items.add(createDots("brawl", r.getString(com.fts.ttbros.R.string.skill_brawl), data))
        items.add(createDots("craft", r.getString(com.fts.ttbros.R.string.skill_craft), data))
        items.add(createDots("drive", r.getString(com.fts.ttbros.R.string.skill_drive), data))
        items.add(createDots("firearms", r.getString(com.fts.ttbros.R.string.skill_firearms), data))
        items.add(createDots("larceny", r.getString(com.fts.ttbros.R.string.skill_larceny), data))
        items.add(createDots("melee", r.getString(com.fts.ttbros.R.string.skill_melee), data))
        items.add(createDots("stealth", r.getString(com.fts.ttbros.R.string.skill_stealth), data))
        items.add(createDots("survival", r.getString(com.fts.ttbros.R.string.skill_survival), data))

        items.add(FormItem.Header(r.getString(com.fts.ttbros.R.string.header_social)))
        items.add(createDots("animal_ken", r.getString(com.fts.ttbros.R.string.skill_animal_ken), data))
        items.add(createDots("etiquette", r.getString(com.fts.ttbros.R.string.skill_etiquette), data))
        items.add(createDots("insight", r.getString(com.fts.ttbros.R.string.skill_insight), data))
        items.add(createDots("intimidation", r.getString(com.fts.ttbros.R.string.skill_intimidation), data))
        items.add(createDots("leadership", r.getString(com.fts.ttbros.R.string.skill_leadership), data))
        items.add(createDots("performance", r.getString(com.fts.ttbros.R.string.skill_performance), data))
        items.add(createDots("persuasion", r.getString(com.fts.ttbros.R.string.skill_persuasion), data))
        items.add(createDots("streetwise", r.getString(com.fts.ttbros.R.string.skill_streetwise), data))
        items.add(createDots("subterfuge", r.getString(com.fts.ttbros.R.string.skill_subterfuge), data))

        items.add(FormItem.Header(r.getString(com.fts.ttbros.R.string.header_mental)))
        items.add(createDots("academics", r.getString(com.fts.ttbros.R.string.skill_academics), data))
        items.add(createDots("awareness", r.getString(com.fts.ttbros.R.string.skill_awareness), data))
        items.add(createDots("finance", r.getString(com.fts.ttbros.R.string.skill_finance), data))
        items.add(createDots("investigation", r.getString(com.fts.ttbros.R.string.skill_investigation), data))
        items.add(createDots("medicine", r.getString(com.fts.ttbros.R.string.skill_medicine), data))
        items.add(createDots("occult", r.getString(com.fts.ttbros.R.string.skill_occult), data))
        items.add(createDots("politics", r.getString(com.fts.ttbros.R.string.skill_politics), data))
        items.add(createDots("science", r.getString(com.fts.ttbros.R.string.skill_science), data))
        items.add(createDots("technology", r.getString(com.fts.ttbros.R.string.skill_technology), data))

        // Disciplines
        items.add(FormItem.Section(r.getString(com.fts.ttbros.R.string.section_disciplines)))
        
        val disciplines = (data["disciplines"] as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
        disciplines.forEach { disc ->
            val id = disc["id"] as? String ?: ""
            val name = disc["name"] as? String ?: ""
            val value = (disc["value"] as? Number)?.toInt() ?: 0
            items.add(FormItem.Discipline(id, name, value))
        }
        
        items.add(FormItem.Button("add_discipline", r.getString(com.fts.ttbros.R.string.action_add_discipline)))

        items.add(FormItem.Section(r.getString(com.fts.ttbros.R.string.section_other_traits)))
        items.add(FormItem.TextField("advantages", r.getString(com.fts.ttbros.R.string.label_advantages), data["advantages"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("flaws", r.getString(com.fts.ttbros.R.string.label_flaws), data["flaws"] as? String ?: "", isMultiline = true))

        return items
    }

    private fun createDots(key: String, label: String, data: Map<String, Any>): FormItem.DotsField {
        // Handle both Int and Long (Firestore stores numbers as Long)
        val value = when (val v = data[key]) {
            is Int -> v
            is Long -> v.toInt()
            else -> 0 // Default to 0 (or 1 for attributes usually, but 0 is safe)
        }
        return FormItem.DotsField(key, label, value)
    }
}
