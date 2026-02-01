@file:OptIn(ExperimentalMaterial3Api::class)

package com.stib.agent.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stib.agent.ui.components.PlanchetteSection
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.stib.agent.data.model.Service
import com.stib.agent.components.ServiceDataManager
import kotlinx.coroutines.launch


@Composable
fun HomeScreen() {
    val todayService = remember { mutableStateOf<Service?>(null) }
    val nextService = remember { mutableStateOf<Service?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    val currentUser = FirebaseAuth.getInstance().currentUser
    val db = FirebaseFirestore.getInstance()
    val refreshTrigger = remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentUser, refreshTrigger.value) {
        if (currentUser != null) {
            android.util.Log.d("HomeScreen", "UserId: ${currentUser.uid}")
            isLoading.value = true

            // ✅ UTILISER getAllServices() avec coroutine
            coroutineScope.launch {
                val allServices = ServiceDataManager.getAllServices()

                val today = LocalDate.now()
                val todayService_temp = allServices.find { service ->
                    if (service.dateService == today) {
                        try {
                            val now = LocalDateTime.now()
                            val endTime = service.partie2Fin ?: service.partie1Fin
                            val endDateTime = LocalDateTime.of(today, endTime)
                            val twoHoursAgo = now.minusHours(2)
                            endDateTime.isAfter(twoHoursAgo)
                        } catch (e: Exception) {
                            true
                        }
                    } else false
                }

                val nextService_temp = allServices
                    .filter { service ->
                        service.dateService != null && service.dateService.isAfter(today)
                    }
                    .sortedBy { it.dateService }
                    .firstOrNull()

                todayService.value = todayService_temp
                nextService.value = nextService_temp
                isLoading.value = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFCF9))
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "AUJOURD'HUI",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF627C7D),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            if (isLoading.value) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF0066CC))
                }
            } else {
                if (todayService.value != null) {
                    ServiceCardEditable(
                        service = todayService.value!!,
                        db = db,
                        currentUser = currentUser,
                        onDataChanged = {
                            refreshTrigger.value += 1
                        }
                    )
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🎉", fontSize = 48.sp, modifier = Modifier.padding(bottom = 16.dp))
                            Text(
                                "Vous n'avez pas de service aujourd'hui.",
                                fontSize = 16.sp,
                                color = Color(0xFF0066CC),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                "Profitez bien de votre journée !",
                                fontSize = 14.sp,
                                color = Color(0xFF627C7D)
                            )
                        }
                    }
                }

                if (nextService.value != null && todayService.value == null) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROCHAIN SERVICE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF627C7D)
                        )
                    }

                    ServiceCardEditable(
                        service = nextService.value!!,
                        db = db,
                        currentUser = currentUser,
                        onDataChanged = {
                            refreshTrigger.value += 1
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceCardEditable(
    service: Service,
    db: FirebaseFirestore,
    currentUser: com.google.firebase.auth.FirebaseUser?,
    onDataChanged: () -> Unit = {}
) {
    val context = LocalContext.current

    var lignesP1 by remember { mutableStateOf(service.partie1Lignes.toMutableList()) }
    var busP1 by remember { mutableStateOf(service.partie1Bus.toMutableList()) }
    var newLigneP1 by remember { mutableStateOf("") }
    var newBusP1 by remember { mutableStateOf("") }

    var lignesP2 by remember { mutableStateOf(service.partie2Lignes.toMutableList()) }
    var busP2 by remember { mutableStateOf(service.partie2Bus.toMutableList()) }
    var newLigneP2 by remember { mutableStateOf("") }
    var newBusP2 by remember { mutableStateOf("") }

    var showNotesSheet by remember { mutableStateOf(false) }
    var notesList by remember { mutableStateOf(service.notes) }
    var newNoteText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (results != null && results.isNotEmpty()) {
            val recognizedText = results[0]
            newNoteText += if (newNoteText.isEmpty()) recognizedText else "\n$recognizedText"
            isRecording = false
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

    fun updatePartie(partie: String, lignes: List<String>, bus: List<String>) {
        if (currentUser != null) {
            db.collection("users")
                .document(currentUser.uid)
                .collection("services")
                .whereEqualTo("date_service", service.dateServiceRaw)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.documents.isNotEmpty()) {
                        val docId = snapshot.documents[0].id

                        val partieData = if (partie == "partie1") {
                            mapOf<String, Any>(
                                "heure_debut" to service.partie1DebutRaw,
                                "heure_fin" to service.partie1FinRaw,
                                "lignes" to lignes,
                                "bus" to bus
                            )
                        } else {
                            mapOf<String, Any>(
                                "heure_debut" to (service.partie2DebutRaw ?: ""),
                                "heure_fin" to (service.partie2FinRaw ?: ""),
                                "lignes" to lignes,
                                "bus" to bus
                            )
                        }

                        db.collection("users")
                            .document(currentUser.uid)
                            .collection("services")
                            .document(docId)
                            .update(partie, partieData)
                            .addOnSuccessListener {
                                android.util.Log.d("HomeScreen", "✅ $partie mis à jour")
                                ServiceDataManager.refreshServices()
                                onDataChanged()
                            }
                    }
                }
        }
    }

    LaunchedEffect(service) {
        db.collection("users")
            .document(currentUser?.uid ?: return@LaunchedEffect)
            .collection("services")
            .whereEqualTo("date_service", service.dateServiceRaw)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.documents.isNotEmpty()) {
                    val doc = snapshot.documents[0]

                    val partie1 = doc.get("partie1") as? Map<*, *>
                    lignesP1 = (partie1?.get("lignes") as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
                    busP1 = (partie1?.get("bus") as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()

                    val partie2 = doc.get("partie2") as? Map<*, *>
                    lignesP2 = (partie2?.get("lignes") as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
                    busP2 = (partie2?.get("bus") as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()

                    val notesData = doc.get("notes")
                    val loadedNotes = when (notesData) {
                        is String -> if (notesData.isNotBlank()) listOf(notesData) else emptyList()
                        is List<*> -> notesData.filterIsInstance<String>()
                        else -> emptyList()
                    }
                    notesList = loadedNotes
                }
            }
    }

    // ✅ FORMATTER POUR AFFICHER LES LocalTime
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F4F8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("DATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF627C7D))
                    Text(
                        formatDateShort(service.dateServiceRaw),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF134252),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("SERVICE N°", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF627C7D))
                    Text(
                        service.serviceNumber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF134252),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF0066CC).copy(alpha = 0.2f))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("HORAIRES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF627C7D), modifier = Modifier.weight(1.5f))
                Text("LIGNES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF627C7D), modifier = Modifier.weight(1f))
                Text("BUS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF627C7D), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // PARTIE 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1.5f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // ✅ FORMATTER LES LocalTime
                    Text(service.partie1Debut?.format(timeFormatter) ?: service.partie1DebutRaw, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF134252))
                    Text("→", fontSize = 11.sp, color = Color(0xFF627C7D))
                    Text(service.partie1Fin?.format(timeFormatter) ?: service.partie1FinRaw, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF134252))
                }

                Column(modifier = Modifier.weight(1f)) {
                    lignesP1.forEach { ligne ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0066CC).copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("L$ligne", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0066CC))
                                IconButton(
                                    onClick = {
                                        lignesP1 = lignesP1.filter { it != ligne }.toMutableList()
                                        updatePartie("partie1", lignesP1, busP1)
                                    },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Supprimer", tint = Color(0xFF0066CC), modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BasicTextField(
                            value = newLigneP1,
                            onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) newLigneP1 = it },
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.White, RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(4.dp))
                                .padding(4.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF134252)),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.Center) {
                                    if (newLigneP1.isEmpty()) Text("056", fontSize = 10.sp, color = Color(0xFFAAAAAA))
                                    innerTextField()
                                }
                            }
                        )

                        IconButton(
                            onClick = {
                                if (newLigneP1.isNotEmpty() && !lignesP1.contains(newLigneP1)) {
                                    lignesP1 = (lignesP1 + newLigneP1).toMutableList()
                                    updatePartie("partie1", lignesP1, busP1)
                                    newLigneP1 = ""
                                }
                            },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Ajouter", tint = Color(0xFF0066CC), modifier = Modifier.size(12.dp))
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    busP1.forEach { bus ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFF9800).copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(bus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                                IconButton(
                                    onClick = {
                                        busP1 = busP1.filter { it != bus }.toMutableList()
                                        updatePartie("partie1", lignesP1, busP1)
                                    },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Supprimer", tint = Color(0xFFFF9800), modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BasicTextField(
                            value = newBusP1,
                            onValueChange = { if (it.length <= 5 && it.all { c -> c.isDigit() }) newBusP1 = it },
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.White, RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(4.dp))
                                .padding(4.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF134252)),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.Center) {
                                    if (newBusP1.isEmpty()) Text("9430", fontSize = 10.sp, color = Color(0xFFAAAAAA))
                                    innerTextField()
                                }
                            }
                        )

                        IconButton(
                            onClick = {
                                if (newBusP1.isNotEmpty() && !busP1.contains(newBusP1)) {
                                    busP1 = (busP1 + newBusP1).toMutableList()
                                    updatePartie("partie1", lignesP1, busP1)
                                    newBusP1 = ""
                                }
                            },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Ajouter", tint = Color(0xFF0066CC), modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            // PARTIE 2
            if (service.partie2Debut != null && service.partie2Fin != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1.5f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // ✅ FORMATTER LES LocalTime
                        Text(service.partie2Debut?.format(timeFormatter) ?: service.partie2DebutRaw ?: "", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF134252))
                        Text("→", fontSize = 11.sp, color = Color(0xFF627C7D))
                        Text(service.partie2Fin?.format(timeFormatter) ?: service.partie2FinRaw ?: "", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF134252))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        lignesP2.forEach { ligne ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF0066CC).copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("L$ligne", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0066CC))
                                    IconButton(
                                        onClick = {
                                            lignesP2 = lignesP2.filter { it != ligne }.toMutableList()
                                            updatePartie("partie2", lignesP2, busP2)
                                        },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Supprimer", tint = Color(0xFF0066CC), modifier = Modifier.size(10.dp))
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicTextField(
                                value = newLigneP2,
                                onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) newLigneP2 = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color.White, RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(4.dp))
                                    .padding(4.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF134252)),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.Center) {
                                        if (newLigneP2.isEmpty()) Text("057", fontSize = 10.sp, color = Color(0xFFAAAAAA))
                                        innerTextField()
                                    }
                                }
                            )

                            IconButton(
                                onClick = {
                                    if (newLigneP2.isNotEmpty() && !lignesP2.contains(newLigneP2)) {
                                        lignesP2 = (lignesP2 + newLigneP2).toMutableList()
                                        updatePartie("partie2", lignesP2, busP2)
                                        newLigneP2 = ""
                                    }
                                },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Ajouter", tint = Color(0xFF0066CC), modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        busP2.forEach { bus ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFF9800).copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(bus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                                    IconButton(
                                        onClick = {
                                            busP2 = busP2.filter { it != bus }.toMutableList()
                                            updatePartie("partie2", lignesP2, busP2)
                                        },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Supprimer", tint = Color(0xFFFF9800), modifier = Modifier.size(10.dp))
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicTextField(
                                value = newBusP2,
                                onValueChange = { if (it.length <= 5 && it.all { c -> c.isDigit() }) newBusP2 = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color.White, RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(4.dp))
                                    .padding(4.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF134252)),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.Center) {
                                        if (newBusP2.isEmpty()) Text("9431", fontSize = 10.sp, color = Color(0xFFAAAAAA))
                                        innerTextField()
                                    }
                                }
                            )

                            IconButton(
                                onClick = {
                                    if (newBusP2.isNotEmpty() && !busP2.contains(newBusP2)) {
                                        busP2 = (busP2 + newBusP2).toMutableList()
                                        updatePartie("partie2", lignesP2, busP2)
                                        newBusP2 = ""
                                    }
                                },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Ajouter", tint = Color(0xFF0066CC), modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF0066CC).copy(alpha = 0.2f))
            )

            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "📄 PLANCHETTE",
                    fontSize = 10.sp,
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
                        onDataChanged()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF0066CC).copy(alpha = 0.2f))
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (notesList.isNotEmpty()) {
                Text(
                    text = "📝 NOTES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0066CC),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                notesList.take(2).forEach { note ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFF8E1),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = if (note.length > 80) note.take(80) + "..." else note,
                            fontSize = 12.sp,
                            color = Color(0xFF134252),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (notesList.size > 2) {
                    Text(
                        text = "+ ${notesList.size - 2} note(s) supplémentaire(s)",
                        fontSize = 11.sp,
                        color = Color(0xFF627C7D),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            Button(
                onClick = {
                    newNoteText = ""
                    showNotesSheet = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0066CC)
                )
            ) {
                Text("📝 Ajouter une note", color = Color.White, fontSize = 13.sp)
            }
        }
    }

    if (showNotesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNotesSheet = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "📝 Nouvelle note",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF134252),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                TextField(
                    value = newNoteText,
                    onValueChange = { newNoteText = it },
                    placeholder = { Text("Écrire une note...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF5F5F5),
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        focusedIndicatorColor = Color(0xFF0066CC),
                        unfocusedIndicatorColor = Color(0xFFCCCCCC)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        isRecording = true
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRENCH.language)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dites quelque chose...")
                        }
                        speechLauncher.launch(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFA500)
                    ),
                    enabled = !isRecording
                ) {
                    Text(
                        text = if (isRecording) "🎤 Enregistrement..." else "🎤 Ajouter mémo vocal",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showNotesSheet = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE0E0E0)
                        )
                    ) {
                        Text("Annuler", color = Color.Black)
                    }

                    Button(
                        onClick = {
                            val sanitized = sanitizeNote(newNoteText)
                            if (sanitized.isNotBlank() && currentUser != null) {
                                val updatedNotes = notesList + sanitized
                                db.collection("users")
                                    .document(currentUser.uid)
                                    .collection("services")
                                    .whereEqualTo("date_service", service.dateServiceRaw)
                                    .get()
                                    .addOnSuccessListener { snapshot ->
                                        if (snapshot.documents.isNotEmpty()) {
                                            val docId = snapshot.documents[0].id
                                            db.collection("users")
                                                .document(currentUser.uid)
                                                .collection("services")
                                                .document(docId)
                                                .update("notes", updatedNotes)
                                                .addOnSuccessListener {
                                                    android.util.Log.d("ServiceCardEditable", "✅ Note ajoutée")
                                                    notesList = updatedNotes
                                                    ServiceDataManager.refreshServices()
                                                    onDataChanged()
                                                    showNotesSheet = false
                                                }
                                        }
                                    }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0066CC)
                        )
                    ) {
                        Text("Ajouter", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

fun formatDateShort(dateString: String): String {
    return try {
        val inputFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val date = java.time.LocalDate.parse(dateString, inputFormatter)
        val outputFormatter = java.time.format.DateTimeFormatter.ofPattern("EEEE dd/MM/yy", Locale("fr", "FR"))
        date.format(outputFormatter)
    } catch (e: Exception) {
        dateString
    }
}
