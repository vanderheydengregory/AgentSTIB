package com.stib.agent.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stib.agent.data.models.User
import kotlinx.coroutines.tasks.await
import android.util.Log

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun getCurrentUser(): User? {
        return try {
            val userId = auth.currentUser?.uid ?: return null
            val document = db.collection("users")
                .document(userId)
                .get()
                .await()

            document.toObject(User::class.java)
        } catch (e: Exception) {
            Log.e("UserRepository", "❌ Erreur: ${e.message}", e)
            null
        }
    }

    suspend fun getUserById(userId: String): User? {
        return try {
            val document = db.collection("users")
                .document(userId)
                .get()
                .await()

            document.toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUser(
        prenom: String,
        nom: String,
        email: String,
        telephone: String,
        depot: String
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val updatedUser = mapOf(
            "prenom" to prenom,
            "nom" to nom,
            "email" to email,
            "telephone" to telephone,
            "depot" to depot
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .update(updatedUser)
            .addOnSuccessListener {
                Log.d("UserRepository", "Profil mis à jour")
            }
            .addOnFailureListener { e ->
                throw Exception("Erreur mise à jour: ${e.message}")
            }
    }

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
}
