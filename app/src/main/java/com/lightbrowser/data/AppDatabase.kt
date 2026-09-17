package com.lightbrowser.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Bookmark::class, HistoryItem::class, TabEntity::class,
        SiteBlockRule::class, CustomBlockRule::class, BrowserExtension::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun tabDao(): TabDao
    abstract fun siteRuleDao(): SiteRuleDao
    abstract fun customRuleDao(): CustomRuleDao
    abstract fun extensionDao(): ExtensionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 → v2：新增站点拦截例外表与自定义拦截规则表 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `site_block_rules` (" +
                        "`host` TEXT NOT NULL, `level` INTEGER NOT NULL, PRIMARY KEY(`host`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_block_rules` (" +
                        "`domain` TEXT NOT NULL, `isTracker` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, PRIMARY KEY(`domain`))"
                )
            }
        }

        /** v2 → v3：新增扩展脚本表 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `extensions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `code` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lightbrowser.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
        }
    }
}
