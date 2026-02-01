package com.stib.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stib.agent.utils.CalendarSyncUtil
import kotlinx.coroutines.launch
import android.util.Log
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.stib.agent.notifications.ServiceAlarmScheduler
import com.stib.agent.data.model.Service

@Composable
fun AddServiceScreen(
    navController: NavController
) {
    val db = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dateInput by remember { mutableStateOf(TextFieldValue("")) }
    var serviceNumInput by remember { mutableStateOf(TextFieldValue("")) }

    // PARTIE 1
    var heureDebutP1 by remember { mutableStateOf(TextFieldValue("")) }
    var heureFinP1 by remember { mutableStateOf(TextFieldValue("")) }
    var ligneP1Input by remember { mutableStateOf(TextFieldValue("")) }

    // PARTIE 2
    var hasPartie2 by remember { mutableStateOf(false) }
    var heureDebutP2 by remember { mutableStateOf(TextFieldValue("")) }
    var heureFinP2 by remember { mutableStateOf(TextFieldValue("")) }
    var ligneP2Input by remember { mutableStateOf(TextFieldValue("")) }

    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFCF9))
    ) {
        // Header - Titre centré sans flèche
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ajouter un service",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF134252)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Date
            FormFieldLabel("Date (JJ/MM/AAAA)")
            DateTextField(
                value = dateInput,
                onValueChange = { dateInput = it },
                placeholder = "23/01/2026"
            )

            // Service number
            FormFieldLabel("Numéro de service (max 5 chiffres)")
            ServiceNumberTextField(
                value = serviceNumInput,
                onValueChange = { serviceNumInput = it },
                placeholder = "41009"
            )

            Divider(color = Color(0xFFE0E0E0), modifier = Modifier.padding(vertical = 8.dp))

            // PARTIE 1
            Text(
                text = "🔵 PARTIE 1 (obligatoire)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0066CC)
            )

            FormFieldLabel("Heure de début (HH:MM)")
            TimeTextField(
                value = heureDebutP1,
                onValueChange = { heureDebutP1 = it },
                placeholder = "05:30"
            )

            FormFieldLabel("Heure de fin (HH:MM)")
            TimeTextField(
                value = heureFinP1,
                onValueChange = { heureFinP1 = it },
                placeholder = "12:06"
            )

            FormFieldLabel("Numéro de ligne (LLL - ex: 056)")
            LineNumberTextField(
                value = ligneP1Input,
                onValueChange = { ligneP1Input = it },
                placeholder = "056"
            )

            Divider(color = Color(0xFFE0E0E0), modifier = Modifier.padding(vertical = 8.dp))

            // PARTIE 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🟢 PARTIE 2 (optionnel - service coupé)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )

                Switch(
                    checked = hasPartie2,
                    onCheckedChange = { hasPartie2 = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4CAF50),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFBDBDBD)
                    )
                )
            }

            if (hasPartie2) {
                FormFieldLabel("Heure de début (HH:MM)")
                TimeTextField(
                    value = heureDebutP2,
                    onValueChange = { heureDebutP2 = it },
                    placeholder = "13:00"
                )

                FormFieldLabel("Heure de fin (HH:MM)")
                TimeTextField(
                    value = heureFinP2,
                    onValueChange = { heureFinP2 = it },
                    placeholder = "18:45"
                )

                FormFieldLabel("Numéro de ligne (LLL - ex: 056)")
                LineNumberTextField(
                    value = ligneP2Input,
                    onValueChange = { ligneP2Input = it },
                    placeholder = "056"
                )

                Divider(color = Color(0xFFE0E0E0), modifier = Modifier.padding(vertical = 8.dp))
            }

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFE53935),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.weight(1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text("Annuler")
            }

            Button(
                onClick = {
                    val validationError = validateForm(
                        dateInput, serviceNumInput,
                        heureDebutP1, heureFinP1,
                        hasPartie2, heureDebutP2, heureFinP2
                    )

                    if (validationError != null) {
                        errorMessage = validationError
                        return@Button
                    }

                    if (currentUser == null) return@Button

                    isLoading = true

                    val serviceData = buildServiceData(
                        dateInput.text,
                        serviceNumInput.text,
                        heureDebutP1.text,
                        heureFinP1.text,
                        ligneP1Input.text,
                        hasPartie2,
                        heureDebutP2.text,
                        heureFinP2.text,
                        ligneP2Input.text
                    )

                    db.collection("users")
                        .document(currentUser.uid)
                        .collection("services")
                        .add(serviceData)
                        .addOnSuccessListener { documentRef ->
                            Log.d("AddService", "✅ Service ajouté: ${documentRef.id}")
// Planifier les alarmes pour le nouveau service
                            scope.launch {
                                try {
                                    val scheduler = ServiceAlarmScheduler(context)
                                    scheduler.scheduleAllAlarmsForService(documentRef.id)
                                    Log.d("AddService", "Alarmes planifiées pour ${documentRef.id}")
                                } catch (e: Exception) {
                                    Log.e("AddService", "Erreur planification alarmes: ${e.message}", e)
                                }
                            }

                            // Récupérer le calendarId depuis Firestore (settings)
                            db.collection("users")
                                .document(currentUser.uid)
                                .collection("calendarSettings")
                                .document("settings")
                                .get()
                                .addOnSuccessListener { settingsDoc ->
                                    val autoSyncEnabled = settingsDoc.getBoolean("autoSyncEnabled") ?: false
                                    val calendarId = settingsDoc.getLong("calendarId") ?: -1L

                                    Log.d("AddService", "📱 AutoSync=$autoSyncEnabled, CalendarId=$calendarId")

                                    if (autoSyncEnabled && calendarId >= 0) {
                                        Log.d("AddService", "🔄 Démarrage sync calendrier...")

                                        // Synchroniser le calendrier immédiatement
                                        scope.launch {
                                            try {
                                                CalendarSyncUtil.syncAllServicesOnStartup(context, currentUser.uid, calendarId)
                                                Log.d("AddService", "✅ Calendrier synchronisé")

                                            } catch (e: Exception) {
                                                Log.e("AddService", "❌ Erreur sync/notifications: ${e.message}", e)
                                                errorMessage = "Erreur sync calendrier/notifications"
                                                isLoading = false
                                            }
                                        }
                                    } else {
                                        Log.d("AddService", "⚠️ Sync désactivée ou calendrier non sélectionné")
                                        isLoading = false
                                        navController.popBackStack()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("AddService", "❌ Erreur récupération paramètres: ${e.message}")
                                    errorMessage = "Erreur récupération paramètres"
                                    isLoading = false
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e("AddService", "❌ Erreur ajout service: ${e.message}", e)
                            errorMessage = "Erreur: ${e.message}"
                            isLoading = false
                        }
                },
                modifier = Modifier.weight(1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0066CC)
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Ajouter")
                }
            }


        }
    }
}

