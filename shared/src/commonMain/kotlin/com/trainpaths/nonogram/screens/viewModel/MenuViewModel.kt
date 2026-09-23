package com.trainpaths.nonogram.screens.viewModel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.trainpaths.nonogram.AppSDK
import com.trainpaths.nonogram.auth.AuthRepository
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.filter.FilterEntry
import com.trainpaths.nonogram.filter.FilterSortState
import com.trainpaths.nonogram.filter.NonogramFilters
import com.trainpaths.nonogram.settings.SettingsRepository

class MenuViewModel(
    private val sdk: AppSDK,
    private val authRepository: AuthRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val showNames = settingsRepository.showNames

    var nonograms: List<Nonogram> by mutableStateOf(emptyList())
        private set
    var isLoading: Boolean by mutableStateOf(true)
        private set
    var isRefreshing: Boolean by mutableStateOf(false)
        private set
    var filterSort: FilterSortState by mutableStateOf(FilterSortState())
        private set
    var authorUid: String? by mutableStateOf(null)
        private set
    val filterEntries: List<FilterEntry> by derivedStateOf { NonogramFilters.forUser(authorUid) }
    val visibleNonograms: List<Nonogram> by derivedStateOf {
        val filtered = filterSort.applyTo(nonograms, filterEntries)
        if (filterSort.sortsBy(filterEntries)) filtered else filtered.ownFirst()
    }
    private var progressMap: Map<Long, List<List<Int>>> by mutableStateOf(emptyMap())
    private var beatMap: Map<Long, Long> by mutableStateOf(emptyMap())

    init {
        reload(true)
    }

    fun reload(loadAll: Boolean = false) {
        isLoading = loadAll
        launchGuarded(onError = { println("Menu: loading nonograms failed: ${it.message}") }) {
            try {
                loadNonograms()
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Runs [sync], then re-reads. The pull-to-refresh flag is set and cleared in this one coroutine
     * rather than by another ViewModel's callback, which could fail to arrive and leave it spinning.
     */
    fun refresh(sync: suspend () -> Unit) {
        if (isRefreshing) return
        isRefreshing = true
        launchGuarded(onError = { println("Menu: refresh failed: ${it.message}") }) {
            try {
                sync()
                loadNonograms()
            } finally {
                isRefreshing = false
            }
        }
    }

    private suspend fun loadNonograms() {
        val uid = authRepository.currentUserUid.value
        authorUid = uid
        nonograms = sdk.getVisibleNonograms(uid.orEmpty())
        if (uid != null) {
            val allProgress = sdk.getProgressForUser(uid)
            progressMap = allProgress
                .filter { it.board != null }
                .associate { it.nonogramId to it.board!! }
            beatMap = allProgress
                .filter { it.beat > 0 }
                .associate { it.nonogramId to it.beat }
        } else {
            progressMap = emptyMap()
            beatMap = emptyMap()
        }
    }

    fun applyFilterSort(state: FilterSortState) {
        filterSort = state
    }

    /** Your puzzles first, but only unsorted: an explicit sort orders own and other puzzles together. */
    private fun List<Nonogram>.ownFirst(): List<Nonogram> =
        sortedByDescending { it.isOwned(authorUid) }

    fun updateSingleProgress(nonogramId: Long, board: List<List<Int>>) {
        progressMap = progressMap + (nonogramId to board)
    }

    fun clearProgress(nonogramId: Long) {
        progressMap = progressMap - nonogramId
    }

    fun getProgress(id: Long): List<List<Int>> = progressMap[id].orEmpty()

    fun hasProgress(id: Long): Boolean = getProgress(id).any { row -> row.any { it != 0 } }

    fun getBeatCount(id: Long): Long = beatMap[id] ?: 0

    fun incrementBeatCount(nonogramId: Long) {
        beatMap = beatMap + (nonogramId to (beatMap[nonogramId] ?: 0) + 1)
    }
}

