package com.aichat.persona.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE personaId = :personaId ORDER BY timestamp ASC")
    fun getMessagesForPersona(personaId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE personaId = :personaId ORDER BY timestamp ASC")
    suspend fun getMessagesForPersonaOnce(personaId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE personaId = :personaId")
    suspend fun deleteMessagesForPersona(personaId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    // 只保留最近 N 条，防止无限增长
    @Query("""
        DELETE FROM messages WHERE personaId = :personaId AND id NOT IN (
            SELECT id FROM messages WHERE personaId = :personaId 
            ORDER BY timestamp DESC LIMIT :keepCount
        )
    """)
    suspend fun trimMessages(personaId: String, keepCount: Int = 200)
}
