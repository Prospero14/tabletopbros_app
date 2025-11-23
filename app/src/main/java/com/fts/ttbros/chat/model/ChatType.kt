package com.fts.ttbros.chat.model

enum class ChatType(val key: String) {
    TEAM("team"),
    ANNOUNCEMENTS("announcements"),
    MASTER_PLAYER("master_player"); // Chat between Master and Player

    companion object {
        fun from(value: String?): ChatType = values().firstOrNull { it.key == value } ?: TEAM
    }
}