// ================== COMPOSABLES RÉUTILISABLES ==================

@Composable
fun FormFieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF627C7D)
    )
}

@Composable
fun DateTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String
) {
    BasicTextField(
        value = value,
        onValueChange = { newVal ->
            val digits = newVal.text.filter { it.isDigit() }
            if (digits.length > 8) return@BasicTextField

            val day = if (digits.length >= 2) digits.substring(0, 2).toIntOrNull() ?: 0 else if (digits.isNotEmpty()) digits.toIntOrNull() ?: 0 else 0
            val month = if (digits.length >= 4) digits.substring(2, 4).toIntOrNull() ?: 0 else if (digits.length > 2) digits.substring(2).toIntOrNull() ?: 0 else 0

            if (day > 31 || month > 12) return@BasicTextField

            val formatted = when {
                digits.length <= 2 -> digits
                digits.length <= 4 -> "${digits.substring(0, 2)}/${digits.substring(2)}"
                digits.length <= 6 -> {
                    // Formatage automatique de l'année (2 chiffres)
                    val yearInput = digits.substring(4)
                    val fullYear = when {
                        yearInput.length == 1 -> yearInput
                        yearInput.length == 2 -> {
                            val yearInt = yearInput.toInt()
                            when {
                                yearInt <= 30 -> "20${yearInput}" // 00-30 -> 2000-2030
                                yearInt <= 99 -> "19${yearInput}" // 31-99 -> 1931-1999
                                else -> yearInput
                            }
                        }
                        else -> yearInput
                    }
                    "${digits.substring(0, 2)}/${digits.substring(2, 4)}/${fullYear}"
                }
                else -> "${digits.substring(0, 2)}/${digits.substring(2, 4)}/${digits.substring(4, 8)}"
            }

            onValueChange(TextFieldValue(text = formatted, selection = TextRange(formatted.length)))
        },
        modifier = Modifier
            .fillMaxWidth()
            .defaultTextFieldStyle(),
        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF134252)
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        decorationBox = { innerTextField ->
            if (value.text.isEmpty()) {
                Text(placeholder, fontSize = 15.sp, color = Color(0xFFAAAAAA))
            }
            innerTextField()
        }
    )
}

