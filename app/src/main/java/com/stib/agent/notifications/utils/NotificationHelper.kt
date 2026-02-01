// notifications/utils/NotificationHelper.kt
package com.stib.agent.notifications.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.stib.agent.MainActivity

object NotificationHelper {

    // Canaux de notification
    private const val CHANNEL_DAY_BEFORE = "day_before_channel"
    private const val CHANNEL_DEPARTURE = "departure_channel"
    private const val CHANNEL_ALARM = "alarm_channel"

    /**
     * Créer tous les canaux de notification nécessaires
     * À appeler au démarrage de l'app
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)

            // Canal : Notification la veille
            val dayBeforeChannel = NotificationChannel(
                CHANNEL_DAY_BEFORE,
                "Notification la veille",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Rappel la veille de votre service"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }

            // Canal : Rappel de départ
            val departureChannel = NotificationChannel(
                CHANNEL_DEPARTURE,
                "Rappel de départ",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification avant l'heure de départ"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }

            // Canal : Alarmes/Réveils
            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM,
                "Réveils",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarmes sonores pour vos services"
                enableVibration(true)
                enableLights(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }

            notificationManager.createNotificationChannel(dayBeforeChannel)
            notificationManager.createNotificationChannel(departureChannel)
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    /**
     * Afficher une notification "la veille" (avec date)
     */
    fun showDayBeforeNotification(
        context: Context,
        serviceId: String,
        serviceTime: String,
        serviceLine: String,
        serviceDate: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("service_id", serviceId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            serviceId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = if (serviceDate.isNotEmpty()) {
            "Votre service débute le $serviceDate à $serviceTime sur la ligne $serviceLine"
        } else {
            "Votre service débute demain à $serviceTime sur la ligne $serviceLine"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_DAY_BEFORE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📅 Service demain")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(serviceId.hashCode() + 4000, notification)
    }

    /**
     * Afficher une notification de rappel de départ
     */
    fun showDepartureReminderNotification(
        context: Context,
        serviceId: String,
        serviceTime: String,
        serviceLine: String,
        minutesBefore: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("service_id", serviceId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            serviceId.hashCode() + 1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DEPARTURE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🚀 Départ dans $minutesBefore minutes !")
            .setContentText("Service à $serviceTime sur la ligne $serviceLine")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Il est temps de partir ! Votre service débute à $serviceTime sur la ligne $serviceLine."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(serviceId.hashCode() + 1000, notification)
    }

    /**
     * Afficher une notification full screen pour le réveil (Android 10+)
     */
    fun showFullScreenAlarmNotification(
        context: Context,
        serviceId: String,
        serviceTime: String,
        serviceLine: String,
        alarmNumber: Int,
        fullScreenIntent: PendingIntent
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏰ Réveil Agent STIB")
            .setContentText("Service à $serviceTime sur la ligne $serviceLine")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(serviceId.hashCode() + 2000 + alarmNumber, notification)
    }
}
