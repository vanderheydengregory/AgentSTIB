// settings/NotificationSettings.kt

package com.stib.agent.ui.settings

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stib.agent.components.NotificationPreferencesManager  // 🆕 IMPORT
import com.stib.agent.components.ServiceDataManager  // 🆕 IMPORT
import com.stib.agent.data.model.NotificationPreferences  // 🆕 IMPORT
import com.stib.agent.notifications.utils.TestHelper
import kotlinx.coroutines.launch
import android.app.Activity
import com.stib.agent.notifications.utils.PermissionsHelper
import androidx.compose.material.icons.outlined.NotificationsOff
import com.stib.agent.notifications.ServiceAlarmScheduler
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.text.style.TextAlign
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.tasks.await

data class UpcomingAlarm(
    val date: String,
    val service: String,
    val alarms: List<AlarmDetail>
)

data class AlarmDetail(
    val type: String,
    val icon: String,
    val label: String,
    val time: String
)

// 🆕 GARDER CETTE DATA CLASS POUR L'UI (conversion String/Int)
data class NotificationSettingsData(
    val notificationsEnabled: Boolean = false,
    val dayBeforeEnabled: Boolean = false,
    val dayBeforeHour: String = "09",
    val dayBeforeMinute: String = "00",
    val departureReminderEnabled: Boolean = false,
    val departureReminderMinutesBefore: String = "15",
    val clockAlarmEnabled: Boolean = false,
    val clockAlarmMinutesBefore: String = "60",
    val appAlarmEnabled: Boolean = false,
    val appAlarms: List<String> = listOf("15"),
    val alarmSoundUri: String = ""
) {
    // 🆕 CONVERSION VERS NotificationPreferences
    fun toNotificationPreferences(): NotificationPreferences {
        return NotificationPreferences(
            appAlarmEnabled = appAlarmEnabled,
            appAlarms = appAlarms.mapNotNull { it.toIntOrNull() },
            clockAlarmEnabled = clockAlarmEnabled,
            clockAlarmMinutesBefore = clockAlarmMinutesBefore.toIntOrNull() ?: 60,
            departureReminderEnabled = departureReminderEnabled,
            departureReminderMinutesBefore = departureReminderMinutesBefore.toIntOrNull() ?: 20,
            dayBeforeEnabled = dayBeforeEnabled,
            dayBeforeHour = dayBeforeHour.toIntOrNull() ?: 18,
            dayBeforeMinute = dayBeforeMinute.toIntOrNull() ?: 0,
            alarmSoundUri = alarmSoundUri
        )
    }

    companion object {
        // 🆕 CONVERSION DEPUIS NotificationPreferences
        fun fromNotificationPreferences(prefs: NotificationPreferences): NotificationSettingsData {
            return NotificationSettingsData(
                notificationsEnabled = prefs.hasAnyAlarmEnabled(),
                dayBeforeEnabled = prefs.dayBeforeEnabled,
                dayBeforeHour = prefs.dayBeforeHour.toString().padStart(2, '0'),
                dayBeforeMinute = prefs.dayBeforeMinute.toString().padStart(2, '0'),
                departureReminderEnabled = prefs.departureReminderEnabled,
                departureReminderMinutesBefore = prefs.departureReminderMinutesBefore.toString(),
                clockAlarmEnabled = prefs.clockAlarmEnabled,
                clockAlarmMinutesBefore = prefs.clockAlarmMinutesBefore.toString(),
                appAlarmEnabled = prefs.appAlarmEnabled,
                appAlarms = prefs.appAlarms.map { it.toString() },
                alarmSoundUri = prefs.alarmSoundUri
            )
        }
    }
}

class NotificationSettingsViewModel : ViewModel() {

    private var appContext: Context? = null

    fun setContext(context: Context) {
        appContext = context.applicationContext
        Log.d(TAG, "✅ Context défini")
    }

    var settings by mutableStateOf(NotificationSettingsData())
        private set

    var isLoading by mutableStateOf(true)
        private set

    var saveMessage by mutableStateOf<String?>(null)
        private set
    // 🆕 Variables pour le dialog de test
    var showTestServiceDialog by mutableStateOf(false)
        private set

    companion object {
        private const val TAG = "NotificationSettings"
    }

