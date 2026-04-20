package com.chrisirlam.snorenudge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisirlam.snorenudge.data.SnoreDatabase
import com.chrisirlam.snorenudge.data.SnoreEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = SnoreDatabase.getInstance(application).snoreEventDao()

    val events: StateFlow<List<SnoreEvent>> = dao.getAllEventsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearHistory() = viewModelScope.launch {
        dao.deleteAll()
    }

    /** Remove events older than 30 days to prevent unbounded growth. */
    fun pruneOldEvents() = viewModelScope.launch {
        val cutoffMs = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        dao.deleteEventsBefore(cutoffMs)
    }
}
