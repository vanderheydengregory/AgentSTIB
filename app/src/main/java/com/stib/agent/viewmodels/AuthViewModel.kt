package com.stib.agent.ui.viewmodels

import androidx.lifecycle.ViewModel
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val newLoginState = firebaseAuth.currentUser != null
        Log.d("AuthViewModel", "🔄 Auth state changed: $newLoginState")
        _isLoggedIn.value = newLoginState
    }

    init {
        auth.addAuthStateListener(authStateListener)
        Log.d("AuthViewModel", "🎯 AuthStateListener ajouté")
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
        Log.d("AuthViewModel", "🧹 AuthStateListener supprimé")
    }

    fun updateLoginStatus() {
        _isLoggedIn.value = auth.currentUser != null
    }

    fun logout() {
        try {
            auth.signOut()
            _isLoggedIn.value = false
            Log.d("AuthViewModel", "✅ Déconnexion réussie")
        } catch (e: Exception) {
            Log.e("AuthViewModel", "❌ Erreur déconnexion", e)
        }
    }
}
