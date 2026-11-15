package com.akshat.chargeverse.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaItemEntity::class,
        AnimationProfileEntity::class,
        SoundItemEntity::class,
        AutomationRuleEntity::class,
        AiHistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ChargeverseDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun animationProfileDao(): AnimationProfileDao
    abstract fun soundItemDao(): SoundItemDao
    abstract fun automationRuleDao(): AutomationRuleDao
    abstract fun aiHistoryDao(): AiHistoryDao

    companion object {
        const val NAME = "chargeverse.db"

        fun build(context: Context): ChargeverseDatabase =
            Room.databaseBuilder(context, ChargeverseDatabase::class.java, NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
