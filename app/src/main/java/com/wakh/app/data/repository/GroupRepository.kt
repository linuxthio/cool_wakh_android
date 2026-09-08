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

    fun observeGroup(groupId: String): Flow<GroupEntity?> = groupDao.observeGroup(groupId)

    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>> = groupDao.observeMembers(groupId)

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

    /** Renomme un groupe existant — purement local, comme le reste du concept de groupe. */
    suspend fun renameGroup(groupId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        groupDao.renameGroup(groupId, trimmed)
    }

    /** Ajoute un ou plusieurs membres à un groupe existant, à partir de contacts déjà enregistrés. */
    suspend fun addMembers(groupId: String, memberPhoneNumbers: List<String>) {
        groupDao.insertMembers(memberPhoneNumbers.distinct().map { GroupMemberEntity(groupId, it) })
    }

    /** Retire un membre d'un groupe existant — l'historique des messages du groupe n'est pas affecté. */
    suspend fun removeMember(groupId: String, phoneNumber: String) {
        groupDao.deleteMember(groupId, phoneNumber)
    }
}
