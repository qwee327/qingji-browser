package com.lightbrowser.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "open_tabs")
data class TabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val position: Int
)

/** 站点级拦截例外：为特定站点单独设置拦截等级 */
@Entity(tableName = "site_block_rules")
data class SiteBlockRule(
    @PrimaryKey val host: String,
    val level: Int
)

/** 用户自定义拦截规则（域名后缀匹配） */
@Entity(tableName = "custom_block_rules")
data class CustomBlockRule(
    @PrimaryKey val domain: String,
    val isTracker: Boolean,
    val enabled: Boolean = true
)
