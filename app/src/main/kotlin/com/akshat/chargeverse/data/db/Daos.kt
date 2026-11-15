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

    @Query("SELECT * FROM sound_items WHERE role = :role ORDER BY addedAtMs DESC")
    fun observeByRole(role: SoundRole): Flow<List<SoundItemEntity>>

    @Query("SELECT * FROM sound_items WHERE id = :id")
    suspend fun byId(id: Long): SoundItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SoundItemEntity): Long

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
