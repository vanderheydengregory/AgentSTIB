package com.stib.agent.notifications

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Gestionnaire centralisé des paramètres de notifications
 * Charge depuis Firestore et met en cache
 */
object NotificationSettingsManager {

    private const val TAG = "NotificationSettingsManager"

    // Cache des paramètres
    private var cachedSettings: NotificationParams? = null

    /**
     * Paramètres de notifications
     */
    data class NotificationParams(
        // Réveil app
        val appAlarmEnabled: Boolean = true,
        val appAlarms: List<Int> = listOf(10, 20, 30),

        // Horloge native
        val clockAlarmEnabled: Boolean = false,
        val clockAlarmMinutesBefore: Int = 60,

        // Rappel de départ
        val departureReminderEnabled: Boolean = true,
        val departureReminderMinutesBefore: Int = 20,

        // Notification de la veille
        val dayBeforeEnabled: Boolean = true,
        val dayBeforeHour: Int = 20,
        val dayBeforeMinute: Int = 0,

        // Son de l'alarme
        val alarmSoundUri: String? = null
    )

    /**
     * Charge les paramètres depuis Firestore (avec cache)
     */
    suspend fun getSettings(forceRefresh: Boolean = false): NotificationParams {
        // Si cache valide et pas de refresh forcé
        if (!forceRefresh && cachedSettings != null) {
            Log.d(TAG, "📦 Utilisation du cache")
            return cachedSettings!!
        }

        Log.d(TAG, "🔄 Chargement depuis Firestore...")

        val firestore = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Log.w(TAG, "⚠️ User non connecté, utilisation valeurs par défaut")
            return NotificationParams()
        }

        return try {
            val doc = firestore.collection("users")
                .document(userId)
                .collection("Settings")
                .document("Notifications")
                .get()
                .await()

            if (!doc.exists()) {
                Log.w(TAG, "⚠️ Document Notifications inexistant, valeurs par défaut")
                return NotificationParams()
            }

            // Extraire les paramètres
            @Suppress("UNCHECKED_CAST")
            val appAlarmsArray = doc.get("appAlarms") as? List<Long>
            val appAlarmsList = appAlarmsArray?.map { it.toInt() } ?: listOf(10, 20, 30)

            val settings = NotificationParams(
                appAlarmEnabled = doc.getBoolean("appAlarmEnabled") ?: true,
                appAlarms = appAlarmsList,
                clockAlarmEnabled = doc.getBoolean("clockAlarmEnabled") ?: false,
                clockAlarmMinutesBefore = doc.getLong("clockAlarmMinutesBefore")?.toInt() ?: 60,
                departureReminderEnabled = doc.getBoolean("departureReminderEnabled") ?: true,
                departureReminderMinutesBefore = doc.getLong("departureReminderMinutesBefore")?.toInt() ?: 20,
                dayBeforeEnabled = doc.getBoolean("dayBeforeEnabled") ?: true,
                dayBeforeHour = doc.getLong("dayBeforeHour")?.toInt() ?: 20,
                dayBeforeMinute = doc.getLong("dayBeforeMinute")?.toInt() ?: 0,
                alarmSoundUri = doc.getString("alarmSoundUri")
            )

            // Mettre en cache
            cachedSettings = settings

            Log.d(TAG, "✅ Settings chargés: appAlarms=${settings.appAlarms}, departure=${settings.departureReminderMinutesBefore}min")

            settings

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement settings: ${e.message}")
            NotificationParams() // Valeurs par défaut
        }
    }

    /**
     * Invalide le cache (à appeler après modification des settings)
     */
    fun invalidateCache() {
        Log.d(TAG, "🗑️ Cache invalidé")
        cachedSettings = null
    }

    /**
     * Charge les settings de manière synchrone (callback)
     */
    fun getSettingsAsync(callback: (NotificationParams) -> Unit) {
        val firestore = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Log.w(TAG, "⚠️ User non connecté")
            callback(NotificationParams())
            return
        }

        // Si cache valide
        if (cachedSettings != null) {
            Log.d(TAG, "📦 Utilisation du cache")
            callback(cachedSettings!!)
            return
        }

        Log.d(TAG, "🔄 Chargement async depuis Firestore...")

        firestore.collection("users")
            .document(userId)
            .collection("Settings")
            .document("Notifications")
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Log.w(TAG, "⚠️ Document inexistant")
                    callback(NotificationParams())
                    return@addOnSuccessListener
                }

                @Suppress("UNCHECKED_CAST")
                val appAlarmsArray = doc.get("appAlarms") as? List<Long>
                val appAlarmsList = appAlarmsArray?.map { it.toInt() } ?: listOf(10, 20, 30)

                val settings = NotificationParams(
                    appAlarmEnabled = doc.getBoolean("appAlarmEnabled") ?: true,
                    appAlarms = appAlarmsList,
                    clockAlarmEnabled = doc.getBoolean("clockAlarmEnabled") ?: false,
                    clockAlarmMinutesBefore = doc.getLong("clockAlarmMinutesBefore")?.toInt() ?: 60,
                    departureReminderEnabled = doc.getBoolean("departureReminderEnabled") ?: true,
                    departureReminderMinutesBefore = doc.getLong("departureReminderMinutesBefore")?.toInt() ?: 20,
                    dayBeforeEnabled = doc.getBoolean("dayBeforeEnabled") ?: true,
                    dayBeforeHour = doc.getLong("dayBeforeHour")?.toInt() ?: 20,
                    dayBeforeMinute = doc.getLong("dayBeforeMinute")?.toInt() ?: 0,
                    alarmSoundUri = doc.getString("alarmSoundUri")
                )

                cachedSettings = settings

                Log.d(TAG, "✅ Settings chargés async")
                callback(settings)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Erreur: ${e.message}")
                callback(NotificationParams())
            }
    }
}
