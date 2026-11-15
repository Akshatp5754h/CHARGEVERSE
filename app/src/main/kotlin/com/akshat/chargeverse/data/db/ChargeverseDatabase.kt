package com.akshat.chargeverse.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MediaItemEntity::class,
        AnimationProfileEntity::class,
        SoundItemEntity::class,
        SoundRoleEntity::class,
        AutomationRuleEntity::class,
        AiHistoryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ChargeverseDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun animationProfileDao(): AnimationProfileDao
    abstract fun soundItemDao(): SoundItemDao
    abstract fun automationRuleDao(): AutomationRuleDao
    abstract fun aiHistoryDao(): AiHistoryDao

    companion object {
        const val NAME = "chargeverse.db"

        /**
         * v1 shipped to users, so 1→2 must preserve every row:
         *  - animation_profiles rebuilt with a real FK to media_items
         *    (cascade-deletes profiles when their media is removed).
         *  - sound_items rebuilt without the single `role` column; each
         *    existing role is moved into the new sound_roles table.
         *  - automation_rules gains `armed` (default true) for transition
         *    based re-armable rules.
         * The DDL mirrors Room's generated schema (app/schemas/…/2.json)
         * so migration validation passes.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v2 keeps foreign keys ON from now on; sweep v1 orphans first
                // so legacy data can't violate the new constraint.
                db.execSQL(
                    "DELETE FROM animation_profiles WHERE mediaItemId NOT IN (SELECT id FROM media_items)"
                )

                // (a) animation_profiles: rebuild with FK(mediaItemId) ON DELETE CASCADE.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `animation_profiles_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `mediaItemId` INTEGER NOT NULL,
                        `scale` REAL NOT NULL,
                        `offsetX` REAL NOT NULL,
                        `offsetY` REAL NOT NULL,
                        `playbackSpeed` REAL NOT NULL,
                        `backgroundHex` INTEGER NOT NULL,
                        `textOverlay` TEXT NOT NULL,
                        `showBatteryOverlay` INTEGER NOT NULL,
                        `showPercent` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        FOREIGN KEY(`mediaItemId`) REFERENCES `media_items`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `animation_profiles_new` (
                        `id`, `name`, `mediaItemId`, `scale`, `offsetX`, `offsetY`,
                        `playbackSpeed`, `backgroundHex`, `textOverlay`,
                        `showBatteryOverlay`, `showPercent`, `isActive`,
                        `createdAtMs`, `updatedAtMs`
                    ) SELECT
                        `id`, `name`, `mediaItemId`, `scale`, `offsetX`, `offsetY`,
                        `playbackSpeed`, `backgroundHex`, `textOverlay`,
                        `showBatteryOverlay`, `showPercent`, `isActive`,
                        `createdAtMs`, `updatedAtMs`
                    FROM `animation_profiles`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `animation_profiles`")
                db.execSQL("ALTER TABLE `animation_profiles_new` RENAME TO `animation_profiles`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_animation_profiles_isActive` ON `animation_profiles` (`isActive`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_animation_profiles_mediaItemId` ON `animation_profiles` (`mediaItemId`)"
                )

                // (b) sound_roles first: each v1 sound's `role` moves here.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sound_roles` (
                        `soundItemId` INTEGER NOT NULL,
                        `role` TEXT NOT NULL,
                        PRIMARY KEY(`soundItemId`, `role`),
                        FOREIGN KEY(`soundItemId`) REFERENCES `sound_items`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `sound_roles` (`soundItemId`, `role`) SELECT `id`, `role` FROM `sound_items`"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sound_roles_soundItemId` ON `sound_roles` (`soundItemId`)"
                )

                // sound_items: rebuild without the `role` column, preserving rows.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sound_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `uri` TEXT NOT NULL,
                        `isBuiltIn` INTEGER NOT NULL,
                        `addedAtMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `sound_items_new` (`id`, `name`, `uri`, `isBuiltIn`, `addedAtMs`)
                    SELECT `id`, `name`, `uri`, `isBuiltIn`, `addedAtMs` FROM `sound_items`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `sound_items`")
                db.execSQL("ALTER TABLE `sound_items_new` RENAME TO `sound_items`")

                // (c) automation_rules: `armed` column for transition rules.
                db.execSQL(
                    "ALTER TABLE `automation_rules` ADD COLUMN `armed` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * v2→v3 (S4): animation_profiles gains `fillMode` — true = FILL/cover
         * (the v1 ZOOM behaviour), false = FIT/contain. Plain additive column
         * with a default, so every existing row keeps the v1 look and no
         * table rebuild is needed.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `animation_profiles` ADD COLUMN `fillMode` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        fun build(context: Context): ChargeverseDatabase =
            Room.databaseBuilder(context, ChargeverseDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : RoomDatabase.Callback() {
                    /** FKs (CASCADE deletes) only hold while the pragma is on. */
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .build()
    }
}
