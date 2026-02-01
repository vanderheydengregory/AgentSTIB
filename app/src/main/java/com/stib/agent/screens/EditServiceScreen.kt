package com.stib.agent.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stib.agent.ui.components.PlanchetteSection
import com.stib.agent.utils.CalendarSyncUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf
import com.stib.agent.notifications.ServiceAlarmScheduler
import com.stib.agent.data.model.Service
import com.stib.agent.components.ServiceDataManager
import java.time.format.DateTimeFormatter

@Composable
fun EditServiceScreen(
    serviceId: String,
    navController: NavController
) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val service = remember { mutableStateOf<Service?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // ✅ CHARGER LE SERVICE AVEC ServiceDataManager
    LaunchedEffect(serviceId, currentUser) {
        if (currentUser != null && serviceId.isNotEmpty()) {
            scope.launch {
                val loadedService = ServiceDataManager.getService(serviceId)
                service.value = loadedService
                isLoading.value = false

                if (loadedService == null) {
                    Log.e("EditService", "⚠️ Service $serviceId introuvable")
                }
            }
        }
    }

    if (isLoading.value) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFCFCF9)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF0066CC))
        }
    } else if (service.value != null) {
        EditServiceContent(
            service = service.value!!,
            serviceId = serviceId,
            navController = navController
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFCFCF9)),
            contentAlignment = Alignment.Center
        ) {
            Text("Service non trouvé", fontSize = 16.sp, color = Color(0xFF627C7D))
        }
    }
}

