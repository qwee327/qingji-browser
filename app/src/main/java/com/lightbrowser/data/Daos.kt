package com.lightbrowser.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: Bookmark): Long

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun isBookmarked(url: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    suspend fun isBookmarkedOnce(url: String): Boolean

    @Query("SELECT * FROM bookmarks WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY createdAt DESC LIMIT 8")
    suspend fun search(query: String): List<Bookmark>
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 500")
    fun observeAll(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem): Long

    @Delete
    suspend fun delete(item: HistoryItem)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC LIMIT 8")
    suspend fun search(query: String): List<HistoryItem>

    @Query("SELECT 0 AS id, url, MAX(title) AS title, MAX(visitedAt) AS visitedAt FROM history GROUP BY url ORDER BY MAX(visitedAt) DESC LIMIT 16")
    fun topSites(): Flow<List<HistoryItem>>
}

@Dao
interface TabDao {
    @Query("SELECT * FROM open_tabs ORDER BY position ASC")
    suspend fun getAll(): List<TabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tabs: List<TabEntity>)

    @Query("DELETE FROM open_tabs")
    suspend fun clear()
}

@Dao
interface SiteRuleDao {
    @Query("SELECT * FROM site_block_rules ORDER BY host ASC")
    fun observeAll(): Flow<List<SiteBlockRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: SiteBlockRule)

    @Query("DELETE FROM site_block_rules WHERE host = :host")
    suspend fun deleteByHost(host: String)
}

@Dao
interface CustomRuleDao {
    @Query("SELECT * FROM custom_block_rules ORDER BY domain ASC")
    fun observeAll(): Flow<List<CustomBlockRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CustomBlockRule)

    @Delete
    suspend fun delete(rule: CustomBlockRule)
}

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extensions ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<BrowserExtension>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(extension: BrowserExtension): Long

    @Delete
    suspend fun delete(extension: BrowserExtension)
}
