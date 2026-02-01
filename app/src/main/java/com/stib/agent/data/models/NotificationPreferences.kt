package com.stib.agent.data.model

/**
 * Modèle de données pour les préférences de notifications
 * Source unique de vérité pour toute l'app
 */
data class NotificationPreferences(
    // Réveil App (plusieurs alarmes)
    val appAlarmEnabled: Boolean = true,
    val appAlarms: List<Int> = listOf(80, 65, 50, 40),  // Minutes avant le service

    // Horloge native
    val clockAlarmEnabled: Boolean = false,
    val clockAlarmMinutesBefore: Int = 60,

    // Rappel de départ
    val departureReminderEnabled: Boolean = true,
    val departureReminderMinutesBefore: Int = 20,

    // Notification de la veille
    val dayBeforeEnabled: Boolean = true,
    val dayBeforeHour: Int = 18,
    val dayBeforeMinute: Int = 0,

    // 🆕 Son du réveil
    val alarmSoundUri: String = "",

    // Timestamp de dernière modification
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Retourne le temps formaté de la notification de la veille
     */
    fun getDayBeforeTimeFormatted(): String {
        return "${dayBeforeHour.toString().padStart(2, '0')}:${dayBeforeMinute.toString().padStart(2, '0')}"
    }

    /**
     * Vérifie si au moins une alarme est activée
     */
    fun hasAnyAlarmEnabled(): Boolean {
        return appAlarmEnabled || clockAlarmEnabled || departureReminderEnabled || dayBeforeEnabled
    }

    /**
     * Retourne le nombre total d'alarmes activées (approximatif)
     */
    fun getActiveAlarmsCount(): Int {
        var count = 0
        if (appAlarmEnabled) count += appAlarms.size
        if (clockAlarmEnabled) count += 1
        if (departureReminderEnabled) count += 1
        if (dayBeforeEnabled) count += 1
        return count
    }
}
