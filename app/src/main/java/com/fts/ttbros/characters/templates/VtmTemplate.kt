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
        items.add(FormItem.TextField("player", r.getString(com.fts.ttbros.R.string.label_player), data["player"] as? String ?: ""))
        items.add(FormItem.TextField("chronicle", r.getString(com.fts.ttbros.R.string.label_chronicle), data["chronicle"] as? String ?: ""))
        items.add(FormItem.TextField("chronicle_tenets", r.getString(com.fts.ttbros.R.string.label_chronicle_tenets), data["chronicle_tenets"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("concept", r.getString(com.fts.ttbros.R.string.label_concept), data["concept"] as? String ?: ""))
        items.add(FormItem.TextField("predator", r.getString(com.fts.ttbros.R.string.label_predator), data["predator"] as? String ?: ""))
        items.add(FormItem.TextField("clan", r.getString(com.fts.ttbros.R.string.label_clan), data["clan"] as? String ?: ""))
        items.add(FormItem.TextField("generation", r.getString(com.fts.ttbros.R.string.label_generation), data["generation"] as? String ?: ""))
        items.add(FormItem.TextField("sire", r.getString(com.fts.ttbros.R.string.label_sire), data["sire"] as? String ?: ""))
        
        // Ambition & Desire
        items.add(FormItem.Section(r.getString(com.fts.ttbros.R.string.section_goals)))
        items.add(FormItem.TextField("ambition", r.getString(com.fts.ttbros.R.string.label_ambition), data["ambition"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("desire", r.getString(com.fts.ttbros.R.string.label_desire), data["desire"] as? String ?: "", isMultiline = true))
        
        // Convictions & Touchstones
        items.add(FormItem.Section(r.getString(com.fts.ttbros.R.string.section_convictions)))
        items.add(FormItem.TextField("convictions", r.getString(com.fts.ttbros.R.string.label_convictions), data["convictions"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("touchstones", r.getString(com.fts.ttbros.R.string.label_touchstones), data["touchstones"] as? String ?: "", isMultiline = true))
        
        // Identity
        items.add(FormItem.Section(r.getString(com.fts.ttbros.R.string.section_identity)))
        items.add(FormItem.TextField("mortal_identity", r.getString(com.fts.ttbros.R.string.label_mortal_identity), data["mortal_identity"] as? String ?: ""))
        items.add(FormItem.TextField("mask", r.getString(com.fts.ttbros.R.string.label_mask), data["mask"] as? String ?: ""))
        
        // Coterie & Domain
        items.add(FormItem.Section(r.getString(com.fts.ttbros.R.string.section_coterie)))
        items.add(FormItem.TextField("coterie", r.getString(com.fts.ttbros.R.string.label_coterie), data["coterie"] as? String ?: ""))
        items.add(FormItem.TextField("haven", r.getString(com.fts.ttbros.R.string.label_haven), data["haven"] as? String ?: ""))
        items.add(FormItem.TextField("domain", r.getString(com.fts.ttbros.R.string.label_domain), data["domain"] as? String ?: ""))

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

        // Trackers
        items.add(FormItem.Section(r.getString(com.fts.ttbros.R.string.section_trackers)))
        items.add(FormItem.TextField("health_current", r.getString(com.fts.ttbros.R.string.label_health_current), data["health_current"] as? String ?: data["Health Levels Filled"]?.toString() ?: ""))
        items.add(FormItem.TextField("health_max", r.getString(com.fts.ttbros.R.string.label_health_max), data["health_max"] as? String ?: data["Health Max"]?.toString() ?: ""))
        items.add(FormItem.TextField("willpower_current", r.getString(com.fts.ttbros.R.string.label_willpower_current), data["willpower_current"] as? String ?: data["Willpower Levels Filled"]?.toString() ?: ""))
        items.add(FormItem.TextField("willpower_max", r.getString(com.fts.ttbros.R.string.label_willpower_max), data["willpower_max"] as? String ?: data["Willpower Max"]?.toString() ?: ""))
        items.add(FormItem.TextField("hunger", r.getString(com.fts.ttbros.R.string.label_hunger), data["hunger"] as? String ?: data["Hunger Levels Filled"]?.toString() ?: ""))
        items.add(FormItem.TextField("humanity", r.getString(com.fts.ttbros.R.string.label_humanity), data["humanity"] as? String ?: data["Humanity Levels Filled"]?.toString() ?: ""))
        
        // Blood & Resonance
        items.add(FormItem.Section(r.getString(com.fts.ttbros.R.string.section_blood)))
        items.add(FormItem.TextField("blood_potency", r.getString(com.fts.ttbros.R.string.label_blood_potency), data["blood_potency"] as? String ?: ""))
        items.add(FormItem.TextField("blood_resonance", r.getString(com.fts.ttbros.R.string.label_blood_resonance), data["blood_resonance"] as? String ?: ""))
        items.add(FormItem.TextField("blood_surge", r.getString(com.fts.ttbros.R.string.label_blood_surge), data["blood_surge"] as? String ?: ""))
        items.add(FormItem.TextField("mend_amount", r.getString(com.fts.ttbros.R.string.label_mend_amount), data["mend_amount"] as? String ?: ""))
        items.add(FormItem.TextField("power_bonus", r.getString(com.fts.ttbros.R.string.label_power_bonus), data["power_bonus"] as? String ?: ""))
        items.add(FormItem.TextField("rouse_re_roll", r.getString(com.fts.ttbros.R.string.label_rouse_reroll), data["rouse_re_roll"] as? String ?: ""))
        items.add(FormItem.TextField("feeding_penalty", r.getString(com.fts.ttbros.R.string.label_feeding_penalty), data["feeding_penalty"] as? String ?: ""))
        
        // Experience
        items.add(FormItem.Section(r.getString(com.fts.ttbros.R.string.section_experience)))
        items.add(FormItem.TextField("experience_total", r.getString(com.fts.ttbros.R.string.label_experience_total), data["experience_total"] as? String ?: ""))
        items.add(FormItem.TextField("experience_spent", r.getString(com.fts.ttbros.R.string.label_experience_spent), data["experience_spent"] as? String ?: ""))
        items.add(FormItem.TextField("experience_available", r.getString(com.fts.ttbros.R.string.label_experience_available), data["experience_available"] as? String ?: ""))
        
        // Other Traits
        items.add(FormItem.Section(r.getString(com.fts.ttbros.R.string.section_other_traits)))
        items.add(FormItem.TextField("advantages", r.getString(com.fts.ttbros.R.string.label_advantages), data["advantages"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("flaws", r.getString(com.fts.ttbros.R.string.label_flaws), data["flaws"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("merits", r.getString(com.fts.ttbros.R.string.label_merits), data["merits"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("notes", r.getString(com.fts.ttbros.R.string.label_notes), data["notes"] as? String ?: "", isMultiline = true))

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
