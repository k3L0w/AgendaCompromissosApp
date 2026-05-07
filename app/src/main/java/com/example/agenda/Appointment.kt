package com.example.agenda

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appointments")
data class Appointment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val category: String = "Outro",
    val appointmentDateTimeMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)
