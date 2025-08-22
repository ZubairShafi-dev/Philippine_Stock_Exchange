package com.pse.pse.ui.viewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pse.pse.data.repository.TransactionRepository
import com.pse.pse.models.TransactionModel
import kotlinx.coroutines.launch

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TransactionRepository(application)

    private val _withdrawResult = MutableLiveData<Boolean>()
    val withdrawResult: LiveData<Boolean> get() = _withdrawResult

    private val _depositResult = MutableLiveData<Boolean>()
    val depositResult: LiveData<Boolean> get() = _depositResult

    private val _transactionHistory = MutableLiveData<List<TransactionModel>>()
    val transactionHistory: LiveData<List<TransactionModel>> get() = _transactionHistory

    private val _currentBalance = MutableLiveData<Double?>()
    val currentBalance: LiveData<Double?> get() = _currentBalance

    init {
        // Push repository’s real‑time balance into LiveData for the UI
        viewModelScope.launch {
            repository.liveBalance.collect { bal ->
                bal?.let { _currentBalance.postValue(it) }
            }
        }
    }

    fun submitWithdrawal(amount: Double, address: String) {
        viewModelScope.launch {
            val result = repository.submitWithdrawal(amount, address)
            _withdrawResult.postValue(result)
        }
    }

    suspend fun deposit(amount: Double, address: String) {
        repository.deposit(amount, address)
    }

    fun loadTransactionHistory() {
        viewModelScope.launch {
            val history = repository.getHistory()
            _transactionHistory.postValue(history)
        }
    }

    fun loadCurrentBalance() {
        viewModelScope.launch {
            val balance = repository.getCurrentBalance()
            _currentBalance.postValue(balance)
        }
    }

    suspend fun hasPendingWithdrawal(): Boolean {
        return repository.hasPendingWithdrawal()
    }

    /** Call from Fragment.onStart (or once after login). */
    fun startBalanceSync() = repository.startBalanceSync()

    /** Call from Fragment.onStop OR ViewModel.onCleared. */
    fun stopBalanceSync() = repository.stopBalanceSync()

    val pendingWithdrawal = repository.pendingWithdrawal

    fun startPendingWatcher() = repository.startPendingWatcher()
    fun stopPendingWatcher() = repository.stopPendingWatcher()

}
