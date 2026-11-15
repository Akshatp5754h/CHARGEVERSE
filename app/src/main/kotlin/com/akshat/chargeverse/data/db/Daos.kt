package com.akshat.chargeverse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items ORDER BY addedAtMs DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE category = :category ORDER BY addedAtMs DESC")
    fun observeByCategory(category: AnimationCategory): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun byId(id: Long): MediaItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaItemEntity): Long

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun count(): Int
}

@Dao
interface AnimationProfileDao {
    @Query("SELECT * FROM animation_profiles ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<AnimationProfileEntity>>

    @Query("SELECT * FROM animation_profiles WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<AnimationProfileEntity?>

    @Query("SELECT * FROM animation_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun active(): AnimationProfileEntity?

    @Query("SELECT * FROM animation_profiles WHERE id = :id")
    suspend fun byId(id: Long): AnimationProfileEntity?

    @Query("SELECT * FROM animation_profiles ORDER BY updatedAtMs DESC")
    suspend fun all(): List<AnimationProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: AnimationProfileEntity): Long

    @Update
    suspend fun update(profile: AnimationProfileEntity)

    @Query("DELETE FROM animation_profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE animation_profiles SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE animation_profiles SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)
}

@Dao
interface SoundItemDao {
    @Query("SELECT * FROM sound_items ORDER BY addedAtMs DESC")
    fun observeAll(): Flow<List<SoundItemEntity>>

    /** Sounds serving a role, via the sound_roles join table. */
    @Query(
        "SELECT si.* FROM sound_items si JOIN sound_roles sr ON sr.soundItemId = si.id " +
            "WHERE sr.role = :role ORDER BY si.addedAtMs DESC"
    )
    fun observeByRole(role: SoundRole): Flow<List<SoundItemEntity>>

    @Query(
        "SELECT si.* FROM sound_items si JOIN sound_roles sr ON sr.soundItemId = si.id " +
            "WHERE sr.role = :role ORDER BY si.addedAtMs DESC"
    )
    suspend fun byRole(role: SoundRole): List<SoundItemEntity>

    /** Every role row, for building per-sound role lists in the UI. */
    @Query("SELECT * FROM sound_roles")
    fun observeAllRoles(): Flow<List<SoundRoleEntity>>

    @Query("SELECT role FROM sound_roles WHERE soundItemId = :soundId")
    suspend fun rolesFor(soundId: Long): List<SoundRole>

    @Query("SELECT * FROM sound_roles")
    suspend fun allRoleRows(): List<SoundRoleEntity>

    @Query("SELECT * FROM sound_items WHERE id = :id")
    suspend fun byId(id: Long): SoundItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SoundItemEntity): Long

    /** Assigns (or replaces) one role for a sound. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun assignRole(assignment: SoundRoleEntity)

    suspend fun assignRole(soundId: Long, role: SoundRole) =
        assignRole(SoundRoleEntity(soundItemId = soundId, role = role))

    @Query("DELETE FROM sound_roles WHERE soundItemId = :soundId AND role = :role")
    suspend fun clearRole(soundId: Long, role: SoundRole)

    @Query("DELETE FROM sound_roles WHERE soundItemId = :soundId")
    suspend fun clearRoles(soundId: Long)

    @Query("DELETE FROM sound_items WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AutomationRuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules WHERE enabled = 1")
    suspend fun enabledRules(): List<AutomationRuleEntity>

    @Query("SELECT * FROM automation_rules WHERE id = :id")
    suspend fun byId(id: Long): AutomationRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AutomationRuleEntity): Long

    @Update
    suspend fun update(rule: AutomationRuleEntity)

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AiHistoryDao {
    @Query("SELECT * FROM ai_history ORDER BY createdAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<AiHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AiHistoryEntity): Long

    @Query("UPDATE ai_history SET status = :status, outputUri = :outputUri, errorMessage = :error WHERE id = :id")
    suspend fun updateResult(id: Long, status: String, outputUri: String?, error: String?)

    @Query("DELETE FROM ai_history WHERE id = :id")
    suspend fun delete(id: Long)
}
