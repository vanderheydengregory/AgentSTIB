// notifications/ui/AlarmActivity.kt
package com.stib.agent.notifications.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.res.painterResource
import com.stib.agent.R
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.ColorFilter
import android.app.AlarmManager
import android.app.PendingIntent
import com.stib.agent.data.model.Service
import com.stib.agent.notifications.scheduler.*
import android.media.AudioManager
import com.stib.agent.data.model.AlarmType







class AlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var autoSnoozeJob: kotlinx.coroutines.Job? = null

    private var serviceId: String = ""
    private var serviceLine: String = ""
    private var serviceTime: String = ""
    private var alarmNumber: Int = 1
    private var snoozeCount: Int = 0
    private var soundUri: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Récupérer les données de l'intent
        serviceId = intent.getStringExtra(EXTRA_SERVICE_ID) ?: ""
        serviceLine = intent.getStringExtra(EXTRA_SERVICE_LINE) ?: ""
        serviceTime = intent.getStringExtra(EXTRA_SERVICE_TIME) ?: ""
        alarmNumber = intent.getIntExtra(EXTRA_ALARM_NUMBER, 1)
        snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
        soundUri = intent.getStringExtra(EXTRA_SOUND_URI) ?: ""
        // 🆕 DEBUG LOG
        android.util.Log.d("AlarmActivity", "🔊 Son reçu: $soundUri")
        android.util.Log.d("AlarmActivity", "   Vide? ${soundUri.isEmpty()}")
        // Configuration pour afficher par-dessus l'écran de verrouillage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Désactiver le keyguard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        // Démarrer le son et la vibration
        startAlarmSound()
        startVibration()

        // Auto-snooze après 5 minutes
        startAutoSnoozeTimer()

        setContent {
            var showDeleteDialog by remember { mutableStateOf(false) }

            AlarmScreen(
                serviceLine = serviceLine,
                serviceTime = serviceTime,
                snoozeCount = snoozeCount,
                showDeleteDialog = showDeleteDialog,
                onDismiss = { dismissAlarm() },
                onSnooze = { snoozeAlarm() },
                onShowDeleteDialog = {
                    android.util.Log.d("AlarmActivity", "🔘 Dialog demandé")
                    showDeleteDialog = true
                },
                onHideDeleteDialog = { showDeleteDialog = false },
                onDeleteFutureAlarms = { deleteFutureAlarms() }
            )
        }

    }

    private fun startAlarmSound() {
        try {
            // 🆕 Forcer le volume ALARME au maximum
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            val uri = if (soundUri.isNotEmpty()) {
                Uri.parse(soundUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setVolume(1.0f, 1.0f) // Volume max du MediaPlayer
                prepare()
                start()
            }

            android.util.Log.d("AlarmActivity", "🔊 Volume ALARME forcé à ${maxVolume}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun startVibration() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        val pattern = longArrayOf(0, 500, 500, 500, 500) // Vibration forte

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, 0),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null
    }

    private fun startAutoSnoozeTimer() {
        autoSnoozeJob = lifecycleScope.launch {
            delay(5 * 60 * 1000) // 5 minutes
            snoozeAlarm()
        }
    }

    private fun dismissAlarm() {
        autoSnoozeJob?.cancel()
        stopAlarmSound()
        finish()
    }

    private fun snoozeAlarm() {
        if (snoozeCount >= 3) {
            dismissAlarm()
            return
        }

        autoSnoozeJob?.cancel()
        stopAlarmSound()

        // ✅ Programmer le snooze via NotificationReceiver
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val snoozeTimeMillis = System.currentTimeMillis() + (5 * 60 * 1000)

        val intent = Intent(this, com.stib.agent.notifications.receivers.NotificationReceiver::class.java).apply {
            action = "com.stib.agent.ACTION_ALARM"
            putExtra(EXTRA_SERVICE_ID, serviceId)
            putExtra(EXTRA_SERVICE_LINE, serviceLine)
            putExtra(EXTRA_SERVICE_TIME, serviceTime)
            putExtra(EXTRA_ALARM_NUMBER, alarmNumber)
            putExtra(EXTRA_SNOOZE_COUNT, snoozeCount + 1)
            putExtra(EXTRA_SOUND_URI, soundUri)
            // 🆕 Ajouter un flag unique pour différencier du reste
            flags = flags or Intent.FLAG_RECEIVER_FOREGROUND
        }

        // 🆕 RequestCode vraiment unique : utiliser System.currentTimeMillis()
        val requestCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // ✅ Utiliser setAlarmClock pour garantir le déclenchement
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(
                    snoozeTimeMillis,
                    null
                )
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTimeMillis,
                    pendingIntent
                )
            }

            android.util.Log.d("AlarmActivity", "✅ Snooze programmé via AlarmClock dans 5 min (count: ${snoozeCount + 1}, requestCode: $requestCode)")
        } catch (e: Exception) {
            android.util.Log.e("AlarmActivity", "❌ Erreur snooze: ${e.message}", e)
        }

        finish()
    }

    private fun deleteFutureAlarms() {
        lifecycleScope.launch {
            try {
                android.util.Log.d("AlarmActivity", "🗑️ Suppression alarmes APP")

                val service = com.stib.agent.components.ServiceDataManager.getService(serviceId)
                if (service == null) {
                    android.util.Log.e("AlarmActivity", "❌ Service introuvable")
                    dismissAlarm()
                    return@launch
                }

                android.util.Log.d("AlarmActivity", "   📋 ${service.scheduledAlarms.size} alarmes totales")

                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val requestCodeBase = serviceId.hashCode()
                val REQUEST_CODE_ALARM_APP = 1000

                // ✅ Annuler TOUTES les alarmes APP possibles (index 0 à 3)
                var canceledCount = 0
                for (index in 0..3) {
                    try {
                        val intent = Intent(this@AlarmActivity, com.stib.agent.notifications.receivers.NotificationReceiver::class.java).apply {
                            action = "com.stib.agent.ACTION_ALARM"
                            putExtra("service_id", serviceId)
                        }

                        val requestCode = REQUEST_CODE_ALARM_APP + requestCodeBase + (index * 10000)

                        val pendingIntent = PendingIntent.getBroadcast(
                            this@AlarmActivity,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                        )

                        if (pendingIntent != null) {
                            alarmManager.cancel(pendingIntent)
                            pendingIntent.cancel()
                            canceledCount++
                            android.util.Log.d("AlarmActivity", "   ✅ Alarme APP #$index annulée (requestCode: $requestCode)")
                        } else {
                            android.util.Log.d("AlarmActivity", "   ⚠️ Alarme APP #$index n'existe pas (requestCode: $requestCode)")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AlarmActivity", "   ❌ Erreur annulation #$index: ${e.message}")
                    }
                }

                android.util.Log.d("AlarmActivity", "   🗑️ $canceledCount alarmes APP annulées dans AlarmManager")

                // ✅ Filtrer Firestore : garder seulement DEPARTURE et DAY_BEFORE
                val filteredAlarms = service.scheduledAlarms.filter { alarm ->
                    alarm.type == com.stib.agent.data.model.AlarmType.DEPARTURE ||
                            alarm.type == com.stib.agent.data.model.AlarmType.DAY_BEFORE
                }

                android.util.Log.d("AlarmActivity", "   ✅ ${filteredAlarms.size} rappels conservés dans Firestore")

                val success = com.stib.agent.components.ServiceDataManager.updateScheduledAlarms(
                    serviceId,
                    filteredAlarms
                )

                if (success) {
                    android.util.Log.d("AlarmActivity", "✅ Firestore mis à jour")
                }

                android.util.Log.d("AlarmActivity", "✅ Suppression terminée")
                dismissAlarm()

            } catch (e: Exception) {
                android.util.Log.e("AlarmActivity", "❌ Erreur: ${e.message}", e)
                dismissAlarm()
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        autoSnoozeJob?.cancel()
        stopAlarmSound()
    }

    companion object {
        const val EXTRA_SERVICE_ID = "service_id"
        const val EXTRA_SERVICE_LINE = "service_line"
        const val EXTRA_SERVICE_TIME = "service_time"
        const val EXTRA_ALARM_NUMBER = "alarm_number"
        const val EXTRA_SNOOZE_COUNT = "snooze_count"
        const val EXTRA_SOUND_URI = "sound_uri"
    }
}

@Composable
fun AlarmScreen(
serviceLine: String,
serviceTime: String,
snoozeCount: Int,
showDeleteDialog: Boolean,  // 🆕
onDismiss: () -> Unit,
onSnooze: () -> Unit,
onShowDeleteDialog: () -> Unit,  // 🆕
onHideDeleteDialog: () -> Unit,  // 🆕
onDeleteFutureAlarms: () -> Unit
) {
    // Animation de pulsation pour l'icône
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0052A3),
                        Color(0xFF0066CC),
                        Color(0xFF007AFF)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
// Header - Logo de l'application
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 60.dp) // Plus bas
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo2),
                    contentDescription = "Agent STIB",
                    modifier = Modifier
                        .size(120.dp) // Plus grand
                        .clip(CircleShape) // Arrondi
                )
            }




            // Centre - Icône et infos
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Icône réveil avec animation
                Box(
                    modifier = Modifier
                        .size(120.dp * scale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Alarm,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }

                // Infos service
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Service Ligne $serviceLine",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "à $serviceTime",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }

                if (snoozeCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Snooze $snoozeCount/3",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 14.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Bottom - Actions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 40.dp)
            ) {
                // Swipe to dismiss
                SwipeToDismiss(onDismiss = onDismiss)

                // Bouton Snooze
                Button(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.3f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    enabled = snoozeCount < 3
                ) {
                    Icon(
                        Icons.Outlined.Snooze,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (snoozeCount < 3) "Snooze 5 minutes" else "Snooze indisponible",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Bouton supprimer futurs réveils
                TextButton(
                    onClick = {
                        android.util.Log.d("AlarmActivity", "🔘 Bouton cliqué")
                        onShowDeleteDialog()  // 🆕 Appeler le callback
                    },
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Icon(
                        Icons.Filled.AlarmOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Supprimer les prochains réveils",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    // Dialog de confirmation
    if (showDeleteDialog) {
        android.util.Log.d("AlarmActivity", "📋 Dialog affiché")
        DeleteConfirmationDialog(
            onConfirm = {
                android.util.Log.d("AlarmActivity", "✅ Confirmation")
                onHideDeleteDialog()
                onDeleteFutureAlarms()
            },
            onDismiss = {
                android.util.Log.d("AlarmActivity", "❌ Annulation")
                onHideDeleteDialog()
            }
        )
    }
}


@Composable
fun SwipeToDismiss(onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    var offsetX by remember { mutableStateOf(0f) }
    val swipeThreshold = screenWidth * 0.6f

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "swipe"
    )

    LaunchedEffect(offsetX) {
        if (offsetX >= swipeThreshold) {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(35.dp))
            .background(Color.White.copy(alpha = 0.2f))
    ) {
        // Texte de fond
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "GLISSER POUR ARRÊTER",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
        }

        // Bouton glissant
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .padding(4.dp)
                .size(62.dp)
                .clip(CircleShape)
                .background(Color.White)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < swipeThreshold) {
                                offsetX = 0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = (offsetX + dragAmount).coerceIn(0f, screenWidth - 70.dp.toPx())
                            offsetX = newOffset
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Arrêter",
                tint = Color(0xFF0066CC),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.AlarmOff,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Supprimer les prochains réveils ?",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "Cela supprimera uniquement les autres réveils de ce service. Le rappel de départ sera conservé.",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = Color.Black.copy(alpha = 0.7f)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Supprimer", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Color(0xFF0066CC))
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}
