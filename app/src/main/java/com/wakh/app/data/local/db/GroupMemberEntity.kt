package com.wakh.app.data.local.db

import androidx.room.Entity

/** Un membre (numéro de téléphone) d'un groupe — voir GroupEntity. */
@Entity(tableName = "group_members", primaryKeys = ["groupId", "phoneNumber"])
data class GroupMemberEntity(
    val groupId: String,
    val phoneNumber: String,
)
