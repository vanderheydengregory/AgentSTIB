// notifications/receivers/NotificationReceiver.kt
package com.stib.agent.notifications.receivers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import com.stib.agent.notifications.ui.AlarmActivity
import com.stib.agent.notifications.ui.DepartureReminderActivity
import com.stib.agent.notifications.utils.NotificationHelper
import java.util.Calendar
import com.stib.agent.components.NotificationPreferencesManager

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Notification reçue : ${intent.action}")

        when (intent.action) {
            ACTION_ALARM -> handleAppAlarm(context, intent)
            ACTION_ALARM_CLOCK -> handleAlarmClock(context, intent)
            ACTION_DEPARTURE_REMINDER -> handleDepartureReminder(context, intent)
            ACTION_DAY_BEFORE -> handleDayBeforeNotification(context, intent)

            // Ancienne méthode (fallback)
            else -> handleLegacyNotification(context, intent)
        }
    }

    private fun handleAppAlarm(context: Context, intent: Intent) {
        val serviceId = intent.getStringExtra(EXTRA_SERVICE_ID) ?: return
        val serviceTime = intent.getStringExtra(EXTRA_SERVICE_TIME) ?: ""
        val serviceLine = intent.getStringExtra(EXTRA_SERVICE_LINE) ?: ""
        val snoozeCount = intent.getIntExtra(AlarmActivity.EXTRA_SNOOZE_COUNT, 0)
        val alarmNumber = intent.getIntExtra(AlarmActivity.EXTRA_ALARM_NUMBER, 1)

        // 🆕 CHARGER LE SON DEPUIS LES PRÉFÉRENCES
        com.stib.agent.components.NotificationPreferencesManager.getPreferencesAsync { prefs ->
            val soundUri = prefs.alarmSoundUri

            Log.d(TAG, "🔊 Son chargé depuis préférences: $soundUri")

            // Lancer l'AlarmActivity avec le son
            val activityIntent = Intent(context, AlarmActivity::class.java).apply {
                putExtra(AlarmActivity.EXTRA_SERVICE_ID, serviceId)
                putExtra(AlarmActivity.EXTRA_SERVICE_LINE, serviceLine)
                putExtra(AlarmActivity.EXTRA_SERVICE_TIME, serviceTime)
                putExtra(AlarmActivity.EXTRA_ALARM_NUMBER, alarmNumber)
                putExtra(AlarmActivity.EXTRA_SNOOZE_COUNT, snoozeCount)
                putExtra(AlarmActivity.EXTRA_SOUND_URI, soundUri)  // 🆕 AJOUTER LE SON
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            }

            try {
                context.startActivity(activityIntent)
                Log.d(TAG, "AlarmActivity lancée avec son: $soundUri")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lancement AlarmActivity : ${e.message}")

                // Plan B : Notification
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    serviceId.hashCode(),
                    activityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                NotificationHelper.showFullScreenAlarmNotification(
                    context,
                    serviceId,
                    serviceTime,
                    serviceLine,
                    alarmNumber,
                    pendingIntent
                )
            }
        }
    }

    private fun handleAlarmClock(context: Context, intent: Intent) {
        val alarmTimeMillis = intent.getLongExtra(EXTRA_ALARM_TIME_MILLIS, 0)
        val serviceTime = intent.getStringExtra(EXTRA_SERVICE_TIME) ?: ""
        val serviceLine = intent.getStringExtra(EXTRA_SERVICE_LINE) ?: ""

        val calendar = Calendar.getInstance().apply {
            timeInMillis = alarmTimeMillis
        }

        val clockIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, calendar.get(Calendar.HOUR_OF_DAY))
            putExtra(AlarmClock.EXTRA_MINUTES, calendar.get(Calendar.MINUTE))
            putExtra(AlarmClock.EXTRA_MESSAGE, "Service Ligne $serviceLine à $serviceTime")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(clockIntent)
            Log.d(TAG, "Réveil horloge configuré")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur configuration réveil horloge: ${e.message}")
        }
    }

    private fun handleDepartureReminder(context: Context, intent: Intent) {
        val serviceId = intent.getStringExtra(EXTRA_SERVICE_ID) ?: return
        val serviceTime = intent.getStringExtra(EXTRA_SERVICE_TIME) ?: ""
        val serviceLine = intent.getStringExtra(EXTRA_SERVICE_LINE) ?: ""
        val minutesBefore = intent.getIntExtra(EXTRA_MINUTES_BEFORE, 15)

        // Lancer la DepartureReminderActivity directement
        val activityIntent = Intent(context, DepartureReminderActivity::class.java).apply {
            putExtra(DepartureReminderActivity.EXTRA_SERVICE_ID, serviceId)
            putExtra(DepartureReminderActivity.EXTRA_SERVICE_LINE, serviceLine)
            putExtra(DepartureReminderActivity.EXTRA_SERVICE_TIME, serviceTime)
            putExtra(DepartureReminderActivity.EXTRA_MINUTES_BEFORE, minutesBefore)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }

        try {
            context.startActivity(activityIntent)
            Log.d(TAG, "DepartureReminderActivity lancée")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lancement DepartureReminderActivity : ${e.message}")

            // Plan B : notification simple
            NotificationHelper.showDepartureReminderNotification(
                context,
                serviceId,
                serviceTime,
                serviceLine,
                minutesBefore
            )
        }
    }

    private fun handleDayBeforeNotification(context: Context, intent: Intent) {
        val serviceId = intent.getStringExtra(EXTRA_SERVICE_ID) ?: return
        val serviceTime = intent.getStringExtra(EXTRA_SERVICE_TIME) ?: ""
        val serviceLine = intent.getStringExtra(EXTRA_SERVICE_LINE) ?: ""
        val serviceDate = intent.getStringExtra(EXTRA_SERVICE_DATE) ?: ""

        // Afficher la notification veille
        NotificationHelper.showDayBeforeNotification(
            context,
            serviceId,
            serviceTime,
            serviceLine,
            serviceDate
        )

        Log.d(TAG, "Notification veille affichée pour service $serviceId")

        // 🆕 CRÉER L'ALARME HORLOGE NATIVE EN MÊME TEMPS
        createClockAlarmForTomorrow(context, serviceTime, serviceLine)
    }

    /**
     * Créer l'alarme horloge native pour demain
     */
    private fun createClockAlarmForTomorrow(context: Context, serviceTime: String, serviceLine: String) {
        try {
            // Vérifier si l'alarme horloge est activée dans les settings
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

            if (userId == null) return

            firestore.collection("users")
                .document(userId)
                .collection("Settings")
                .document("Notifications")
                .get()
                .addOnSuccessListener { doc ->
                    val clockAlarmEnabled = doc.getBoolean("clockAlarmEnabled") ?: false
                    val minutesBefore = doc.getLong("clockAlarmMinutesBefore")?.toInt() ?: 60

                    if (!clockAlarmEnabled) {
                        Log.d(TAG, "Réveil horloge désactivé, skip")
                        return@addOnSuccessListener
                    }

                    // Parser l'heure du service
                    val parts = serviceTime.split(":")
                    if (parts.size != 2) return@addOnSuccessListener

                    val serviceHour = parts[0].toInt()
                    val serviceMinute = parts[1].toInt()

                    // Calculer l'heure d'alarme (demain)
                    val alarmCalendar = java.util.Calendar.getInstance().apply {
                        add(java.util.Calendar.DAY_OF_MONTH, 1) // Demain
                        set(java.util.Calendar.HOUR_OF_DAY, serviceHour)
                        set(java.util.Calendar.MINUTE, serviceMinute)
                        add(java.util.Calendar.MINUTE, -minutesBefore) // X minutes avant
                        set(java.util.Calendar.SECOND, 0)
                    }

                    val alarmHour = alarmCalendar.get(java.util.Calendar.HOUR_OF_DAY)
                    val alarmMinute = alarmCalendar.get(java.util.Calendar.MINUTE)

                    // Créer l'alarme horloge native
                    val alarmIntent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(android.provider.AlarmClock.EXTRA_HOUR, alarmHour)
                        putExtra(android.provider.AlarmClock.EXTRA_MINUTES, alarmMinute)
                        putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "STIB - Ligne $serviceLine ($serviceTime)")
                        putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }

                    context.startActivity(alarmIntent)
                    Log.d(TAG, "✅ Alarme horloge créée : ${String.format("%02d:%02d", alarmHour, alarmMinute)} pour service $serviceLine")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Erreur chargement settings: ${e.message}")
                }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur création alarme horloge: ${e.message}")
        }
    }


    // Fallback pour l'ancienne méthode
    private fun handleLegacyNotification(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE) ?: return
        val serviceId = intent.getStringExtra(EXTRA_SERVICE_ID) ?: return
        val serviceTime = intent.getStringExtra(EXTRA_SERVICE_TIME) ?: ""
        val serviceLine = intent.getStringExtra(EXTRA_SERVICE_LINE) ?: ""

        when (type) {
            TYPE_DAY_BEFORE -> {
                NotificationHelper.showDayBeforeNotification(
                    context, serviceId, serviceTime, serviceLine, ""
                )
            }
            TYPE_DEPARTURE -> {
                val minutesBefore = intent.getIntExtra(EXTRA_MINUTES_BEFORE, 15)

                // Lancer la DepartureReminderActivity directement (copie du code de handleDepartureReminder)
                val activityIntent = Intent(context, DepartureReminderActivity::class.java).apply {
                    putExtra(DepartureReminderActivity.EXTRA_SERVICE_ID, serviceId)
                    putExtra(DepartureReminderActivity.EXTRA_SERVICE_LINE, serviceLine)
                    putExtra(DepartureReminderActivity.EXTRA_SERVICE_TIME, serviceTime)
                    putExtra(DepartureReminderActivity.EXTRA_MINUTES_BEFORE, minutesBefore)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                }

                try {
                    context.startActivity(activityIntent)
                    Log.d(TAG, "DepartureReminderActivity lancée (legacy)")
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur lancement DepartureReminderActivity : ${e.message}")
                    NotificationHelper.showDepartureReminderNotification(
                        context,
                        serviceId,
                        serviceTime,
                        serviceLine,
                        minutesBefore
                    )
                }
            }
            TYPE_APP_ALARM -> {
                // Rediriger vers handleAppAlarm
                val newIntent = Intent(context, NotificationReceiver::class.java).apply {
                    action = ACTION_ALARM
                    putExtra(EXTRA_SERVICE_ID, serviceId)
                    putExtra(EXTRA_SERVICE_TIME, serviceTime)
                    putExtra(EXTRA_SERVICE_LINE, serviceLine)
                }
                handleAppAlarm(context, newIntent)
            }
        }
    }

    companion object {
        private const val TAG = "NotificationReceiver"

        // Actions
        const val ACTION_ALARM = "com.stib.agent.ACTION_ALARM"
        const val ACTION_ALARM_CLOCK = "com.stib.agent.ACTION_ALARM_CLOCK"
        const val ACTION_DEPARTURE_REMINDER = "com.stib.agent.ACTION_DEPARTURE_REMINDER"
        const val ACTION_DAY_BEFORE = "com.stib.agent.ACTION_DAY_BEFORE"

        // Types de notifications (ancienne méthode - rétrocompatibilité)
        const val TYPE_DAY_BEFORE = "day_before"
        const val TYPE_DEPARTURE = "departure"
        const val TYPE_APP_ALARM = "app_alarm"

        // Extras
        const val EXTRA_TYPE = "type"
        const val EXTRA_SERVICE_ID = "service_id"
        const val EXTRA_SERVICE_TIME = "service_time"
        const val EXTRA_SERVICE_LINE = "service_line"
        const val EXTRA_SERVICE_DATE = "service_date"
        const val EXTRA_MINUTES_BEFORE = "minutes_before"
        const val EXTRA_ALARM_NUMBER = "alarm_number"
        const val EXTRA_SOUND_URI = "sound_uri"
        const val EXTRA_ALARM_TIME_MILLIS = "alarm_time_millis"
    }
}
