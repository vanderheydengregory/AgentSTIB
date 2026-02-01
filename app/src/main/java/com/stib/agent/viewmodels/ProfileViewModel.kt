package com.stib.agent.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.stib.agent.data.models.User
import com.stib.agent.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val repository = UserRepository()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _user.value = repository.getCurrentUser()
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Erreur : ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshUser() {
        loadCurrentUser()
    }

    fun logout() {
        viewModelScope.launch {
            try {
                // Effacer les données utilisateur
                _user.value = null
                _isLoading.value = true

                // TODO: Appeler AuthViewModel pour effacer le token/session
                // TODO: Naviguer vers LoginScreen

                Log.d("ProfileViewModel", "Déconnexion réussie")
            } catch (e: Exception) {
                _error.value = "Erreur lors de la déconnexion: ${e.message}"
                Log.e("ProfileViewModel", "Erreur déconnexion", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
