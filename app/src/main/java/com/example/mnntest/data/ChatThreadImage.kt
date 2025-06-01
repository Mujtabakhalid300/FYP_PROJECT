package com.example.mnntest.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.sql.Timestamp

@Entity(
    tableName = "chat_thread_image",
    foreignKeys = [ForeignKey(
        entity = ChatThread::class,
        parentColumns = ["id"],
        childColumns = ["thread_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ChatThreadImage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "thread_id", index = true)
    val threadId: Int,

    @ColumnInfo(name = "image_path")
    val imagePath: String?,

    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    val createdAt: Timestamp
) 