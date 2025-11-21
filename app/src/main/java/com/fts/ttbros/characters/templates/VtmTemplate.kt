package com.fts.ttbros.characters.templates

import com.fts.ttbros.characters.form.FormItem

object VtmTemplate {
    fun generate(data: Map<String, Any>): List<FormItem> {
        val items = mutableListOf<FormItem>()

        // Header
        items.add(FormItem.Header("Vampire: The Masquerade 5e"))

        // Basic Info
        items.add(FormItem.Section("Info"))
        items.add(FormItem.TextField("name", "Name", data["name"] as? String ?: ""))
        items.add(FormItem.TextField("concept", "Concept", data["concept"] as? String ?: ""))
        items.add(FormItem.TextField("chronicle", "Chronicle", data["chronicle"] as? String ?: ""))
        items.add(FormItem.TextField("ambition", "Ambition", data["ambition"] as? String ?: ""))
        items.add(FormItem.TextField("desire", "Desire", data["desire"] as? String ?: ""))
        items.add(FormItem.TextField("predator", "Predator", data["predator"] as? String ?: ""))
        items.add(FormItem.TextField("clan", "Clan", data["clan"] as? String ?: ""))
        items.add(FormItem.TextField("generation", "Generation", data["generation"] as? String ?: ""))
        items.add(FormItem.TextField("sire", "Sire", data["sire"] as? String ?: ""))

        // Attributes
        items.add(FormItem.Section("Attributes"))
        
        items.add(FormItem.Header("Physical"))
        items.add(createDots("strength", "Strength", data))
        items.add(createDots("dexterity", "Dexterity", data))
        items.add(createDots("stamina", "Stamina", data))

        items.add(FormItem.Header("Social"))
        items.add(createDots("charisma", "Charisma", data))
        items.add(createDots("manipulation", "Manipulation", data))
        items.add(createDots("composure", "Composure", data))

        items.add(FormItem.Header("Mental"))
        items.add(createDots("intelligence", "Intelligence", data))
        items.add(createDots("wits", "Wits", data))
        items.add(createDots("resolve", "Resolve", data))

        // Skills
        items.add(FormItem.Section("Skills"))
        
        items.add(FormItem.Header("Physical"))
        items.add(createDots("athletics", "Athletics", data))
        items.add(createDots("brawl", "Brawl", data))
        items.add(createDots("craft", "Craft", data))
        items.add(createDots("drive", "Drive", data))
        items.add(createDots("firearms", "Firearms", data))
        items.add(createDots("larceny", "Larceny", data))
        items.add(createDots("melee", "Melee", data))
        items.add(createDots("stealth", "Stealth", data))
        items.add(createDots("survival", "Survival", data))

        items.add(FormItem.Header("Social"))
        items.add(createDots("animal_ken", "Animal Ken", data))
        items.add(createDots("etiquette", "Etiquette", data))
        items.add(createDots("insight", "Insight", data))
        items.add(createDots("intimidation", "Intimidation", data))
        items.add(createDots("leadership", "Leadership", data))
        items.add(createDots("performance", "Performance", data))
        items.add(createDots("persuasion", "Persuasion", data))
        items.add(createDots("streetwise", "Streetwise", data))
        items.add(createDots("subterfuge", "Subterfuge", data))

        items.add(FormItem.Header("Mental"))
        items.add(createDots("academics", "Academics", data))
        items.add(createDots("awareness", "Awareness", data))
        items.add(createDots("finance", "Finance", data))
        items.add(createDots("investigation", "Investigation", data))
        items.add(createDots("medicine", "Medicine", data))
        items.add(createDots("occult", "Occult", data))
        items.add(createDots("politics", "Politics", data))
        items.add(createDots("science", "Science", data))
        items.add(createDots("technology", "Technology", data))

        // Other Traits
        items.add(FormItem.Section("Other Traits"))
        items.add(FormItem.TextField("disciplines", "Disciplines", data["disciplines"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("advantages", "Advantages", data["advantages"] as? String ?: "", isMultiline = true))
        items.add(FormItem.TextField("flaws", "Flaws", data["flaws"] as? String ?: "", isMultiline = true))

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