    init {
        loadSettings()
    }

    // 🆕 CHARGEMENT VIA NotificationPreferencesManager
    fun loadSettings() {
        viewModelScope.launch {
            isLoading = true
            try {
                val prefs = NotificationPreferencesManager.getPreferences()
                settings = NotificationSettingsData.fromNotificationPreferences(prefs)
                Log.d(TAG, "✅ Settings chargés via NotificationPreferencesManager")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur chargement: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    // 🆕 SAUVEGARDE VIA NotificationPreferencesManager
    private fun saveSettings() {
        viewModelScope.launch {
            try {
                val prefs = settings.toNotificationPreferences()
                val success = NotificationPreferencesManager.savePreferences(prefs)

                if (success) {
                    saveMessage = "Enregistré"
                    Log.d(TAG, "💾 Settings sauvegardés, replanification...")
                    rescheduleAllServicesAlarms()
                } else {
                    saveMessage = "Erreur"
                }
            } catch (e: Exception) {
                saveMessage = "Erreur"
                Log.e(TAG, "❌ Erreur sauvegarde: ${e.message}")
            }
        }
    }

    // 🆕 REPLANIFICATION AVEC ServiceDataManager
    private fun rescheduleAllServicesAlarms() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔄 Replanification de toutes les alarmes...")
                val context = appContext ?: run {
                    Log.e(TAG, "❌ Context non disponible")
                    return@launch
                }

                // 🆕 UTILISER ServiceDataManager
                val allServices = ServiceDataManager.getAllServices()
                val scheduler = ServiceAlarmScheduler(context)

                allServices.forEach { service ->
                    try {
                        // Vérifier que le service est futur
                        if (service.dateService != null && service.dateService >= java.time.LocalDate.now()) {
                            scheduler.rescheduleAllAlarmsForService(service.id)
                            Log.d(TAG, "✅ Service ${service.id} replanifié")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur replanification service ${service.id}: ${e.message}")
                    }
                }

                Log.d(TAG, "✅ Toutes les alarmes replanifiées")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur replanification: ${e.message}")
            }
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        settings = settings.copy(
            notificationsEnabled = enabled,
            dayBeforeEnabled = if (!enabled) false else settings.dayBeforeEnabled,
            departureReminderEnabled = if (!enabled) false else settings.departureReminderEnabled,
            clockAlarmEnabled = if (!enabled) false else settings.clockAlarmEnabled,
            appAlarmEnabled = if (!enabled) false else settings.appAlarmEnabled
        )
        saveSettings()
    }

    fun updateDayBeforeEnabled(enabled: Boolean) {
        settings = settings.copy(
            dayBeforeEnabled = enabled,
            clockAlarmEnabled = if (!enabled) false else settings.clockAlarmEnabled
        )
        saveSettings()
    }

    fun updateDayBeforeHour(hour: String) {
        val filtered = hour.filter { it.isDigit() }.take(2)
        settings = settings.copy(dayBeforeHour = filtered)
        saveSettings()
    }

    fun updateDayBeforeMinute(minute: String) {
        val filtered = minute.filter { it.isDigit() }.take(2)
        settings = settings.copy(dayBeforeMinute = filtered)
        saveSettings()
    }

    fun updateDepartureReminderEnabled(enabled: Boolean) {
        settings = settings.copy(departureReminderEnabled = enabled)
        saveSettings()
    }

    fun updateDepartureReminderMinutesBefore(minutes: String) {
        val filtered = minutes.filter { it.isDigit() }.take(3)
        settings = settings.copy(departureReminderMinutesBefore = filtered)
        saveSettings()
    }

    fun updateClockAlarmEnabled(enabled: Boolean) {
        settings = settings.copy(
            clockAlarmEnabled = enabled,
            dayBeforeEnabled = if (enabled) true else settings.dayBeforeEnabled
        )
        saveSettings()
    }

    fun updateClockAlarmMinutesBefore(minutes: String) {
        val filtered = minutes.filter { it.isDigit() }.take(3)
        settings = settings.copy(clockAlarmMinutesBefore = filtered)
        saveSettings()
    }

    fun updateAppAlarmEnabled(enabled: Boolean) {
        settings = settings.copy(appAlarmEnabled = enabled)
        saveSettings()
    }

    fun updateAppAlarm(index: Int, minutes: String) {
        val filtered = minutes.filter { it.isDigit() }.take(3)
        val updatedAlarms = settings.appAlarms.toMutableList()
        if (index < updatedAlarms.size) {
            updatedAlarms[index] = filtered
            settings = settings.copy(appAlarms = updatedAlarms)
            saveSettings()
        }
    }

    fun addAppAlarm() {
        if (settings.appAlarms.size < 5) {
            settings = settings.copy(appAlarms = settings.appAlarms + "15")
            saveSettings()
        }
    }

    fun removeAppAlarm(index: Int) {
        if (settings.appAlarms.size > 1) {
            settings = settings.copy(
                appAlarms = settings.appAlarms.filterIndexed { i, _ -> i != index }
            )
            saveSettings()
        }
    }

    fun updateAlarmSoundUri(uri: String) {
        settings = settings.copy(alarmSoundUri = uri)
        saveSettings()
    }

    fun clearSaveMessage() {
        saveMessage = null
    }

    fun testDayBeforeNotification(context: Context) {
        viewModelScope.launch {
            val success = TestHelper.testDayBeforeNotification(context)
            saveMessage = if (success) "Test dans 30 secondes" else "Erreur"
        }
    }

    fun testAlarmNotification(context: Context) {
        viewModelScope.launch {
            val success = TestHelper.testAlarmNotification(context, settings.alarmSoundUri)
            saveMessage = if (success) "Test dans 30 secondes" else "Erreur"
        }
    }

    fun testDepartureNotification(context: Context) {
        viewModelScope.launch {
            val success = TestHelper.testDepartureNotification(context)
            saveMessage = if (success) "Test dans 30 secondes" else "Erreur"
        }
    }
    fun showTestDialog() {
        showTestServiceDialog = true
    }

    fun hideTestDialog() {
        showTestServiceDialog = false
    }

    // 🆕 Créer un service de test optimal pour tester snooze + suppression
    fun createTestService(context: Context) {
        viewModelScope.launch {
            try {
                val now = LocalDateTime.now()

                // ✅ Service dans 20 minutes
                val serviceTime = now.plusMinutes(20)

                // Créer l'ID du service
                val serviceId = "TEST_${System.currentTimeMillis()}"

                // Créer les données du service au format Firestore
                val serviceData = hashMapOf(
                    "date_service" to serviceTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    "date_import" to now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    "service" to "TEST-999",
                    "partie1" to hashMapOf(
                        "heure_debut" to serviceTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        "heure_fin" to serviceTime.plusMinutes(30).format(DateTimeFormatter.ofPattern("HH:mm")),
                        "lignes" to listOf("TEST"),
                        "bus" to listOf("9999")
                    ),
                    "partie2" to null,
                    "notes" to listOf("⚠️ SERVICE DE TEST - À SUPPRIMER APRÈS UTILISATION"),
                    "scheduledAlarms" to emptyList<Map<String, Any>>()
                )

                // Sauvegarder dans Firestore
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId == null) {
                    saveMessage = "❌ Utilisateur non connecté"
                    return@launch
                }

                val db = FirebaseFirestore.getInstance()
                val success = try {
                    db.collection("users")
                        .document(userId)
                        .collection("services")
                        .document(serviceId)
                        .set(serviceData)
                        .await()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur sauvegarde Firestore", e)
                    false
                }

                if (success) {
                    // ✅ Configurer les préférences pour le test
                    val currentPrefs = NotificationPreferencesManager.getPreferences()
                    val testPrefs = currentPrefs.copy(
                        dayBeforeEnabled = false,  // Désactiver (impossible le jour même)
                        appAlarmEnabled = true,
                        appAlarms = listOf(19, 12),  // 19 min et 12 min avant
                        departureReminderEnabled = true,
                        departureReminderMinutesBefore = 10  // 10 min avant
                    )
                    NotificationPreferencesManager.savePreferences(testPrefs)

                    // Invalider le cache pour recharger les services
                    ServiceDataManager.refreshServices()

                    // Programmer les alarmes
                    val scheduler = ServiceAlarmScheduler(context)
                    scheduler.scheduleAllAlarmsForService(serviceId)

                    // Restaurer les vraies préférences
                    NotificationPreferencesManager.savePreferences(currentPrefs)

                    val alarmTime1 = now.plusMinutes(1).format(DateTimeFormatter.ofPattern("HH:mm"))
                    val alarmTime2 = now.plusMinutes(8).format(DateTimeFormatter.ofPattern("HH:mm"))
                    val departureTime = now.plusMinutes(10).format(DateTimeFormatter.ofPattern("HH:mm"))
                    val serviceTimeStr = serviceTime.format(DateTimeFormatter.ofPattern("HH:mm"))

                    saveMessage = """✅ Service test créé !

📋 SCÉNARIO DE TEST :

1️⃣ $alarmTime1 - Réveil APP 1 sonne
   → Testez le SNOOZE (5 min)

2️⃣ ${now.plusMinutes(6).format(DateTimeFormatter.ofPattern("HH:mm"))} - Réveil 1 re-sonne
   → Testez SUPPRESSION alarmes APP

3️⃣ $alarmTime2 - Réveil APP 2 ne sonne PAS ✅

4️⃣ $departureTime - Rappel départ sonne ✅

⏰ Service : $serviceTimeStr

⚠️ Supprimez le service TEST après !"""

                    Log.d(TAG, "✅ Service test créé : $serviceId")
                    Log.d(TAG, "   🔔 Réveil APP 1 : $alarmTime1 (19 min avant)")
                    Log.d(TAG, "   🔔 Réveil APP 2 : $alarmTime2 (12 min avant)")
                    Log.d(TAG, "   🚌 Rappel départ : $departureTime (10 min avant)")
                    Log.d(TAG, "   ⏰ Service démarre : $serviceTimeStr")
                } else {
                    saveMessage = "❌ Erreur création service"
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur création service test", e)
                saveMessage = "❌ Erreur : ${e.message}"
            }
        }
    }

}

@Composable
fun HelpDialog(
    type: String,
    onDismiss: () -> Unit
) {
    val (title, description) = when (type) {
        "day_before" -> Pair(
            "Notification la veille",
            "Recevez une notification la veille de votre service à l'heure que vous choisissez. Cela vous permet de vous préparer à l'avance et de ne pas oublier votre service.\n\n⚠️ Si vous désactivez cette notification, le réveil téléphone sera automatiquement désactivé."
        )

        "departure" -> Pair(
            "Rappel de départ",
            "Une notification vous sera envoyée quelques minutes avant l'heure de départ de votre service. Cela vous donne le temps de vous préparer et de partir à l'heure."
        )
        "clock" -> Pair(
            "Réveil téléphone",
            "Crée une alarme dans l'application Horloge native de votre téléphone.\n\n⚠️ Important :\n• L'alarme est créée la veille lors de la notification\n• La notification de la veille sera automatiquement activée\n• Pensez à supprimer manuellement les alarmes dans l'app Horloge"
        )

        "app_alarm" -> Pair(
            "Réveil dans l'app",
            "Alarme sonore gérée directement par Agent STIB. Vous pouvez configurer jusqu'à 5 réveils différents qui sonneront X minutes avant votre service. Ces alarmes sont supprimées automatiquement après utilisation."
        )
        else -> Pair("", "")
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color(0xFF0066CC),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1F2121)
                    )
                }

                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color(0xFF627C7D),
                    lineHeight = 20.sp
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0066CC)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Compris", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun CategoryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onHelpClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFFE5E7EB)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (enabled)
                                    Color(0xFF10B981).copy(alpha = 0.15f)
                                else
                                    Color(0xFFF3F4F6)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (enabled) Color(0xFF10B981) else Color(0xFF9CA3AF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) Color(0xFF1F2121) else Color(0xFF9CA3AF)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onHelpClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = "Aide",
                            tint = Color(0xFF0066CC),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFD1D5DB)
                        )
                    )
                }
            }

            if (enabled) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val filtered = newValue.filter { it.isDigit() }.take(3)
            onValueChange(filtered)
        },
        modifier = Modifier
            .width(70.dp)
            .height(48.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        placeholder = {
            Text(placeholder, fontSize = 13.sp, color = Color(0xFFCCCCCC))
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF0066CC),
            unfocusedBorderColor = Color(0xFFE0E0E0),
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        ),
        shape = RoundedCornerShape(6.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    )
}

