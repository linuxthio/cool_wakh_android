package com.wakh.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Nombre de membres par groupe — voir [GroupDao.observeAllMemberCounts]. */
data class GroupMemberCount(
    val groupId: String,
    val memberCount: Int,
)

@Dao
interface GroupDao {

    @Query("SELECT * FROM groups ORDER BY name COLLATE NOCASE ASC")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :groupId LIMIT 1")
    suspend fun getGroup(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE id = :groupId LIMIT 1")
    fun observeGroup(groupId: String): Flow<GroupEntity?>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getMembers(groupId: String): List<GroupMemberEntity>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId")
    fun observeMemberCount(groupId: String): Flow<Int>

    /** Nombre de membres par groupe, en une seule requête — pour la liste des conversations. */
    @Query("SELECT groupId, COUNT(*) AS memberCount FROM group_members GROUP BY groupId")
    fun observeAllMemberCounts(): Flow<List<GroupMemberCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Query("UPDATE groups SET name = :name WHERE id = :groupId")
    suspend fun renameGroup(groupId: String, name: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND phoneNumber = :phoneNumber")
    suspend fun deleteMember(groupId: String, phoneNumber: String)
}
