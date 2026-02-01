package com.stib.agent.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.stib.agent.data.models.User
import com.stib.agent.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EditProfileViewModel : ViewModel() {
    private val repository = UserRepository()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage

    init {
        loadUser()
    }

    fun loadUser() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _user.value = repository.getCurrentUser()
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Erreur : ${e.message}"
                Log.e("EditProfileViewModel", "Erreur chargement", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUser(
        prenom: String,
        nom: String,
        email: String,
        telephone: String,
        depot: String
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                // TODO: Implémenter la mise à jour dans Firestore
                repository.updateUser(
                    prenom = prenom,
                    nom = nom,
                    email = email,
                    telephone = telephone,
                    depot = depot
                )

                _successMessage.value = "Profil mis à jour avec succès !"
                _error.value = null

                Log.d("EditProfileViewModel", "Profil mis à jour")
            } catch (e: Exception) {
                _error.value = "Erreur : ${e.message}"
                _successMessage.value = null
                Log.e("EditProfileViewModel", "Erreur mise à jour", e)
            } finally {
                _isSaving.value = false
            }
        }
    }
}