@Composable
fun TestButton(
    onClick: () -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0066CC),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
@Composable
fun AlarmItemRow(
    icon: String,
    label: String,
    time: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                icon,
                fontSize = 18.sp
            )
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF134252)
            )
        }
        Text(
            time,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0066CC)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettings(
    viewModel: NotificationSettingsViewModel = viewModel()  // ✅ SIMPLE
) {
    val context = LocalContext.current

    // 🆕 Passer le context au ViewModel
    LaunchedEffect(Unit) {
        viewModel.setContext(context)
    }

    val scrollState = rememberScrollState()
    var showHelpDialog by remember { mutableStateOf<String?>(null) }
    // Dialog de confirmation pour service test
// Dialog de confirmation pour service test
    if (viewModel.showTestServiceDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideTestDialog() },
            icon = {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Créer un service de test ?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "⚠️ Ceci va créer un VRAI service dans votre planning avec des alarmes qui se déclencheront.",
                        fontSize = 14.sp,
                        color = Color(0xFF92400E),
                        lineHeight = 20.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF3CD)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "📋 Scénario de test (20 min) :",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                "1️⃣ Dans 1 min → Réveil APP 1",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                "2️⃣ Testez SNOOZE (5 min)",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                "3️⃣ Dans 6 min → Réveil re-sonne",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                "4️⃣ Supprimez les alarmes APP",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                "5️⃣ Dans 8 min → Réveil APP 2 ne sonne PAS ✅",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                "6️⃣ Dans 10 min → Rappel départ sonne ✅",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                    }

                    Text(
                        text = "📝 N'oubliez pas de SUPPRIMER le service TEST après !",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEF4444),
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.hideTestDialog()
                        viewModel.createTestService(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0066CC)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Créer le service test", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideTestDialog() }) {
                    Text("Annuler", color = Color(0xFF627C7D))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }


    var showAlarmsList by remember { mutableStateOf(false) }
    var upcomingAlarms by remember { mutableStateOf<List<UpcomingAlarm>>(emptyList()) }
    var isLoadingAlarms by remember { mutableStateOf(false) }
