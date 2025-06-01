package com.example.mnntest.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.sql.Timestamp

@Entity(
    tableName = "chat_message",
    foreignKeys = [ForeignKey(
        entity = ChatThread::class,
        parentColumns = ["id"],
        childColumns = ["thread_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "thread_id", index = true)
    val threadId: Int,

    val sender: String, // "user" or "llm"

    val message: String?,

    @ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
    val timestamp: Timestamp
) 