package com.lightbrowser.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lightbrowser.BrowserApplication
import com.lightbrowser.browser.SearchEngines
import com.lightbrowser.browser.SuggestionFetcher
import com.lightbrowser.data.Bookmark
import com.lightbrowser.data.HistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SuggestionType { SEARCH, HISTORY, BOOKMARK }

data class Suggestion(val text: String, val url: String?, val type: SuggestionType)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BrowserApplication
    private val db = app.database
    private val settingsRepo = app.settingsRepository

    val bookmarks: StateFlow<List<Bookmark>> = db.bookmarkDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryItem>> = db.historyDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topSites: StateFlow<List<HistoryItem>> = db.historyDao().topSites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun isBookmarked(url: String): Flow<Boolean> =
        if (url.isBlank()) flowOf(false) else db.bookmarkDao().isBookmarked(url)

    fun toggleBookmark(title: String, url: String) {
        if (url.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (db.bookmarkDao().isBookmarkedOnce(url)) {
                db.bookmarkDao().deleteByUrl(url)
            } else {
                db.bookmarkDao().upsert(Bookmark(title = title.ifBlank { url }, url = url))
            }
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch(Dispatchers.IO) { db.bookmarkDao().delete(bookmark) }
    }

    fun deleteHistory(item: HistoryItem) {
        viewModelScope.launch(Dispatchers.IO) { db.historyDao().delete(item) }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) { db.historyDao().clear() }
    }

    // ---------- 地址栏联想 ----------

    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()
    private var suggestJob: Job? = null

    @OptIn(FlowPreview::class)
    fun querySuggestions(raw: String) {
        val query = raw.trim()
        suggestJob?.cancel()
        if (query.isEmpty()) {
            _suggestions.value = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(120)
            val local = withContext(Dispatchers.IO) {
                (db.historyDao().search(query).map {
                    Suggestion(it.title.ifBlank { it.url }, it.url, SuggestionType.HISTORY)
                } + db.bookmarkDao().search(query).map {
                    Suggestion(it.title.ifBlank { it.url }, it.url, SuggestionType.BOOKMARK)
                // 按展示文本去重：历史里可能有多条同标题/同网址记录，
                // 重复项会导致建议列表 LazyColumn key 冲突闪退
                }).distinctBy { it.text }.take(5)
            }
            _suggestions.value = local
            val engine = SearchEngines.byId(settingsRepo.settings.first().searchEngineId)
            val remote = SuggestionFetcher.fetch(engine, query)
                .map { Suggestion(it, null, SuggestionType.SEARCH) }
            _suggestions.value = (remote + local).distinctBy { it.text }.take(8)
        }
    }

    fun clearSuggestions() {
        suggestJob?.cancel()
        _suggestions.value = emptyList()
    }
}
