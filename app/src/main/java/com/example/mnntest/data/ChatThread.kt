package com.example.mnntest.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.sql.Timestamp

@Entity(tableName = "chat_thread")
data class ChatThread(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String?,

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String?,

    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    val createdAt: Timestamp,

    @ColumnInfo(name = "updated_at", defaultValue = "CURRENT_TIMESTAMP")
    val updatedAt: Timestamp
) 