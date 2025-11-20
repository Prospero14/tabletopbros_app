package com.fts.ttbros.data.model

enum class UserRole {
    MASTER,
    PLAYER;

    companion object {
        fun from(value: String?): UserRole =
            when (value?.uppercase()) {
                MASTER.name -> MASTER
                else -> PLAYER
            }
    }
}

