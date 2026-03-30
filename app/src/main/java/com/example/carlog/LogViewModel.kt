package com.example.carlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

class LogViewModel(private val dao: CarDao) : ViewModel() {
    val logs = MutableStateFlow<List<CarEntry>>(emptyList())

    fun fetchLogs() {
        viewModelScope.launch {
            logs.value = dao.getAllLogs()
        }
    }

    fun addLog(entry: CarEntry) {
        viewModelScope.launch {
            dao.insertLog(entry)
            fetchLogs()
        }
    }
}
