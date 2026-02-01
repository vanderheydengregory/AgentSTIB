// notifications/utils/TestHelper.kt
package com.stib.agent.notifications.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.stib.agent.notifications.receivers.NotificationReceiver
import java.util.*

object TestHelper {

    private const val TAG = "TestHelper"
    private const val TEST_DELAY_SECONDS = 30

    /**
     * Tester la notification "la veille"
     */
    fun testDayBeforeNotification(context: Context): Boolean {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val triggerTime = Calendar.getInstance().apply {
                add(Calendar.SECOND, TEST_DELAY_SECONDS)
            }

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra(NotificationReceiver.EXTRA_TYPE, NotificationReceiver.TYPE_DAY_BEFORE)
                putExtra(NotificationReceiver.EXTRA_SERVICE_ID, "test_day_before")
                putExtra(NotificationReceiver.EXTRA_SERVICE_TIME, "06:00")
                putExtra(NotificationReceiver.EXTRA_SERVICE_LINE, "4")
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                99991, // ID unique pour les tests
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime.timeInMillis,
                pendingIntent
            )

            Log.d(TAG, "Test notification veille programmé pour ${Date(triggerTime.timeInMillis)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur test notification veille", e)
            false
        }
    }

    /**
     * Tester le réveil dans l'app
     */
    fun testAlarmNotification(context: Context, soundUri: String): Boolean {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val triggerTime = Calendar.getInstance().apply {
                add(Calendar.SECOND, TEST_DELAY_SECONDS)
            }

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra(NotificationReceiver.EXTRA_TYPE, NotificationReceiver.TYPE_APP_ALARM)
                putExtra(NotificationReceiver.EXTRA_SERVICE_ID, "test_alarm")
                putExtra(NotificationReceiver.EXTRA_SERVICE_TIME, "06:00")
                putExtra(NotificationReceiver.EXTRA_SERVICE_LINE, "4")
                putExtra(NotificationReceiver.EXTRA_ALARM_NUMBER, 1)
                putExtra(NotificationReceiver.EXTRA_SOUND_URI, soundUri)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                99992, // ID unique pour les tests
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime.timeInMillis,
                pendingIntent
            )

            Log.d(TAG, "Test réveil app programmé pour ${Date(triggerTime.timeInMillis)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur test réveil app", e)
            false
        }
    }

    /**
     * Tester le rappel de départ
     */
    fun testDepartureNotification(context: Context): Boolean {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val triggerTime = Calendar.getInstance().apply {
                add(Calendar.SECOND, TEST_DELAY_SECONDS)
            }

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra(NotificationReceiver.EXTRA_TYPE, NotificationReceiver.TYPE_DEPARTURE)
                putExtra(NotificationReceiver.EXTRA_SERVICE_ID, "test_departure")
                putExtra(NotificationReceiver.EXTRA_SERVICE_TIME, "06:00")
                putExtra(NotificationReceiver.EXTRA_SERVICE_LINE, "4")
                putExtra(NotificationReceiver.EXTRA_MINUTES_BEFORE, 15)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                99993, // ID unique pour les tests
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime.timeInMillis,
                pendingIntent
            )

            Log.d(TAG, "Test rappel départ programmé pour ${Date(triggerTime.timeInMillis)}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur test rappel départ", e)
            false
        }
    }
}
