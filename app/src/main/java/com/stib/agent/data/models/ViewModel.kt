package com.stib.agent.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NewsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _unreadNewsCount = MutableStateFlow(0)
    val unreadNewsCount: StateFlow<Int> = _unreadNewsCount

    private var newsListener: ListenerRegistration? = null
    private var lastNewsReadAt = 0L

    fun startListeningToNews(userSyndicat: String, userDepot: String) {
        val userId = auth.currentUser?.uid ?: return

        // D'abord, récupérer le timestamp de la dernière lecture
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                lastNewsReadAt = userDoc.getLong("lastNewsReadAt") ?: 0L
                Log.d("NewsViewModel", "📋 lastNewsReadAt: $lastNewsReadAt")

                // Écouter les changes de news en temps réel
                newsListener = db.collection("news")
                    .whereEqualTo("actif", true)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("NewsViewModel", "❌ Erreur: ${error.message}")
                            return@addSnapshotListener
                        }

                        if (snapshot != null) {
                            // Compter SEULEMENT les news créées APRÈS la dernière lecture
                            var count = 0
                            for (doc in snapshot.documents) {
                                val syndicat = doc.getString("syndicat") ?: "all"
                                val depots = doc.get("depots") as? List<String> ?: listOf("all")
                                val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L

                                val syndicatMatch = syndicat == "all" || syndicat == userSyndicat
                                val depotMatch = depots.contains("all") || depots.contains(userDepot)
                                val isNew = createdAt > lastNewsReadAt  // ✅ VÉRIFIER SI LA NEWS EST NOUVELLE

                                if (syndicatMatch && depotMatch && isNew) {
                                    count++
                                }
                            }

                            _unreadNewsCount.value = count
                            Log.d("NewsViewModel", "📰 $count news NOUVELLES depuis $lastNewsReadAt")
                        }
                    }
            }
            .addOnFailureListener { e ->
                Log.e("NewsViewModel", "❌ Erreur récupération lastNewsReadAt: ${e.message}")
            }
    }

    fun resetNewsCount() {
        _unreadNewsCount.value = 0
        Log.d("NewsViewModel", "🔄 Compteur réinitialisé à 0")
    }

    override fun onCleared() {
        super.onCleared()
        newsListener?.remove()
    }
}
