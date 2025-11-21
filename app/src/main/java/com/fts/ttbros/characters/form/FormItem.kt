package com.fts.ttbros.characters.form

sealed class FormItem {
    data class Header(val title: String) : FormItem()
    data class Section(val title: String) : FormItem()
    
    data class TextField(
        val key: String,
        val label: String,
        val value: String = "",
        val hint: String = "",
        val isMultiline: Boolean = false
    ) : FormItem()

    data class DotsField(
        val key: String,
        val label: String,
        val value: Int = 0,
        val max: Int = 5
    ) : FormItem()

    data class CheckboxField(
        val key: String,
        val label: String,
        val checked: Boolean = false
    ) : FormItem()
}
