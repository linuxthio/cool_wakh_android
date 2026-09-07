package com.wakh.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Nombre de messages non lus pour un contact — voir [MessageDao.observeUnreadCounts]. */
data class UnreadCount(
    val contactPhoneNumber: String,
    val unreadCount: Int,
)

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE contactPhoneNumber = :phoneNumber ORDER BY timestamp ASC")
    fun observeForContact(phoneNumber: String): Flow<List<MessageEntity>>

    /** Dernier message de chaque conversation, pour la liste des conversations. */
    @Query(
        """
        SELECT m.* FROM messages m
        INNER JOIN (
            SELECT contactPhoneNumber, MAX(timestamp) AS maxTimestamp
            FROM messages
            GROUP BY contactPhoneNumber
        ) latest
        ON m.contactPhoneNumber = latest.contactPhoneNumber AND m.timestamp = latest.maxTimestamp
        """
    )
    fun observeLastMessagePerContact(): Flow<List<MessageEntity>>

    /** Nombre de messages reçus non lus, par contact — pour le badge dans la liste des conversations. */
    @Query(
        """
        SELECT contactPhoneNumber, COUNT(*) AS unreadCount
        FROM messages
        WHERE direction = 'RECEIVED' AND isRead = 0
        GROUP BY contactPhoneNumber
        """
    )
    fun observeUnreadCounts(): Flow<List<UnreadCount>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: MessageStatus)

    @Query("UPDATE messages SET status = :status, queuedRemoteId = :queuedRemoteId WHERE id = :id")
    suspend fun updateStatusWithQueuedId(id: String, status: MessageStatus, queuedRemoteId: String?)

    /** Marque comme lus tous les messages reçus non lus de cette conversation (voir MessageRepository.markConversationAsRead). */
    @Query("UPDATE messages SET isRead = 1 WHERE contactPhoneNumber = :phoneNumber AND direction = 'RECEIVED' AND isRead = 0")
    suspend fun markConversationRead(phoneNumber: String)

    /** Suppression locale uniquement — voir MessageRepository.deleteMessage pour la suppression du fichier associé. */
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)
}
