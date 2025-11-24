package com.fts.ttbros.data.model

import com.google.firebase.Timestamp

data class Document(
    val id: String = "",
    val teamId: String = "",
    val title: String = "",
    val fileName: String = "",
    val downloadUrl: String = "",
    val uploadedBy: String = "",
    val uploadedByName: String = "",
    val timestamp: Timestamp? = null,
    val sizeBytes: Long = 0
)