@Composable
fun EditServiceContent(
    service: Service,
    serviceId: String,
    navController: NavController
) {
    val db = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 🎯 SNACKBAR STATE
    val snackbarHostState = remember { SnackbarHostState() }

    // ✅ FORMATTER POUR AFFICHER LES LocalTime
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // ✅ SÉPARATION PARTIE 1 ET PARTIE 2
    val p1LignesState = remember { mutableStateOf(service.partie1Lignes.toMutableList()) }
    val p1BusState = remember { mutableStateOf(service.partie1Bus.toMutableList()) }
    val p2LignesState = remember { mutableStateOf(service.partie2Lignes.toMutableList()) }
    val p2BusState = remember { mutableStateOf(service.partie2Bus.toMutableList()) }

    val newP1LigneInput = remember { mutableStateOf("") }
    val newP1BusInput = remember { mutableStateOf("") }
    val newP2LigneInput = remember { mutableStateOf("") }
    val newP2BusInput = remember { mutableStateOf("") }

    val notesList = remember { mutableStateOf(service.notes) }
    val newNoteInput = remember { mutableStateOf("") }
    val editingNoteIndex = remember { mutableStateOf<Int?>(null) }
    val editNoteInput = remember { mutableStateOf("") }

    val errorMessage = remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }
    val showDeleteConfirm = remember { mutableStateOf(false) }
    val refreshTrigger = remember { mutableIntStateOf(0) }
    val autoSaveInProgress = remember { mutableStateOf(false) }
    val isActive = remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose {
            isActive.value = false
            Log.d("EditService", "Screen disposed - stopping autosave")
        }
    }

    fun sanitizeNote(input: String): String {
        return input
            .replace("<", "")
            .replace(">", "")
            .replace("script", "", ignoreCase = true)
            .replace("javascript:", "", ignoreCase = true)
            .take(500)
            .trim()
    }

    // ✅ AUTO-SAVE AVEC PARTIE 1 ET PARTIE 2 SÉPARÉES
    LaunchedEffect(p1LignesState.value, p1BusState.value, p2LignesState.value, p2BusState.value) {
        if (!isActive.value || currentUser == null || serviceId.isEmpty()) return@LaunchedEffect

        delay(500)

        if (!isActive.value || autoSaveInProgress.value) return@LaunchedEffect

        autoSaveInProgress.value = true

        val updateData = mutableMapOf<String, Any>()

        // PARTIE 1
        updateData["partie1"] = mapOf(
            "heure_debut" to service.partie1DebutRaw,
            "heure_fin" to service.partie1FinRaw,
            "lignes" to p1LignesState.value,
            "bus" to p1BusState.value
        )

        // PARTIE 2 (si existe)
        if (service.hasPartie2) {
            updateData["partie2"] = mapOf(
                "heure_debut" to (service.partie2DebutRaw ?: ""),
                "heure_fin" to (service.partie2FinRaw ?: ""),
                "lignes" to p2LignesState.value,
                "bus" to p2BusState.value
            )
        }

        db.collection("users")
            .document(currentUser.uid)
            .collection("services")
            .document(serviceId)
            .update(updateData)
            .addOnSuccessListener {
                if (isActive.value) {
                    Log.d("EditService", "✅ Auto-sauvegardé")
                    autoSaveInProgress.value = false

                    // ✅ Invalider le cache
                    ServiceDataManager.refreshServices()

                    // Replanifier les alarmes après modification
                    scope.launch {
                        try {
                            val scheduler = ServiceAlarmScheduler(context)
                            scheduler.rescheduleAllAlarmsForService(serviceId)
                            Log.d("EditService", "Alarmes replanifiées")
                        } catch (e: Exception) {
                            Log.e("EditService", "Erreur replanification alarmes: ${e.message}", e)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                if (isActive.value) {
                    Log.e("EditService", "❌ Erreur auto-save: ${e.message}", e)
                }
                autoSaveInProgress.value = false
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFCFCF9))
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(20.dp)
            ) {
                Text(
                    text = "Édition du service",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF134252),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "DATE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF999999),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = service.dateServiceRaw,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF134252)
                        )
                    }

                    Column {
                        Text(
                            text = "SERVICE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF999999),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = service.serviceNumber,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF134252)
                        )
                    }

                    Column {
                        Text(
                            text = "HORAIRE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF999999),
                            letterSpacing = 0.5.sp
                        )
                        Column {
                            Text(
                                text = "${service.partie1Debut?.format(timeFormatter) ?: service.partie1DebutRaw} - ${service.partie1Fin?.format(timeFormatter) ?: service.partie1FinRaw}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF134252)
                            )
                            if (service.hasPartie2) {
                                Text(
                                    text = "${service.partie2Debut?.format(timeFormatter) ?: service.partie2DebutRaw} - ${service.partie2Fin?.format(timeFormatter) ?: service.partie2FinRaw}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF134252)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE0E0E0))
                Spacer(modifier = Modifier.height(20.dp))

                // ✅ FORMULAIRE PARTIE 1
                Text(
                    text = "PARTIE 1",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0066CC),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lignes",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF627C7D),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        p1LignesState.value.forEach { ligne ->
                            CompactEditableTag(
                                value = ligne,
                                onRemove = {
                                    p1LignesState.value =
                                        p1LignesState.value.filter { it != ligne }.toMutableList()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = newP1LigneInput.value,
                                onValueChange = { newVal ->
                                    if (newVal.length <= 3 && newVal.all { it.isDigit() }) {
                                        newP1LigneInput.value = newVal
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF134252)
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    if (newP1LigneInput.value.isEmpty()) {
                                        Text("056", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                                    }
                                    innerTextField()
                                }
                            )

                            IconButton(
                                onClick = {
                                    if (newP1LigneInput.value.isNotEmpty() && !p1LignesState.value.contains(
                                            newP1LigneInput.value
                                        )
                                    ) {
                                        p1LignesState.value =
                                            (p1LignesState.value + newP1LigneInput.value).toMutableList()
                                        newP1LigneInput.value = ""
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Ajouter",
                                    tint = Color(0xFF0066CC),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bus",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF627C7D),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        p1BusState.value.forEach { bus ->
                            CompactEditableTag(
                                value = bus,
                                onRemove = {
                                    p1BusState.value =
                                        p1BusState.value.filter { it != bus }.toMutableList()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = newP1BusInput.value,
                                onValueChange = { newVal ->
                                    if (newVal.length <= 5 && newVal.all { it.isDigit() }) {
                                        newP1BusInput.value = newVal
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF134252)
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    if (newP1BusInput.value.isEmpty()) {
                                        Text("9430", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                                    }
                                    innerTextField()
                                }
                            )

                            IconButton(
                                onClick = {
                                    if (newP1BusInput.value.isNotEmpty() && !p1BusState.value.contains(
                                            newP1BusInput.value
                                        )
                                    ) {
                                        p1BusState.value =
                                            (p1BusState.value + newP1BusInput.value).toMutableList()
                                        newP1BusInput.value = ""
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Ajouter",
                                    tint = Color(0xFF0066CC),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ✅ FORMULAIRE PARTIE 2 (seulement si existe)
                if (service.hasPartie2) {
                    Text(
                        text = "PARTIE 2",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0066CC),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lignes",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF627C7D),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            p2LignesState.value.forEach { ligne ->
                                CompactEditableTag(
                                    value = ligne,
                                    onRemove = {
                                        p2LignesState.value =
                                            p2LignesState.value.filter { it != ligne }
                                                .toMutableList()
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = newP2LigneInput.value,
                                    onValueChange = { newVal ->
                                        if (newVal.length <= 3 && newVal.all { it.isDigit() }) {
                                            newP2LigneInput.value = newVal
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
                                        .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    textStyle = LocalTextStyle.current.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF134252)
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        if (newP2LigneInput.value.isEmpty()) {
                                            Text("056", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                                        }
                                        innerTextField()
                                    }
                                )

                                IconButton(
                                    onClick = {
                                        if (newP2LigneInput.value.isNotEmpty() && !p2LignesState.value.contains(
                                                newP2LigneInput.value
                                            )
                                        ) {
                                            p2LignesState.value =
                                                (p2LignesState.value + newP2LigneInput.value).toMutableList()
                                            newP2LigneInput.value = ""
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Ajouter",
                                        tint = Color(0xFF0066CC),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bus",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF627C7D),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            p2BusState.value.forEach { bus ->
                                CompactEditableTag(
                                    value = bus,
                                    onRemove = {
                                        p2BusState.value =
                                            p2BusState.value.filter { it != bus }.toMutableList()
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = newP2BusInput.value,
                                    onValueChange = { newVal ->
                                        if (newVal.length <= 5 && newVal.all { it.isDigit() }) {
                                            newP2BusInput.value = newVal
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
                                        .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    textStyle = LocalTextStyle.current.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF134252)
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        if (newP2BusInput.value.isEmpty()) {
                                            Text(
                                                "9430",
                                                fontSize = 13.sp,
                                                color = Color(0xFFAAAAAA)
                                            )
                                        }
                                        innerTextField()
                                    }
                                )

                                IconButton(
                                    onClick = {
                                        if (newP2BusInput.value.isNotEmpty() && !p2BusState.value.contains(
                                                newP2BusInput.value
                                            )
                                        ) {
                                            p2BusState.value =
                                                (p2BusState.value + newP2BusInput.value).toMutableList()
                                            newP2BusInput.value = ""
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Ajouter",
                                        tint = Color(0xFF0066CC),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                Text(
                    text = "📄 PLANCHETTE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0066CC),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                PlanchetteSection(
                    service = service.serviceNumber,
                    heureDebut = service.partie1Debut?.format(timeFormatter) ?: service.partie1DebutRaw,
                    heureFin = service.partie1Fin?.format(timeFormatter) ?: service.partie1FinRaw,
                    onPlanchetteChanged = {
                        refreshTrigger.value += 1
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "📝 NOTES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0066CC),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                notesList.value.forEachIndexed { index, note ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                    ) {
                        if (editingNoteIndex.value == index) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                BasicTextField(
                                    value = editNoteInput.value,
                                    onValueChange = { editNoteInput.value = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(6.dp))
                                        .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    textStyle = LocalTextStyle.current.copy(
                                        fontSize = 14.sp,
                                        color = Color(0xFF134252)
                                    )
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = {
                                        editingNoteIndex.value = null
                                        editNoteInput.value = ""
                                    }) {
                                        Text("Annuler", color = Color(0xFF999999))
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            val sanitized = sanitizeNote(editNoteInput.value)
                                            if (sanitized.isNotBlank() && isActive.value) {
                                                val updatedNotes = notesList.value.toMutableList()
                                                updatedNotes[index] = sanitized
                                                notesList.value = updatedNotes

                                                db.collection("users")
                                                    .document(currentUser!!.uid)
                                                    .collection("services")
                                                    .document(serviceId)
                                                    .update(hashMapOf<String, Any>("notes" to notesList.value))
                                                    .addOnSuccessListener {
                                                        if (isActive.value) {
                                                            Log.d("EditService", "✅ Note modifiée")
                                                            ServiceDataManager.refreshServices()
                                                        }
                                                    }

                                                editingNoteIndex.value = null
                                                editNoteInput.value = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(
                                                0xFF0066CC
                                            )
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Enregistrer", fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    note,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color(0xFF134252),
                                    modifier = Modifier.weight(1f)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            editingNoteIndex.value = index
                                            editNoteInput.value = note
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Modifier",
                                            tint = Color(0xFF0066CC),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (isActive.value) {
                                                notesList.value =
                                                    notesList.value.filterIndexed { i, _ -> i != index }

                                                db.collection("users")
                                                    .document(currentUser!!.uid)
                                                    .collection("services")
                                                    .document(serviceId)
                                                    .update(hashMapOf<String, Any>("notes" to notesList.value))
                                                    .addOnSuccessListener {
                                                        if (isActive.value) {
                                                            Log.d("EditService", "✅ Note supprimée")
                                                            ServiceDataManager.refreshServices()
                                                        }
                                                    }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Supprimer",
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = newNoteInput.value,
                        onValueChange = { newNoteInput.value = it },
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF134252)
                        ),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (newNoteInput.value.isEmpty()) {
                                Text(
                                    "Ajouter une note...",
                                    fontSize = 14.sp,
                                    color = Color(0xFFAAAAAA)
                                )
                            }
                            innerTextField()
                        }
                    )

                    IconButton(
                        onClick = {
                            val sanitized = sanitizeNote(newNoteInput.value)
                            if (sanitized.isNotBlank() && isActive.value) {
                                notesList.value = (notesList.value + sanitized).toMutableList()

                                db.collection("users")
                                    .document(currentUser!!.uid)
                                    .collection("services")
                                    .document(serviceId)
                                    .update(hashMapOf<String, Any>("notes" to notesList.value))
                                    .addOnSuccessListener {
                                        if (isActive.value) {
                                            Log.d("EditService", "✅ Note ajoutée")
                                            ServiceDataManager.refreshServices()
                                        }
                                    }

                                newNoteInput.value = ""
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Ajouter note",
                            tint = Color(0xFF0066CC)
                        )
                    }
                }

                if (errorMessage.value.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage.value,
                        color = Color(0xFFE53935),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isActive.value = false
                            navController.popBackStack()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Retour")
                    }

                    Button(
                        onClick = { showDeleteConfirm.value = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Supprimer",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Supprimer", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 🎯 ALERT DIALOG DE CONFIRMATION
            if (showDeleteConfirm.value) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm.value = false },
                    title = {
                        Text("Supprimer le service ?", fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text("Cette action est irréversible. Le service et ses événements calendrier seront définitivement supprimés.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (currentUser != null && serviceId.isNotEmpty()) {
                                    isLoading.value = true
                                    showDeleteConfirm.value = false

                                    // ✅ ÉTAPE 1: Supprimer les événements calendrier (P1 & P2)
                                    Log.d("EditService", "🗑️ Suppression des événements calendrier...")
                                    CalendarSyncUtil.deleteServiceEventFromCalendar(
                                        context,
                                        serviceId,
                                        currentUser.uid
                                    )

                                    // ✅ ÉTAPE 2: Attendre que le calendrier soit synchronisé puis supprimer de Firestore
                                    scope.launch {
                                        delay(800)

                                        Log.d("EditService", "🗑️ Suppression du service Firestore...")

                                        db.collection("users")
                                            .document(currentUser.uid)
                                            .collection("services")
                                            .document(serviceId)
                                            .delete()
                                            .addOnSuccessListener {
                                                Log.d(
                                                    "EditService",
                                                    "✅ Service et événements supprimés avec succès"
                                                )

                                                // Annuler toutes les alarmes
                                                try {
                                                    val scheduler = ServiceAlarmScheduler(context)
                                                    scheduler.cancelAllAlarmsForService(serviceId)
                                                    Log.d("EditService", "Alarmes annulées pour $serviceId")
                                                } catch (e: Exception) {
                                                    Log.e("EditService", "Erreur annulation alarmes: ${e.message}", e)
                                                }

                                                // ✅ Invalider le cache
                                                ServiceDataManager.refreshServices()

                                                isLoading.value = false
                                                isActive.value = false

                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = "✅ Service supprimé avec succès",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }

                                                android.os.Handler(android.os.Looper.getMainLooper())
                                                    .postDelayed({
                                                        navController.popBackStack()
                                                        navController.popBackStack()
                                                    }, 1500)
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e(
                                                    "EditService",
                                                    "❌ Erreur suppression Firestore: ${e.message}",
                                                    e
                                                )
                                                isLoading.value = false

                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = "❌ Erreur: ${e.message}",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                }
                                            }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE53935)
                            )
                        ) {
                            Text("Supprimer")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showDeleteConfirm.value = false }) {
                            Text("Annuler")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CompactEditableTag(
    value: String,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0066CC)
            )

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Supprimer",
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