// Charger depuis Firestore quand on ouvre la liste
    LaunchedEffect(showAlarmsList) {
        if (showAlarmsList) {
            isLoadingAlarms = true
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("scheduledAlarms")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val alarms = snapshot.documents.mapNotNull { doc ->
                            val date = doc.getString("date") ?: return@mapNotNull null
                            val service = doc.getString("service") ?: return@mapNotNull null
                            @Suppress("UNCHECKED_CAST")
                            val alarmsList = (doc.get("alarms") as? List<Map<String, String>>)?.map {
                                AlarmDetail(
                                    type = it["type"] ?: "",
                                    icon = it["icon"] ?: "",
                                    label = it["label"] ?: "",
                                    time = it["time"] ?: ""
                                )
                            } ?: emptyList()

                            UpcomingAlarm(date, service, alarmsList)
                        }.sortedBy { alarm ->
                            val parts = alarm.date.split("/")
                            if (parts.size == 3) {
                                java.time.LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                            } else {
                                java.time.LocalDate.MIN
                            }
                        }

                        upcomingAlarms = alarms
                        isLoadingAlarms = false
                    }
                    .addOnFailureListener {
                        isLoadingAlarms = false
                    }
            } else {
                isLoadingAlarms = false
            }
        }
    }


    // Launcher pour choisir le son
    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { uri ->
            viewModel.updateAlarmSoundUri(uri.toString())
        }
    }

    LaunchedEffect(viewModel.saveMessage) {
        viewModel.saveMessage?.let {
            kotlinx.coroutines.delay(1500)
            viewModel.clearSaveMessage()
        }
    }

    // Dialog d'aide
    showHelpDialog?.let { helpType ->
        HelpDialog(
            type = helpType,
            onDismiss = { showHelpDialog = null }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFCF9))
    ) {
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF0066CC))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Ligne principale : Notifications ON/OFF
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (viewModel.settings.notificationsEnabled)
                                            Color(0xFF10B981).copy(alpha = 0.15f)
                                        else
                                            Color(0xFFE5E7EB)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (viewModel.settings.notificationsEnabled)
                                        Color(0xFF10B981)
                                    else
                                        Color(0xFF9CA3AF),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Notifications",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1F2121)
                                )
                                Text(
                                    text = if (viewModel.settings.notificationsEnabled) "Activées" else "Désactivées",
                                    fontSize = 12.sp,
                                    color = Color(0xFF627C7D)
                                )
                            }
                        }

                        Switch(
                            checked = viewModel.settings.notificationsEnabled,
                            onCheckedChange = { viewModel.updateNotificationsEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFD1D5DB)
                            )
                        )
                    }
                }

                // Si notifications activées, afficher les options
                if (viewModel.settings.notificationsEnabled) {
                    // Vérification des permissions
                    val hasOverlayPermission = remember {
                        mutableStateOf(PermissionsHelper.hasOverlayPermission(context))
                    }

                    if (!hasOverlayPermission.value) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFF3CD),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(24.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Permission requise",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF92400E)
                                    )
                                    Text(
                                        text = "Pour afficher les réveils en plein écran",
                                        fontSize = 12.sp,
                                        color = Color(0xFF92400E)
                                    )
                                }

                                Button(
                                    onClick = {
                                        PermissionsHelper.requestOverlayPermission(context as android.app.Activity)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF59E0B)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    )
                                ) {
                                    Text(
                                        "Autoriser",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    val hasDndPermission = PermissionsHelper.hasDoNotDisturbPermission(context)


                    if (!hasDndPermission) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFDCFCE7),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsOff,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(24.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Mode Ne Pas Déranger",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF065F46)
                                    )
                                    Text(
                                        text = "Pour que les réveils sonnent même en mode silencieux",
                                        fontSize = 12.sp,
                                        color = Color(0xFF065F46)
                                    )
                                }

                                Button(
                                    onClick = {
                                        PermissionsHelper.requestDoNotDisturbPermission(context as android.app.Activity)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF059669)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    )
                                ) {
                                    Text(
                                        "Autoriser",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    // 1. Notification la veille
                    CategoryCard(
                        icon = Icons.Outlined.EventNote,
                        title = "Notification la veille",
                        enabled = viewModel.settings.dayBeforeEnabled,
                        onEnabledChange = { viewModel.updateDayBeforeEnabled(it) },
                        onHelpClick = { showHelpDialog = "day_before" }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Heure :", fontSize = 14.sp, color = Color(0xFF627C7D))
                            SmallNumberField(
                                value = viewModel.settings.dayBeforeHour,
                                onValueChange = { viewModel.updateDayBeforeHour(it) },
                                placeholder = "09"
                            )
                            Text(":", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            SmallNumberField(
                                value = viewModel.settings.dayBeforeMinute,
                                onValueChange = { viewModel.updateDayBeforeMinute(it) },
                                placeholder = "00"
                            )
                        }
                    }

                    // 2. Rappel de départ
                    CategoryCard(
                        icon = Icons.Outlined.DirectionsRun,
                        title = "Rappel de départ",
                        enabled = viewModel.settings.departureReminderEnabled,
                        onEnabledChange = { viewModel.updateDepartureReminderEnabled(it) },
                        onHelpClick = { showHelpDialog = "departure" }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmallNumberField(
                                value = viewModel.settings.departureReminderMinutesBefore,
                                onValueChange = { viewModel.updateDepartureReminderMinutesBefore(it) },
                                placeholder = "15"
                            )
                            Text(
                                "minutes avant le départ",
                                fontSize = 14.sp,
                                color = Color(0xFF627C7D)
                            )
                        }
                    }

                    // 3. Réveil téléphone
                    CategoryCard(
                        icon = Icons.Outlined.PhoneAndroid,
                        title = "Réveil téléphone",
                        enabled = viewModel.settings.clockAlarmEnabled,
                        onEnabledChange = { viewModel.updateClockAlarmEnabled(it) },
                        onHelpClick = { showHelpDialog = "clock" }
                    ) {
                        // 🆕 WARNING SI ACTIVÉ
                        if (viewModel.settings.clockAlarmEnabled) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFDCFCE7)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("ℹ️", fontSize = 16.sp)
                                    Text(
                                        "L'alarme sera créée lors de la notification de la veille",
                                        fontSize = 12.sp,
                                        color = Color(0xFF065F46),
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                        }

                        // Le reste du contenu (choix des minutes)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmallNumberField(
                                value = viewModel.settings.clockAlarmMinutesBefore,
                                onValueChange = { viewModel.updateClockAlarmMinutesBefore(it) },
                                placeholder = "60"
                            )
                            Text("minutes avant le service", fontSize = 14.sp, color = Color(0xFF627C7D))
                        }
                    }

                    // 4. Réveil dans l'app
                    CategoryCard(
                        icon = Icons.Outlined.Alarm,
                        title = "Réveil dans l'app",
                        enabled = viewModel.settings.appAlarmEnabled,
                        onEnabledChange = { viewModel.updateAppAlarmEnabled(it) },
                        onHelpClick = { showHelpDialog = "app_alarm" }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Liste des réveils
                            viewModel.settings.appAlarms.forEachIndexed { index, minutes ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Réveil ${index + 1} :",
                                        fontSize = 14.sp,
                                        color = Color(0xFF627C7D)
                                    )
                                    SmallNumberField(
                                        value = minutes,
                                        onValueChange = { viewModel.updateAppAlarm(index, it) },
                                        placeholder = "15"
                                    )
                                    Text("min avant", fontSize = 14.sp, color = Color(0xFF627C7D))

                                    if (viewModel.settings.appAlarms.size > 1) {
                                        IconButton(
                                            onClick = { viewModel.removeAppAlarm(index) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Supprimer",
                                                tint = Color(0xFF999999),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (viewModel.settings.appAlarms.size < 5) {
                                TextButton(
                                    onClick = { viewModel.addAppAlarm() },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Ajouter un réveil (${viewModel.settings.appAlarms.size}/5)",
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Divider(
                                color = Color(0xFFE0E0E0),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            // Choix du son
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Son du réveil",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1F2121)
                                    )
                                    Text(
                                        text = if (viewModel.settings.alarmSoundUri.isEmpty())
                                            "Son par défaut"
                                        else
                                            "Son personnalisé",
                                        fontSize = 12.sp,
                                        color = Color(0xFF627C7D)
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val intent =
                                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                putExtra(
                                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                    RingtoneManager.TYPE_ALARM
                                                )
                                                putExtra(
                                                    RingtoneManager.EXTRA_RINGTONE_TITLE,
                                                    "Choisir un son"
                                                )
                                                putExtra(
                                                    RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,
                                                    false
                                                )
                                                putExtra(
                                                    RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,
                                                    true
                                                )
                                                if (viewModel.settings.alarmSoundUri.isNotEmpty()) {
                                                    putExtra(
                                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                        Uri.parse(viewModel.settings.alarmSoundUri)
                                                    )
                                                }
                                            }
                                        soundPickerLauncher.launch(intent)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF0066CC)
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Choisir", fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color(0xFFE0E0E0)
                    )

// Section Tests
                    Text(
                        text = "Tester les notifications",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1F2121)
                    )

                    Button(
                        onClick = { viewModel.showTestDialog() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0066CC)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Créer un service de test complet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TestButton(
                            onClick = { viewModel.testDayBeforeNotification(context) },
                            label = "Veille",
                            icon = Icons.Outlined.EventNote,
                            modifier = Modifier.weight(1f)
                        )
                        TestButton(
                            onClick = { viewModel.testAlarmNotification(context) },
                            label = "Réveil",
                            icon = Icons.Outlined.Alarm,
                            modifier = Modifier.weight(1f)
                        )
                        TestButton(
                            onClick = { viewModel.testDepartureNotification(context) },
                            label = "Départ",
                            icon = Icons.Outlined.DirectionsRun,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Toast de sauvegarde
        viewModel.saveMessage?.let { message ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier.padding(bottom = 24.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        message.startsWith("Test") -> Color(0xFF0066CC)
                        message == "Enregistré" -> Color(0xFF10B981)
                        else -> Color(0xFFEF4444)
                    },
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = message,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (message.startsWith("Test")) {
                            Text(
                                text = "Verrouillez votre téléphone",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}