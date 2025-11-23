package com.fts.ttbros.data.model

data class Event(
    val id: String = "",
    val teamId: String = "",
    val title: String = "",
    val description: String = "",
    val dateTime: Long = 0, // Timestamp in milliseconds
    val createdBy: String = "", // User ID
    val createdByName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val notificationsSent: NotificationStatus = NotificationStatus()
)

data class NotificationStatus(
    val onCreate: Boolean = false,
    val oneDayBefore: Boolean = false,
    val twoHoursBefore: Boolean = false
)
