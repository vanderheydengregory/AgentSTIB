package com.stib.agent.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stib.agent.utils.CalendarSyncUtil
import com.stib.agent.utils.CalendarInfo
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun CalendarSettings() {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val coroutineScope = rememberCoroutineScope()

    // Calendar settings
    val selectedCalendarId = remember { mutableStateOf(-1L) }
    val autoSyncEnabled = remember { mutableStateOf(false) }
    val availableCalendars = remember { mutableStateOf(emptyList<CalendarInfo>()) }
    val showCalendarDropdown = remember { mutableStateOf(false) }

    // 🔄 Migration states
    val showMigrationDialog = remember { mutableStateOf(false) }
    val oldCalendarId = remember { mutableStateOf(-1L) }
    val isMigrating = remember { mutableStateOf(false) }
    val migrationMessage = remember { mutableStateOf("") }

    // 🔄 Reset states
    val showResetDialog = remember { mutableStateOf(false) }
    val isResetting = remember { mutableStateOf(false) }
    val resetMessage = remember { mutableStateOf("") }

    val isLoading = remember { mutableStateOf(true) }
    val saveMessage = remember { mutableStateOf("") }

    // ✅ AUTOSAVE + SYNC CALENDRIER
    LaunchedEffect(selectedCalendarId.value, autoSyncEnabled.value) {
        if (!isLoading.value && currentUser != null) {
            delay(500)

            val calendarData = mapOf(
                "calendarId" to selectedCalendarId.value,
                "autoSyncEnabled" to autoSyncEnabled.value
            )

            db.collection("users")
                .document(currentUser.uid)
                .collection("Settings")
                .document("Calendrier")
                .set(calendarData)
                .addOnSuccessListener {
                    saveMessage.value = "✅ Sauvegarde automatique"

                    // 🔄 SYNCHRONISER LE CALENDRIER ICI
                    if (selectedCalendarId.value > 0 && autoSyncEnabled.value) {
                        Log.d("CalendarSettings", "🔄 Déclenchement syncAllServicesOnStartup")
                        CalendarSyncUtil.syncAllServicesOnStartup(
                            context = context,
                            userId = currentUser.uid,
                            calendarId = selectedCalendarId.value
                        )
                    }

                    coroutineScope.launch {
                        delay(2000)
                        saveMessage.value = ""
                    }
                }
        }
    }

    LaunchedEffect(Unit) {
        availableCalendars.value = CalendarSyncUtil.getAvailableCalendars(context)
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            try {
                val calendarSettings = db.collection("users")
                    .document(currentUser.uid)
                    .collection("Settings")
                    .document("Calendrier")
                    .get()
                    .await()

                selectedCalendarId.value = calendarSettings.getLong("calendarId") ?: -1L
                oldCalendarId.value = selectedCalendarId.value
                autoSyncEnabled.value = calendarSettings.getBoolean("autoSyncEnabled") ?: false

                isLoading.value = false
            } catch (e: Exception) {
                Log.e("CalendarSettings", "Erreur chargement: ${e.message}")
                isLoading.value = false
            }
        }
    }

    // 🔄 DÉTECTION DE CHANGEMENT DE CALENDRIER
    LaunchedEffect(selectedCalendarId.value) {
        if (selectedCalendarId.value > 0 && currentUser != null) {
            if (oldCalendarId.value > 0 && oldCalendarId.value != selectedCalendarId.value) {
                Log.d("CalendarSettings", "🔄 Changement de calendrier détecté")
                showMigrationDialog.value = true
            } else if (oldCalendarId.value == -1L) {
                oldCalendarId.value = selectedCalendarId.value
            }
        }
    }

    // 🔄 DIALOG DE MIGRATION
    if (showMigrationDialog.value) {
        AlertDialog(
            onDismissRequest = { if (!isMigrating.value) showMigrationDialog.value = false },
            title = { Text("🔄 Changement de calendrier") },
            text = {
                Column {
                    Text("Vous changez de calendrier.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tous vos événements STIB seront transférés vers le nouveau calendrier.")
                    Spacer(modifier = Modifier.height(16.dp))
                    if (migrationMessage.value.isNotEmpty()) {
                        Text(migrationMessage.value, fontWeight = FontWeight.Bold, color = Color(0xFF0066CC))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isMigrating.value = true
                        migrationMessage.value = "⏳ Migration en cours..."

                        CalendarSyncUtil.migrateToNewCalendar(
                            context = context,
                            userId = currentUser!!.uid,
                            oldCalendarId = oldCalendarId.value,
                            newCalendarId = selectedCalendarId.value
                        ) { success, message ->
                            migrationMessage.value = message
                            isMigrating.value = false

                            if (success) {
                                oldCalendarId.value = selectedCalendarId.value
                                coroutineScope.launch {
                                    delay(2000)
                                    showMigrationDialog.value = false
                                    migrationMessage.value = ""
                                }
                            }
                        }
                    },
                    enabled = !isMigrating.value,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))
                ) {
                    Text(if (isMigrating.value) "⏳ Migration..." else "✅ Migrer")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMigrationDialog.value = false
                        selectedCalendarId.value = oldCalendarId.value
                    },
                    enabled = !isMigrating.value
                ) {
                    Text("Annuler")
                }
            }
        )
    }

    // 🔄 DIALOG DE RESET
    if (showResetDialog.value) {
        AlertDialog(
            onDismissRequest = { if (!isResetting.value) showResetDialog.value = false },
            title = { Text("⚠️ Reset des synchronisations") },
            text = {
                Column {
                    Text("Êtes-vous sûr? Cela va:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Supprimer tous les eventId")
                    Text("• Les événements seront recréés au prochain démarrage")
                    Spacer(modifier = Modifier.height(16.dp))
                    if (resetMessage.value.isNotEmpty()) {
                        Text(resetMessage.value, fontWeight = FontWeight.Bold, color = Color(0xFF0066CC))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isResetting.value = true
                        resetMessage.value = "⏳ Reset en cours..."

                        CalendarSyncUtil.resetEventIdsForAllServices(currentUser!!.uid) { count ->
                            resetMessage.value = "✅ $count services réinitialisés"
                            isResetting.value = false

                            coroutineScope.launch {
                                delay(2000)
                                showResetDialog.value = false
                                resetMessage.value = ""
                            }
                        }
                    },
                    enabled = !isResetting.value,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text(if (isResetting.value) "⏳ Reset..." else "✅ Confirmer Reset")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog.value = false },
                    enabled = !isResetting.value
                ) {
                    Text("Annuler")
                }
            }
        )
    }

    if (isLoading.value) {
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
                .background(Color(0xFFFCFCF9))
        ) {
            // ========== MESSAGE DE SAUVEGARDE ==========
            if (saveMessage.value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8F5E9))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = saveMessage.value,
                        color = Color(0xFF2E7D32),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ========== CONTENU CALENDRIER ==========
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                SettingSection(title = "📅 Synchronisation Calendrier") {
                    Column {
                        Text(
                            text = "Calendrier cible",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF627C7D),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                                .clickable { showCalendarDropdown.value = !showCalendarDropdown.value }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (selectedCalendarId.value <= 0) {
                                        "Aucun"
                                    } else {
                                        availableCalendars.value.find { it.id == selectedCalendarId.value }?.displayName
                                            ?: "Sélectionner"
                                    },
                                    fontSize = 14.sp,
                                    color = Color(0xFF134252)
                                )

                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown",
                                    tint = Color(0xFF0066CC),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (showCalendarDropdown.value) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = "Aucun",
                                    fontSize = 12.sp,
                                    color = Color(0xFF134252),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCalendarId.value = -1L
                                            showCalendarDropdown.value = false
                                        }
                                        .padding(12.dp)
                                )
                                Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)

                                availableCalendars.value.forEach { calendar ->
                                    Column {
                                        Text(
                                            text = calendar.displayName,
                                            fontSize = 12.sp,
                                            color = if (selectedCalendarId.value == calendar.id) Color(0xFF0066CC) else Color(0xFF134252),
                                            fontWeight = if (selectedCalendarId.value == calendar.id) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedCalendarId.value = calendar.id
                                                    showCalendarDropdown.value = false
                                                }
                                                .padding(12.dp)
                                        )
                                        Text(
                                            text = calendar.accountName,
                                            fontSize = 10.sp,
                                            color = Color(0xFF627C7D),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedCalendarId.value = calendar.id
                                                    showCalendarDropdown.value = false
                                                }
                                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                        )
                                        Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        SwitchSetting(
                            label = "Synchronisation automatique",
                            checked = autoSyncEnabled.value,
                            enabled = selectedCalendarId.value > 0,
                            onCheckedChange = { autoSyncEnabled.value = it }
                        )

                        if (selectedCalendarId.value > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "ℹ️ Les services seront automatiquement ajoutés à votre calendrier",
                                fontSize = 11.sp,
                                color = Color(0xFF627C7D),
                                lineHeight = 14.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 🔄 BOUTON RESET
                            Button(
                                onClick = { showResetDialog.value = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reset 🔄", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0066CC),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (enabled) Color(0xFF134252) else Color(0xFF9E9E9E),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF0066CC)
            )
        )
    }
}
