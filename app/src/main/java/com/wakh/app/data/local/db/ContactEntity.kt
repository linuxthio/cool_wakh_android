package com.wakh.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un contact ajouté simplement en entrant son numéro de téléphone.
 * Stocké uniquement en local (Room) — aucune synchronisation cloud.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val phoneNumber: String,
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis(),
)
