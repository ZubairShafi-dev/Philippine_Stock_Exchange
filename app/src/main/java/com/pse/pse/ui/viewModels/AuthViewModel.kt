package com.pse.pse.ui.viewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.pse.pse.data.repository.AuthRepository
import com.pse.pse.models.UserModel
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(application)

    fun registerUser(userModel: UserModel, onResult: (FirebaseUser?) -> Unit) {
        viewModelScope.launch {
            val user = authRepository.registerUser(userModel)
            onResult(user)
        }
    }

    // ✅ Updated this to return (Boolean, FirebaseUser?)
    fun loginUser(email: String, password: String, onResult: (Boolean, FirebaseUser?) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.loginUser(email, password)
            onResult(result.first, result.second)
        }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.sendPasswordResetEmail(email)
            onResult(result)
        }
    }
}
