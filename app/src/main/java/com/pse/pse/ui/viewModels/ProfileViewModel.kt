package com.pse.pse.ui.viewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pse.pse.data.repository.AuthRepository
import com.pse.pse.models.UserModel
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository(application)

    private val _user = MutableLiveData<UserModel>()
    val user: LiveData<UserModel> = _user

    private val _updateStatus = MutableLiveData<Boolean>()
    val updateStatus: LiveData<Boolean> = _updateStatus

    fun loadProfile(uid: String) {
        viewModelScope.launch {
            authRepository.getUserProfile(uid)?.let { _user.postValue(it) }
        }
    }

//    fun updateProfile(newModel: UserModel) {
//        viewModelScope.launch {
//            val success = authRepository.updateUserProfile(newModel)
//            _updateStatus.postValue(success)
//            if (success) _user.postValue(newModel)
//        }
//    }


    fun updatePhoneNumber(newPhoneNumber: String) {
        viewModelScope.launch {
            val uid = user.value?.uid ?: return@launch  // Get current user's uid
            val success = authRepository.updateUserPhoneNumber(uid, newPhoneNumber)
            _updateStatus.postValue(success)

            if (success) {
                _user.value =
                    _user.value?.copy(phoneNumber = newPhoneNumber)  // Update local LiveData
            }
        }
    }
}
