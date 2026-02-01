package com.stib.agent.components

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stib.agent.data.model.NotificationPreferences
import kotlinx.coroutines.tasks.await

/**
 * Gestionnaire centralisé pour les préférences de notifications
 * Source unique de vérité pour toute l'app
 */
object NotificationPreferencesManager {

    private const val TAG = "NotificationPreferencesManager"

    // Collection et document Firestore
    private const val COLLECTION_SETTINGS = "Settings"
    private const val DOCUMENT_NOTIFICATIONS = "Notifications"

    // Cache local (optionnel, pour éviter trop d'appels Firestore)
    private var cachedPreferences: NotificationPreferences? = null
    private var cacheTimestamp: Long = 0
    private const val CACHE_VALIDITY_MS = 30_000L // 30 secondes

    /**
     * Récupère les préférences de notifications (version suspend)
     */
    suspend fun getPreferences(): NotificationPreferences {
        // Vérifier le cache
        if (cachedPreferences != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_VALIDITY_MS) {
            Log.d(TAG, "📦 Retour du cache")
            return cachedPreferences!!
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Log.w(TAG, "⚠️ User non connecté, retour des valeurs par défaut")
            return NotificationPreferences()
        }

        return try {
            val firestore = FirebaseFirestore.getInstance()
            val doc = firestore.collection("users")
                .document(userId)
                .collection(COLLECTION_SETTINGS)
                .document(DOCUMENT_NOTIFICATIONS)
                .get()
                .await()

            if (!doc.exists()) {
                Log.w(TAG, "⚠️ Document Settings/Notifications introuvable, création avec valeurs par défaut")
                val defaultPreferences = NotificationPreferences()
                savePreferences(defaultPreferences)
                return defaultPreferences
            }

            // Parser les données Firestore
            val appAlarmEnabled = doc.getBoolean("appAlarmEnabled") ?: true
            val clockAlarmEnabled = doc.getBoolean("clockAlarmEnabled") ?: false
            val departureReminderEnabled = doc.getBoolean("departureReminderEnabled") ?: true
            val dayBeforeEnabled = doc.getBoolean("dayBeforeEnabled") ?: true

            @Suppress("UNCHECKED_CAST")
            val appAlarmsArray = doc.get("appAlarms") as? List<Long>
            val appAlarms = appAlarmsArray?.map { it.toInt() } ?: listOf(80, 65, 50, 40)

            val clockAlarmMinutesBefore = doc.getLong("clockAlarmMinutesBefore")?.toInt() ?: 60
            val departureReminderMinutesBefore = doc.getLong("departureReminderMinutesBefore")?.toInt() ?: 20
            val dayBeforeHour = doc.getLong("dayBeforeHour")?.toInt() ?: 18
            val dayBeforeMinute = doc.getLong("dayBeforeMinute")?.toInt() ?: 0
            val alarmSoundUri = doc.getString("alarmSoundUri") ?: ""
            val lastUpdated = doc.getLong("lastUpdated") ?: System.currentTimeMillis()

            val preferences = NotificationPreferences(
                appAlarmEnabled = appAlarmEnabled,
                appAlarms = appAlarms,
                clockAlarmEnabled = clockAlarmEnabled,
                clockAlarmMinutesBefore = clockAlarmMinutesBefore,
                departureReminderEnabled = departureReminderEnabled,
                departureReminderMinutesBefore = departureReminderMinutesBefore,
                dayBeforeEnabled = dayBeforeEnabled,
                dayBeforeHour = dayBeforeHour,
                dayBeforeMinute = dayBeforeMinute,
                alarmSoundUri = alarmSoundUri,
                lastUpdated = lastUpdated
            )

            // Mettre en cache
            cachedPreferences = preferences
            cacheTimestamp = System.currentTimeMillis()

            Log.d(TAG, "✅ Préférences chargées:")
            Log.d(TAG, "   App alarm: $appAlarmEnabled (${appAlarms.size} alarmes)")
            Log.d(TAG, "   Clock: $clockAlarmEnabled")
            Log.d(TAG, "   Départ: $departureReminderEnabled ($departureReminderMinutesBefore min)")
            Log.d(TAG, "   Veille: $dayBeforeEnabled (${dayBeforeHour}h)")
            Log.d(TAG, "   Son: ${if (alarmSoundUri.isEmpty()) "Par défaut" else "Personnalisé"}")

            preferences

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement préférences: ${e.message}")
            NotificationPreferences() // Retour valeurs par défaut en cas d'erreur
        }
    }

