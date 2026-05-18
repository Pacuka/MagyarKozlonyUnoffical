package com.pacuka.magyarkozlony.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pacuka.magyarkozlony.models.Kozlony
import com.pacuka.magyarkozlony.services.KozlonyService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class AppTheme {
    LIGHT, DARK, SYSTEM, NAVY
}

class KozlonyViewModel : ViewModel() {
    private val service = KozlonyService()

    private val _homeIssues = MutableStateFlow<List<Kozlony>>(emptyList())
    private val _searchIssues = MutableStateFlow<List<Kozlony>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val displayIssues: StateFlow<List<Kozlony>> = combine(
        _homeIssues, _searchIssues, _searchQuery
    ) { home, search, query ->
        if (query.isBlank()) home else search
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _appTheme = MutableStateFlow(AppTheme.SYSTEM)
    val appTheme: StateFlow<AppTheme> = _appTheme

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    private val _externalBrowserEnabled = MutableStateFlow(false)
    val externalBrowserEnabled: StateFlow<Boolean> = _externalBrowserEnabled

    private var homePage = 1
    private var searchPage = 1
    private var canLoadMoreHome = true
    private var canLoadMoreSearch = true
    private var searchJob: Job? = null

    init {
        fetchIssues()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchIssues.value = emptyList()
        } else {
            searchJob = viewModelScope.launch {
                delay(800)
                performSearch()
            }
        }
    }

    fun performSearch() {
        if (_searchQuery.value.isBlank()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            searchPage = 1
            canLoadMoreSearch = true
            val results = service.getIssues(searchPage, _searchQuery.value)
            _searchIssues.value = results
            _isLoading.value = false
        }
    }

    fun setTheme(theme: AppTheme) {
        _appTheme.value = theme
    }

    fun toggleNotifications() {
        _notificationsEnabled.value = !_notificationsEnabled.value
    }

    fun toggleExternalBrowser() {
        _externalBrowserEnabled.value = !_externalBrowserEnabled.value
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            if (_searchQuery.value.isBlank()) {
                homePage = 1
                canLoadMoreHome = true
                _homeIssues.value = service.getIssues(homePage)
            } else {
                searchPage = 1
                canLoadMoreSearch = true
                _searchIssues.value = service.getIssues(searchPage, _searchQuery.value)
            }
            _isRefreshing.value = false
        }
    }

    fun loadNextPage() {
        if (_isLoading.value || _isRefreshing.value) return

        if (_searchQuery.value.isBlank()) {
            if (!canLoadMoreHome) return
            viewModelScope.launch {
                _isLoading.value = true
                homePage++
                val nextIssues = service.getIssues(homePage)
                if (nextIssues.isEmpty()) canLoadMoreHome = false
                else _homeIssues.value = _homeIssues.value + nextIssues
                _isLoading.value = false
            }
        } else {
            if (!canLoadMoreSearch) return
            viewModelScope.launch {
                _isLoading.value = true
                searchPage++
                val nextIssues = service.getIssues(searchPage, _searchQuery.value)
                if (nextIssues.isEmpty()) canLoadMoreSearch = false
                else _searchIssues.value = _searchIssues.value + nextIssues
                _isLoading.value = false
            }
        }
    }

    private fun fetchIssues() {
        viewModelScope.launch {
            _isLoading.value = true
            _homeIssues.value = service.getIssues(1)
            _isLoading.value = false
        }
    }
}
