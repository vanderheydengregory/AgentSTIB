package com.stib.agent.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.stib.agent.components.NotificationPreferencesManager
import com.stib.agent.components.ServiceDataManager
import com.stib.agent.data.model.AlarmType
import com.stib.agent.data.model.ScheduledAlarm
import com.stib.agent.data.model.Service
import com.stib.agent.notifications.receivers.NotificationReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

class ServiceAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "ServiceAlarmScheduler"

        // Request codes uniques
        private const val REQUEST_CODE_ALARM_APP = 1000
        private const val REQUEST_CODE_ALARM_CLOCK = 2000
        private const val REQUEST_CODE_DEPARTURE = 3000
        private const val REQUEST_CODE_DAY_BEFORE = 4000
    }

    /**
     * Planifie toutes les alarmes pour un service selon les préférences
     */
    fun scheduleAllAlarmsForService(serviceId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            scheduleAllAlarmsForServiceSuspend(serviceId)
        }
    }

    /**
     * Version coroutine pour planifier les alarmes
     */
    suspend fun scheduleAllAlarmsForServiceSuspend(serviceId: String) {
        Log.d(TAG, "🔄 Planification des alarmes pour service $serviceId")

        // 1. Charger le service
        val service = ServiceDataManager.getService(serviceId)
        if (service == null) {
            Log.e(TAG, "❌ Service $serviceId introuvable")
            return
        }

        if (service.dateService == null || service.partie1Debut == null) {
            Log.e(TAG, "❌ Service invalide (date ou heure manquante)")
            return
        }

        Log.d(TAG, "✅ Service chargé: ${service.serviceNumber}")
        Log.d(TAG, "   Date: ${service.dateService}")
        Log.d(TAG, "   P1: ${service.partie1Debut} → ${service.partie1Fin}")
        if (service.hasPartie2) {
            Log.d(TAG, "   P2: ${service.partie2Debut} → ${service.partie2Fin}")
        }

        // 2. Charger les préférences
        val prefs = NotificationPreferencesManager.getPreferences()
        Log.d(TAG, "✅ Préférences chargées")
        Log.d(TAG, "   App alarm: ${prefs.appAlarmEnabled} (${prefs.appAlarms.size} alarmes)")
        Log.d(TAG, "   Clock: ${prefs.clockAlarmEnabled}")
        Log.d(TAG, "   Départ: ${prefs.departureReminderEnabled} (${prefs.departureReminderMinutesBefore} min)")
        Log.d(TAG, "   Veille: ${prefs.dayBeforeEnabled} (${prefs.dayBeforeHour}h)")

        // 3. Générer les alarmes à partir des préférences
        val scheduledAlarms = mutableListOf<ScheduledAlarm>()

        // Notification de la veille
        if (prefs.dayBeforeEnabled) {
            scheduledAlarms.add(
                ScheduledAlarm(
                    type = AlarmType.DAY_BEFORE,
                    time = prefs.getDayBeforeTimeFormatted(),
                    enabled = true,
                    label = "Veille",
                    icon = "📅"
                )
            )
        }

        // Réveil app (plusieurs alarmes)
        if (prefs.appAlarmEnabled) {
            prefs.appAlarms.forEachIndexed { index, minutesBefore ->
                val alarmDateTime = LocalDateTime.of(service.dateService, service.partie1Debut)
                    .minusMinutes(minutesBefore.toLong())

                scheduledAlarms.add(
                    ScheduledAlarm(
                        type = AlarmType.APP,
                        time = alarmDateTime.toLocalTime().toString(),
                        minutesBefore = minutesBefore,
                        enabled = true,
                        label = "Réveil app ${index + 1}",
                        icon = "🔔"
                    )
                )
            }
        }

        // Horloge native
        if (prefs.clockAlarmEnabled) {
            scheduledAlarms.add(
                ScheduledAlarm(
                    type = AlarmType.CLOCK,
                    time = "Créée la veille",
                    minutesBefore = prefs.clockAlarmMinutesBefore,
                    enabled = true,
                    label = "Horloge native",
                    icon = "⏰"
                )
            )
        }

        // Rappel départ P1
        if (prefs.departureReminderEnabled) {
            val departureDateTime = LocalDateTime.of(service.dateService, service.partie1Debut)
                .minusMinutes(prefs.departureReminderMinutesBefore.toLong())

            scheduledAlarms.add(
                ScheduledAlarm(
                    type = AlarmType.DEPARTURE,
                    time = departureDateTime.toLocalTime().toString(),
                    minutesBefore = prefs.departureReminderMinutesBefore,
                    enabled = true,
                    label = "Départ P1",
                    icon = "🚌"
                )
            )
        }

        // Rappel départ P2 (si existe)
        if (prefs.departureReminderEnabled && service.hasPartie2 && service.partie2Debut != null) {
            val departureDateTime = LocalDateTime.of(service.dateService, service.partie2Debut)
                .minusMinutes(prefs.departureReminderMinutesBefore.toLong())

            scheduledAlarms.add(
                ScheduledAlarm(
                    type = AlarmType.DEPARTURE,
                    time = departureDateTime.toLocalTime().toString(),
                    minutesBefore = prefs.departureReminderMinutesBefore,
                    enabled = true,
                    label = "Départ P2",
                    icon = "🚌"
                )
            )
        }

        Log.d(TAG, "✅ ${scheduledAlarms.size} alarmes générées")

        // 4. Sauvegarder les alarmes dans le service
        ServiceDataManager.updateScheduledAlarms(serviceId, scheduledAlarms)
        Log.d(TAG, "✅ Alarmes sauvegardées dans Firestore")

        // 5. Planifier avec AlarmManager
        scheduleAlarmsWithAlarmManager(service, scheduledAlarms)
    }

    /**
     * Planifie les alarmes avec AlarmManager
     */
    private fun scheduleAlarmsWithAlarmManager(service: Service, alarms: List<ScheduledAlarm>) {
        alarms.forEachIndexed { index, alarm ->
            if (!alarm.enabled) {
                Log.d(TAG, "⏭️ Alarme désactivée: ${alarm.label}")
                return@forEachIndexed
            }

            when (alarm.type) {
                AlarmType.APP -> {
                    if (service.partie1Debut != null && alarm.minutesBefore != null) {
                        scheduleAlarmApp(service, alarm, index)
                    }
                }
                AlarmType.CLOCK -> {
                    Log.d(TAG, "⏰ Horloge native: sera créée la veille")
                }
                AlarmType.DEPARTURE -> {
                    if (alarm.label.contains("P2")) {
                        if (service.hasPartie2 && service.partie2Debut != null && alarm.minutesBefore != null) {
                            scheduleDepartureReminderPartie2(service, alarm)
                        }
                    } else {
                        if (service.partie1Debut != null && alarm.minutesBefore != null) {
                            scheduleDepartureReminder(service, alarm)
                        }
                    }
                }
                AlarmType.DAY_BEFORE -> {
                    scheduleDayBeforeNotification(service, alarm)
                }
            }
        }

        Log.d(TAG, "✅ Alarmes planifiées avec AlarmManager pour service ${service.id}")
    }

    /**
     * Annule toutes les alarmes pour un service
     */
    fun cancelAllAlarmsForService(serviceId: String) {
        Log.d(TAG, "🗑️ Annulation des alarmes pour service $serviceId")

        val requestCodeBase = serviceId.hashCode()

        // Annuler les 4 alarmes app possibles
        for (index in 0..3) {
            cancelAlarm(REQUEST_CODE_ALARM_APP + requestCodeBase + (index * 10000))
        }

        cancelAlarm(REQUEST_CODE_ALARM_CLOCK + requestCodeBase)
        cancelAlarm(REQUEST_CODE_DEPARTURE + requestCodeBase)
        cancelAlarm(REQUEST_CODE_DAY_BEFORE + requestCodeBase)

        // Annuler aussi les alarmes de la partie 2
        val requestCodeBaseP2 = "${serviceId}_p2".hashCode()
        cancelAlarm(REQUEST_CODE_DEPARTURE + requestCodeBaseP2)

        Log.d(TAG, "✅ Alarmes annulées pour service $serviceId")
    }

    /**
     * Replanifie toutes les alarmes (annule puis recrée)
     */
    fun rescheduleAllAlarmsForService(serviceId: String) {
        Log.d(TAG, "🔄 Replanification des alarmes pour service $serviceId")

        // 1. Annuler les anciennes alarmes
        cancelAllAlarmsForService(serviceId)

        // 2. Planifier les nouvelles
        scheduleAllAlarmsForService(serviceId)
    }

    // ========== Réveil App ==========

    private fun scheduleAlarmApp(service: Service, alarm: ScheduledAlarm, index: Int) {
        val serviceDateTime = LocalDateTime.of(service.dateService!!, service.partie1Debut!!)
        val alarmDateTime = serviceDateTime.minusMinutes(alarm.minutesBefore!!.toLong())
        val alarmTimeMillis = alarmDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (alarmTimeMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "⏭️ ${alarm.label} : heure passée, ignoré")
            return
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_ALARM
            putExtra(NotificationReceiver.EXTRA_SERVICE_ID, service.id)
            putExtra(NotificationReceiver.EXTRA_SERVICE_TIME, service.partie1DebutRaw)
            putExtra(NotificationReceiver.EXTRA_SERVICE_LINE, service.partie1Lignes.joinToString(", "))
            putExtra(NotificationReceiver.EXTRA_MINUTES_BEFORE, alarm.minutesBefore)
        }

        val requestCode = REQUEST_CODE_ALARM_APP + service.id.hashCode() + (index * 10000)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactAlarm(alarmTimeMillis, pendingIntent)
        Log.d(TAG, "✅ ${alarm.label} planifié à ${formatTime(alarmTimeMillis)} (${alarm.minutesBefore} min avant)")
    }

    // ========== Rappel de départ Partie 1 ==========

    private fun scheduleDepartureReminder(service: Service, alarm: ScheduledAlarm) {
        val serviceDateTime = LocalDateTime.of(service.dateService!!, service.partie1Debut!!)
        val alarmDateTime = serviceDateTime.minusMinutes(alarm.minutesBefore!!.toLong())
        val alarmTimeMillis = alarmDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (alarmTimeMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "⏭️ Rappel départ P1 : heure passée, ignoré")
            return
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_DEPARTURE_REMINDER
            putExtra(NotificationReceiver.EXTRA_SERVICE_ID, service.id)
            putExtra(NotificationReceiver.EXTRA_SERVICE_TIME, service.partie1DebutRaw)
            putExtra(NotificationReceiver.EXTRA_SERVICE_LINE, service.partie1Lignes.joinToString(", "))
            putExtra(NotificationReceiver.EXTRA_MINUTES_BEFORE, alarm.minutesBefore)
        }

        val requestCode = REQUEST_CODE_DEPARTURE + service.id.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactAlarm(alarmTimeMillis, pendingIntent)
        Log.d(TAG, "✅ Rappel départ P1 planifié à ${formatTime(alarmTimeMillis)}")
    }

    // ========== Rappel de départ Partie 2 ==========

    private fun scheduleDepartureReminderPartie2(service: Service, alarm: ScheduledAlarm) {
        if (!service.hasPartie2 || service.partie2Debut == null) {
            Log.d(TAG, "⏭️ Pas de partie 2, rappel ignoré")
            return
        }

        val serviceDateTime = LocalDateTime.of(service.dateService!!, service.partie2Debut)
        val alarmDateTime = serviceDateTime.minusMinutes(alarm.minutesBefore!!.toLong())
        val alarmTimeMillis = alarmDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (alarmTimeMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "⏭️ Rappel départ P2 : heure passée, ignoré")
            return
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_DEPARTURE_REMINDER
            putExtra(NotificationReceiver.EXTRA_SERVICE_ID, "${service.id}_p2")
            putExtra(NotificationReceiver.EXTRA_SERVICE_TIME, service.partie2DebutRaw)
            putExtra(NotificationReceiver.EXTRA_SERVICE_LINE, service.partie2Lignes?.joinToString(", ") ?: "")
            putExtra(NotificationReceiver.EXTRA_MINUTES_BEFORE, alarm.minutesBefore)
        }

        val requestCode = REQUEST_CODE_DEPARTURE + "${service.id}_p2".hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactAlarm(alarmTimeMillis, pendingIntent)
        Log.d(TAG, "✅ Rappel départ P2 planifié à ${formatTime(alarmTimeMillis)}")
    }

    // ========== Notification de la veille ==========

    private fun scheduleDayBeforeNotification(service: Service, alarm: ScheduledAlarm) {
        if (service.dateService == null) {
            Log.d(TAG, "⏭️ Date invalide, notification veille ignorée")
            return
        }

        try {
            // Extraire l'heure depuis alarm.time (format "HH:mm")
            val timeParts = alarm.time.split(":")
            val hour = timeParts[0].toInt()
            val minute = if (timeParts.size > 1) timeParts[1].toInt() else 0

            val dayBefore = service.dateService.minusDays(1)
            val notificationDateTime = dayBefore.atTime(hour, minute)
            val notificationTimeMillis = notificationDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (notificationTimeMillis <= System.currentTimeMillis()) {
                Log.d(TAG, "⏭️ Notification veille : heure passée, ignoré")
                return
            }

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_DAY_BEFORE
                putExtra(NotificationReceiver.EXTRA_SERVICE_ID, service.id)
                putExtra(NotificationReceiver.EXTRA_SERVICE_TIME, service.partie1DebutRaw)
                putExtra(NotificationReceiver.EXTRA_SERVICE_LINE, service.partie1Lignes.joinToString(", "))
                putExtra(NotificationReceiver.EXTRA_SERVICE_DATE, service.dateServiceRaw)
            }

            val requestCode = REQUEST_CODE_DAY_BEFORE + service.id.hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            scheduleExactAlarm(notificationTimeMillis, pendingIntent)
            Log.d(TAG, "✅ Notification veille planifiée à ${formatTime(notificationTimeMillis)}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur planification notification veille: ${e.message}", e)
        }
    }

    // ========== Utilitaires ==========

    private fun scheduleExactAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun cancelAlarm(requestCode: Int) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun formatTime(millis: Long): String {
        return java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }
}
