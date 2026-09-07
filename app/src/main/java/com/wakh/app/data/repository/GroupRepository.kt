package com.wakh.app.data.repository

import com.wakh.app.data.local.db.GroupDao
import com.wakh.app.data.local.db.GroupEntity
import com.wakh.app.data.local.db.GroupMemberCount
import com.wakh.app.data.local.db.GroupMemberEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Un groupe est purement local (Room) : juste un nom et une liste de
 * contacts existants. Le serveur n'a aucune notion de "groupe" — voir
 * MessageRepository pour le principe d'envoi (fan-out vers chaque membre,
 * individuellement, selon exactement le même principe que le 1-à-1).
 */
class GroupRepository(
    private val groupDao: GroupDao,
) {
    fun observeGroups(): Flow<List<GroupEntity>> = groupDao.observeGroups()

    fun observeAllMemberCounts(): Flow<List<GroupMemberCount>> = groupDao.observeAllMemberCounts()

    fun observeMemberCount(groupId: String): Flow<Int> = groupDao.observeMemberCount(groupId)

    suspend fun getGroup(groupId: String): GroupEntity? = groupDao.getGroup(groupId)

    suspend fun getMemberPhoneNumbers(groupId: String): List<String> =
        groupDao.getMembers(groupId).map { it.phoneNumber }

    /** Crée le groupe et ses membres, renvoie l'identifiant généré. */
    suspend fun createGroup(name: String, memberPhoneNumbers: List<String>): String {
        val groupId = UUID.randomUUID().toString()
        groupDao.insertGroup(GroupEntity(id = groupId, name = name.trim()))
        groupDao.insertMembers(memberPhoneNumbers.distinct().map { GroupMemberEntity(groupId, it) })
        return groupId
    }
}