    /**
     * Récupère les préférences (version callback)
     */
    fun getPreferencesAsync(callback: (NotificationPreferences) -> Unit) {
        // Vérifier le cache
        if (cachedPreferences != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_VALIDITY_MS) {
            Log.d(TAG, "📦 Retour du cache (callback)")
            callback(cachedPreferences!!)
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Log.w(TAG, "⚠️ User non connecté")
            callback(NotificationPreferences())
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("users")
            .document(userId)
            .collection(COLLECTION_SETTINGS)
            .document(DOCUMENT_NOTIFICATIONS)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Log.w(TAG, "⚠️ Document introuvable, retour valeurs par défaut")
                    callback(NotificationPreferences())
                    return@addOnSuccessListener
                }

                try {
                    val appAlarmEnabled = doc.getBoolean("appAlarmEnabled") ?: true
                    val clockAlarmEnabled = doc.getBoolean("clockAlarmEnabled") ?: false
                    val departureReminderEnabled = doc.getBoolean("departureReminderEnabled") ?: true
                    val dayBeforeEnabled = doc.getBoolean("dayBeforeEnabled") ?: true

                    @Suppress("UNCHECKED_CAST")
                    val appAlarmsArray = doc.get("appAlarms") as? List<Long>
                    val appAlarms = appAlarmsArray?.map { it.toInt() } ?: listOf(80, 65, 50, 40)

                    val clockAlarmMinutesBefore = doc.getLong("clockAlarmMinutesBefore")?.toInt() ?: 60
                    val departureReminderMinutesBefore = doc.getLong("departureReminderMinutesBefore")?.toInt() ?: 20
                    val dayBeforeHour = doc.getLong("dayBeforeHour")?.toInt() ?: 18
                    val dayBeforeMinute = doc.getLong("dayBeforeMinute")?.toInt() ?: 0
                    val alarmSoundUri = doc.getString("alarmSoundUri") ?: ""
                    val lastUpdated = doc.getLong("lastUpdated") ?: System.currentTimeMillis()

                    val preferences = NotificationPreferences(
                        appAlarmEnabled = appAlarmEnabled,
                        appAlarms = appAlarms,
                        clockAlarmEnabled = clockAlarmEnabled,
                        clockAlarmMinutesBefore = clockAlarmMinutesBefore,
                        departureReminderEnabled = departureReminderEnabled,
                        departureReminderMinutesBefore = departureReminderMinutesBefore,
                        dayBeforeEnabled = dayBeforeEnabled,
                        dayBeforeHour = dayBeforeHour,
                        dayBeforeMinute = dayBeforeMinute,
                        alarmSoundUri = alarmSoundUri,
                        lastUpdated = lastUpdated
                    )

                    // Mettre en cache
                    cachedPreferences = preferences
                    cacheTimestamp = System.currentTimeMillis()

                    Log.d(TAG, "✅ Préférences chargées (callback)")
                    callback(preferences)

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erreur parsing: ${e.message}")
                    callback(NotificationPreferences())
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Erreur Firestore: ${e.message}")
                callback(NotificationPreferences())
            }
    }

    /**
     * Sauvegarde les préférences dans Firestore
     */
    suspend fun savePreferences(preferences: NotificationPreferences): Boolean {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Log.w(TAG, "⚠️ User non connecté")
            return false
        }

        return try {
            val firestore = FirebaseFirestore.getInstance()

            val data = hashMapOf(
                "appAlarmEnabled" to preferences.appAlarmEnabled,
                "appAlarms" to preferences.appAlarms,
                "clockAlarmEnabled" to preferences.clockAlarmEnabled,
                "clockAlarmMinutesBefore" to preferences.clockAlarmMinutesBefore,
                "departureReminderEnabled" to preferences.departureReminderEnabled,
                "departureReminderMinutesBefore" to preferences.departureReminderMinutesBefore,
                "dayBeforeEnabled" to preferences.dayBeforeEnabled,
                "dayBeforeHour" to preferences.dayBeforeHour,
                "dayBeforeMinute" to preferences.dayBeforeMinute,
                "alarmSoundUri" to preferences.alarmSoundUri,
                "lastUpdated" to System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(userId)
                .collection(COLLECTION_SETTINGS)
                .document(DOCUMENT_NOTIFICATIONS)
                .set(data)
                .await()

            // Mettre à jour le cache
            cachedPreferences = preferences.copy(lastUpdated = System.currentTimeMillis())
            cacheTimestamp = System.currentTimeMillis()

            Log.d(TAG, "✅ Préférences sauvegardées")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sauvegarde: ${e.message}")
            false
        }
    }

    /**
     * Invalide le cache (force un rechargement)
     */
    fun invalidateCache() {
        cachedPreferences = null
        cacheTimestamp = 0
        Log.d(TAG, "🗑️ Cache invalidé")
    }

    /**
     * Réinitialise les préférences aux valeurs par défaut
     */
    suspend fun resetToDefaults(): Boolean {
        return savePreferences(NotificationPreferences())
    }
}