@Composable
fun ServiceNumberTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String
) {
    BasicTextField(
        value = value,
        onValueChange = { newVal ->
            if (newVal.text.length <= 5 && newVal.text.all { it.isDigit() }) {
                onValueChange(newVal)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .defaultTextFieldStyle(),
        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF134252)
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        decorationBox = { innerTextField ->
            if (value.text.isEmpty()) {
                Text(placeholder, fontSize = 15.sp, color = Color(0xFFAAAAAA))
            }
            innerTextField()
        }
    )
}

@Composable
fun TimeTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String
) {
    BasicTextField(
        value = value,
        onValueChange = { newVal ->
            // Si l'utilisateur supprime (newVal.text.length < value.text.length)
            if (newVal.text.length < value.text.length) {
                // Laisser passer la suppression directement
                onValueChange(newVal)
                return@BasicTextField
            }

            val digits = newVal.text.filter { it.isDigit() }
            if (digits.length > 4) return@BasicTextField

            val formatted = when (digits.length) {
                0 -> ""
                1 -> {
                    val h = digits.toInt()
                    if (h > 2) "0${digits}:" else digits
                }
                2 -> {
                    val h = digits.toInt()
                    if (h > 23) return@BasicTextField
                    "${digits}:"
                }
                3 -> {
                    val h = digits.substring(0, 2).toInt()
                    val m = digits.substring(2, 3).toInt()
                    if (h > 23 || m > 5) return@BasicTextField
                    "${digits.substring(0, 2)}:${digits.substring(2)}"
                }
                4 -> {
                    val h = digits.substring(0, 2).toInt()
                    val m = digits.substring(2, 4).toInt()
                    if (h > 23 || m > 59) return@BasicTextField
                    "${digits.substring(0, 2)}:${digits.substring(2, 4)}"
                }
                else -> value.text
            }

            onValueChange(
                TextFieldValue(
                    text = formatted,
                    selection = TextRange(formatted.length)
                )
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .defaultTextFieldStyle(),
        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF134252)
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        decorationBox = { innerTextField ->
            if (value.text.isEmpty()) {
                Text(placeholder, fontSize = 15.sp, color = Color(0xFFAAAAAA))
            }
            innerTextField()
        }
    )
}


@Composable
fun LineNumberTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String
) {
    BasicTextField(
        value = value,
        onValueChange = { newVal ->
            val digits = newVal.text.filter { it.isDigit() }
            if (digits.length > 3) return@BasicTextField

            // Laisser taper/supprimer sans formatage immédiat
            onValueChange(
                TextFieldValue(
                    text = digits,
                    selection = TextRange(digits.length)
                )
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .defaultTextFieldStyle(),
        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF134252)
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        decorationBox = { innerTextField ->
            if (value.text.isEmpty()) {
                Text(placeholder, fontSize = 15.sp, color = Color(0xFFAAAAAA))
            }
            innerTextField()
        }
    )

    // ✅ Le formatage se fait SEULEMENT au moment de l'enregistrement
    // Dans buildServiceData() :
    // val ligneP1Formatted = if (ligneP1.isNotEmpty()) {
    //     ligneP1.padStart(3, '0')  // Formate à ce moment
    // } else ""
}

// ================== EXTENSION FUNCTION ==================

fun Modifier.defaultTextFieldStyle(): Modifier {
    return this
        .background(Color(0xFFF5F5F5), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
        .border(1.dp, Color(0xFFE0E0E0), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp)
}

// ================== VALIDATION ET DONNÉES ==================

fun validateForm(
    dateInput: TextFieldValue,
    serviceNumInput: TextFieldValue,
    heureDebutP1: TextFieldValue,
    heureFinP1: TextFieldValue,
    hasPartie2: Boolean,
    heureDebutP2: TextFieldValue,
    heureFinP2: TextFieldValue
): String? {
    if (dateInput.text.isEmpty() || serviceNumInput.text.isEmpty() ||
        heureDebutP1.text.isEmpty() || heureFinP1.text.isEmpty()) {
        return "Date, service et horaires partie 1 obligatoires"
    }

    if (!dateInput.text.matches(Regex("""^\d{2}/\d{2}/\d{4}$"""))) {
        return "Format date invalide (JJ/MM/AAAA)"
    }

    if (!heureDebutP1.text.matches(Regex("""^\d{2}:\d{2}$"""))) {
        return "Format heure début P1 invalide"
    }

    if (!heureFinP1.text.matches(Regex("""^\d{2}:\d{2}$"""))) {
        return "Format heure fin P1 invalide"
    }

    if (hasPartie2) {
        if (heureDebutP2.text.isEmpty() || heureFinP2.text.isEmpty()) {
            return "Si partie 2 activée, les horaires sont obligatoires"
        }

        if (!heureDebutP2.text.matches(Regex("""^\d{2}:\d{2}$"""))) {
            return "Format heure début P2 invalide"
        }

        if (!heureFinP2.text.matches(Regex("""^\d{2}:\d{2}$"""))) {
            return "Format heure fin P2 invalide"
        }
    }

    return null
}

fun buildServiceData(
    date: String,
    serviceNum: String,
    heureDebutP1: String,
    heureFinP1: String,
    ligneP1: String,
    hasPartie2: Boolean,
    heureDebutP2: String,
    heureFinP2: String,
    ligneP2: String
): HashMap<String, Any> {


    // ✅ APRÈS: garder le formatage HH:MM
    val p1DebutHHMM = heureDebutP1  // Ex: "05:30"
    val p1FinHHMM = heureFinP1      // Ex: "12:06"
    val ligneP1Formatted = if (ligneP1.isNotEmpty()) {
        ligneP1.padStart(3, '0')  // "5" → "005", "56" → "056"
    } else ""
    val serviceData = hashMapOf<String, Any>(
        "date_service" to date,
        "service" to serviceNum,
        "partie1" to hashMapOf(
            "heure_debut" to p1DebutHHMM,
            "heure_fin" to p1FinHHMM,
            "lignes" to if (ligneP1.isNotEmpty()) listOf(ligneP1) else emptyList<String>()
        ),
        "date_import" to LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
        "notes" to emptyList<String>()
    )

    if (hasPartie2) {
        val p2DebutHHMM = heureDebutP2  // Ex: "13:00"
        val p2FinHHMM = heureFinP2      // Ex: "18:45"

        serviceData["partie2"] = hashMapOf(
            "heure_debut" to p2DebutHHMM,
            "heure_fin" to p2FinHHMM,
            "lignes" to if (ligneP2.isNotEmpty()) listOf(ligneP2) else emptyList<String>()
        )
    }

    return serviceData
}
