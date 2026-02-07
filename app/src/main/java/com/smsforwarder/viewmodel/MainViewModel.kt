package com.smsforwarder.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.smsforwarder.SmsForwarderApp
import com.smsforwarder.data.model.FilterRule
import com.smsforwarder.data.model.ForwardNumber
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as SmsForwarderApp).repository

    val filterRules: LiveData<List<FilterRule>> = repository.allFilterRules
    val forwardNumbers: LiveData<List<ForwardNumber>> = repository.allForwardNumbers
    val forwardLogs = repository.allForwardLogs

    fun insertRule(rule: FilterRule) = viewModelScope.launch {
        repository.insertRule(rule)
    }

    fun updateRule(rule: FilterRule) = viewModelScope.launch {
        repository.updateRule(rule)
    }

    fun deleteRule(rule: FilterRule) = viewModelScope.launch {
        repository.deleteRule(rule)
    }

    fun insertNumber(number: ForwardNumber) = viewModelScope.launch {
        repository.insertNumber(number)
    }

    fun updateNumber(number: ForwardNumber) = viewModelScope.launch {
        repository.updateNumber(number)
    }

    fun deleteNumber(number: ForwardNumber) = viewModelScope.launch {
        repository.deleteNumber(number)
    }

    fun clearLogs() = viewModelScope.launch {
        repository.clearLogs()
    }
}
