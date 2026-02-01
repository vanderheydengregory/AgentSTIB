// notifications/schedulers/AppAlarmScheduler.kt
package com.stib.agent.notifications.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.stib.agent.notifications.receivers.NotificationReceiver
import java.util.*

object AppAlarmScheduler {

    private const val TAG = "AppAlarmScheduler"

    /**
     * Programmer un réveil app
     */
    fun scheduleAlarm(
        context: Context,
        serviceId: String,
        serviceTime: Calendar,
        serviceLine: String,
        minutesBefore: Int,
        alarmNumber: Int,
        soundUri: String
    ): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Vérifier les permissions (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e(TAG, "Permission SCHEDULE_EXACT_ALARM non accordée")
                // Rediriger vers les paramètres
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return false
            }
        }

        // Calculer l'heure de déclenchement
        val triggerTime = serviceTime.clone() as Calendar
        triggerTime.add(Calendar.MINUTE, -minutesBefore)

        // Vérifier que c'est dans le futur
        if (triggerTime.timeInMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "L'heure de réveil est dans le passé, ignoré")
            return false
        }

        // Créer l'intent
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(NotificationReceiver.EXTRA_TYPE, NotificationReceiver.TYPE_APP_ALARM)
            putExtra(NotificationReceiver.EXTRA_SERVICE_ID, serviceId)
            putExtra(NotificationReceiver.EXTRA_SERVICE_TIME, formatTime(serviceTime))
            putExtra(NotificationReceiver.EXTRA_SERVICE_LINE, serviceLine)
            putExtra(NotificationReceiver.EXTRA_ALARM_NUMBER, alarmNumber)
            putExtra(NotificationReceiver.EXTRA_SOUND_URI, soundUri)
        }

        // Créer un ID unique pour ce réveil
        val requestCode = generateRequestCode(serviceId, alarmNumber)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Programmer l'alarme
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime.timeInMillis,
                pendingIntent
            )

            Log.d(TAG, "Réveil $alarmNumber programmé pour ${Date(triggerTime.timeInMillis)} (service $serviceId)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la programmation du réveil", e)
            return false
        }
    }

    /**
     * Annuler un réveil spécifique
     */
    fun cancelAlarm(
        context: Context,
        serviceId: String,
        alarmNumber: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NotificationReceiver::class.java)
        val requestCode = generateRequestCode(serviceId, alarmNumber)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Réveil $alarmNumber annulé pour service $serviceId")
        }
    }

    /**
     * Annuler tous les réveils d'un service
     */
    fun cancelAllAlarmsForService(
        context: Context,
        serviceId: String,
        maxAlarms: Int = 5
    ) {
        for (i in 1..maxAlarms) {
            cancelAlarm(context, serviceId, i)
        }
        Log.d(TAG, "Tous les réveils annulés pour service $serviceId")
    }

    /**
     * Programmer tous les réveils pour un service
     */
    fun scheduleAllAlarms(
        context: Context,
        serviceId: String,
        serviceTime: Calendar,
        serviceLine: String,
        minutesBeforeList: List<Int>,
        soundUri: String
    ): Int {
        var successCount = 0

        minutesBeforeList.forEachIndexed { index, minutesBefore ->
            val alarmNumber = index + 1
            if (scheduleAlarm(
                    context,
                    serviceId,
                    serviceTime,
                    serviceLine,
                    minutesBefore,
                    alarmNumber,
                    soundUri
                )) {
                successCount++
            }
        }

        Log.d(TAG, "$successCount réveils programmés sur ${minutesBeforeList.size} pour service $serviceId")
        return successCount
    }

    /**
     * Générer un request code unique basé sur serviceId et alarmNumber
     */
    private fun generateRequestCode(serviceId: String, alarmNumber: Int): Int {
        // Utiliser les 3 premiers caractères du serviceId + alarmNumber
        val serviceHash = serviceId.take(3).hashCode() and 0x0FFFFFFF
        return (serviceHash * 10) + alarmNumber
    }

    /**
     * Formater l'heure en HH:mm
     */
    private fun formatTime(calendar: Calendar): String {
        return String.format(
            Locale.getDefault(),
            "%02d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
    }
}
