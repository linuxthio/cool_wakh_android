package com.wakh.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un groupe est un concept PUREMENT local : une simple liste de contacts
 * existants réunis sous un nom, stockée uniquement dans Room. Le serveur
 * n'en a aucune connaissance — voir MessageRepository pour le principe de
 * "fan-out" (chaque message de groupe est envoyé individuellement à
 * chaque membre, exactement comme un message 1-à-1).
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)
