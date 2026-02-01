package com.stib.agent.ui.screens

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stib.agent.ui.navigation.Screen
import com.stib.agent.utils.PlanchetteManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class PlanningService(
    val id: String,
    val date: String,
    val jour: String,
    val service: String,
    val dateservice: String,
    val lignes: List<String> = emptyList(),
    val bus: List<String> = emptyList(),
    val partie1Debut: String = "",
    val partie1Fin: String = "",
    val partie1Lignes: List<String> = emptyList(),
    val partie1Bus: List<String> = emptyList(),
    val partie2Debut: String? = null,
    val partie2Fin: String? = null,
    val partie2Lignes: List<String> = emptyList(),
    val partie2Bus: List<String> = emptyList()
)

@Composable
fun PlanningScreen(navController: NavController) {
    val services = remember { mutableStateOf<List<PlanningService>>(emptyList()) }
    val isLoading = remember { mutableStateOf(true) }
    val showHistory = remember { mutableStateOf(false) }
    val showMenuDialog = remember { mutableStateOf(false) }

    val currentUser = FirebaseAuth.getInstance().currentUser
    val db = FirebaseFirestore.getInstance()

    // ✅ CORRECTION #1 : Tri robuste par DATE puis SERVICE
    val refreshServices = {
        if (currentUser != null) {
            isLoading.value = true
            fetchAllServices(db, currentUser.uid) { allServices ->
                services.value = allServices.sortedWith(
                    compareBy<PlanningService> { service ->
                        // Convertir DD/MM/YYYY en YYYY-MM-DD pour tri chronologique correct
                        try {
                            val parts = service.dateservice.split("/")
                            if (parts.size == 3) {
                                val year = parts[2].toIntOrNull() ?: 9999
                                val month = parts[1].toIntOrNull()?.toString()?.padStart(2, '0') ?: "00"
                                val day = parts[0].toIntOrNull()?.toString()?.padStart(2, '0') ?: "00"
                                "$year-$month-$day"
                            } else {
                                service.dateservice
                            }
                        } catch (e: Exception) {
                            service.dateservice
                        }
                    }
                        .thenBy { it.service }  // Puis trier par numéro de service
                )
                isLoading.value = false
            }
        }
    }

    LaunchedEffect(currentUser) {
        refreshServices()
    }

    val today = LocalDate.now()
    val futureServices = services.value.filter { service ->
        try {
            val parts = service.dateservice.split("/")
            if (parts.size == 3) {
                val date = LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                date.isAfter(today) || date.isEqual(today)
            } else false
        } catch (e: Exception) {
            false
        }
    }

    // ✅ CORRECTION #2 : Tri robuste pour l'historique (plus récents d'abord)
    val pastServices = services.value.filter { service ->
        try {
            val parts = service.dateservice.split("/")
            if (parts.size == 3) {
                val date = LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                date.isBefore(today)
            } else false
        } catch (e: Exception) {
            false
        }
    }.sortedWith(
        compareBy<PlanningService> { service ->
            try {
                val parts = service.dateservice.split("/")
                if (parts.size == 3) {
                    val year = parts[2].toIntOrNull() ?: 9999
                    val month = parts[1].toIntOrNull()?.toString()?.padStart(2, '0') ?: "00"
                    val day = parts[0].toIntOrNull()?.toString()?.padStart(2, '0') ?: "00"
                    "$year-$month-$day"
                } else {
                    service.dateservice
                }
            } catch (e: Exception) {
                service.dateservice
            }
        }
            .reversed()  // Inverser pour les plus récents d'abord
            .thenBy { it.service }
    )

    val displayedServices = if (showHistory.value) pastServices else futureServices

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFCF9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showHistory.value) "VOIR HISTORIQUE" else "PROCHAINS SERVICES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0066CC),
                letterSpacing = 0.5.sp
            )

            Button(
                onClick = { showHistory.value = !showHistory.value },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showHistory.value) Color(0xFF0066CC) else Color(0xFFE3F2FD),
                    contentColor = if (showHistory.value) Color.White else Color(0xFF0066CC)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.History, contentDescription = "", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (showHistory.value) "Historique" else "Voir historique",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoading.value) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF0066CC)
                )
            } else {
                if (displayedServices.isEmpty()) {
                    Text(
                        text = if (showHistory.value) "Aucun service passé" else "Aucun service futur",
                        fontSize = 14.sp,
                        color = Color(0xFF627C7D),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .padding(bottom = 20.dp)
                    ) {
                        displayedServices.forEach { service ->
                            ServiceVignette(
                                service = service,
                                onClick = {
                                    navController.navigate("viewService/${service.id}")
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showMenuDialog.value = true },
                containerColor = Color(0xFF0066CC),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 20.dp, end = 16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter un service", modifier = Modifier.size(28.dp))
            }
        }
    }

    if (showMenuDialog.value) {
        AddServiceMenuDialog(
            onDismiss = { showMenuDialog.value = false },
            onImportPDF = {
                showMenuDialog.value = false
                navController.navigate(Screen.ImportPDF.route)
            },
            onAddManually = {
                showMenuDialog.value = false
                navController.navigate(Screen.AddService.route)
            }
        )
    }
}

@Composable
fun AddServiceMenuDialog(
    onDismiss: () -> Unit,
    onImportPDF: () -> Unit,
    onAddManually: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Ajouter un service",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF134252),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF0066CC), RoundedCornerShape(12.dp))
                        .clickable { onImportPDF() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F9FF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = "Importer PDF",
                            tint = Color(0xFF0066CC),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Importer la quinzaine",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF134252)
                            )
                            Text(
                                text = "Importer depuis un fichier PDF",
                                fontSize = 12.sp,
                                color = Color(0xFF627C7D),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF0066CC), RoundedCornerShape(12.dp))
                        .clickable { onAddManually() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F9FF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Ajouter manuellement",
                            tint = Color(0xFF0066CC),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Ajouter manuellement",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF134252)
                            )
                            Text(
                                text = "Créer un service manuellement",
                                fontSize = 12.sp,
                                color = Color(0xFF627C7D),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Annuler", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun ServiceVignette(service: PlanningService, onClick: () -> Unit) {
    val context = LocalContext.current
    val planchetteUri = remember { mutableStateOf<Uri?>(null) }
    var showFullscreenPlanchette by remember { mutableStateOf(false) }

    LaunchedEffect(service.service, service.partie1Debut, service.partie1Fin) {
        planchetteUri.value = PlanchetteManager.getPlanchette(
            context,
            service.service,
            service.partie1Debut,
            service.partie1Fin
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.material3.CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(70.dp)
                        .background(Color(0xFF0066CC), RoundedCornerShape(1.dp))
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "${service.jour} ${service.date}",
                        fontSize = 10.sp,
                        color = Color(0xFF0066CC),
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = service.service,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF134252)
                        )

                        service.lignes.forEach { ligne ->
                            Text(
                                text = ligne,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0066CC)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (service.partie1Debut.isNotEmpty() && service.partie1Fin.isNotEmpty()) {
                            Text(
                                text = "${service.partie1Debut} - ${service.partie1Fin}",
                                fontSize = 10.sp,
                                color = Color(0xFF627C7D),
                                fontWeight = FontWeight.Normal
                            )
                        }

                        if (service.partie2Debut != null && service.partie2Fin != null) {
                            Text(
                                text = "${service.partie2Debut} - ${service.partie2Fin}",
                                fontSize = 10.sp,
                                color = Color(0xFF627C7D),
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (planchetteUri.value != null) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
                            .background(Color(0xFFF5F5F5))
                            .clickable { showFullscreenPlanchette = true }
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(planchetteUri.value),
                            contentDescription = "Planchette",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.width(60.dp)
                ) {
                    Text(
                        text = "🚌",
                        fontSize = 24.sp
                    )

                    if (service.bus.isEmpty()) {
                        Text(
                            text = "Aucun",
                            fontSize = 9.sp,
                            color = Color(0xFF0066CC),
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        service.bus.forEach { bus ->
                            Text(
                                text = bus,
                                fontSize = 10.sp,
                                color = Color(0xFF134252),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFullscreenPlanchette && planchetteUri.value != null) {
        PlanchetteFullscreenFromVignette(
            uri = planchetteUri.value!!,
            onDismiss = { showFullscreenPlanchette = false }
        )
    }
}

@Composable
fun PlanchetteFullscreenFromVignette(uri: Uri, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = "Planchette plein écran",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale *= zoom
                            scale = scale.coerceIn(1f, 5f)

                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentScale = ContentScale.Fit
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Fermer",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            if (scale == 1f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Pincez pour zoomer • Glissez pour déplacer",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ✅ Lecture de partie1.bus et partie2.bus
fun fetchAllServices(db: FirebaseFirestore, userId: String, callback: (services: List<PlanningService>) -> Unit) {
    db.collection("users")
        .document(userId)
        .collection("services")
        .get()
        .addOnSuccessListener { snapshot ->
            try {
                val services = mutableListOf<PlanningService>()

                for (doc in snapshot.documents) {
                    try {
                        val data = doc.data
                        if (data != null) {
                            val dateService = data["date_service"] as? String ?: ""
                            val serviceNum = data["service"] as? String ?: ""

                            // ✅ Lecture partie1 avec LISTES
                            val partie1 = data["partie1"] as? Map<*, *>
                            val p1Debut = partie1?.get("heure_debut") as? String ?: ""
                            val p1Fin = partie1?.get("heure_fin") as? String ?: ""
                            val p1Lignes = (partie1?.get("lignes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            val p1Bus = (partie1?.get("bus") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                            // ✅ Lecture partie2 avec LISTES
                            val partie2 = data["partie2"] as? Map<*, *>
                            val p2Debut = partie2?.get("heure_debut") as? String
                            val p2Fin = partie2?.get("heure_fin") as? String
                            val p2Lignes = (partie2?.get("lignes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            val p2Bus = (partie2?.get("bus") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                            // ✅ Combiner les listes pour affichage global
                            val lignes = p1Lignes + p2Lignes
                            val buses = p1Bus + p2Bus

                            val service = PlanningService(
                                id = doc.id,
                                date = formatDate(dateService),
                                jour = formatJour(dateService),
                                service = serviceNum,
                                dateservice = dateService,
                                lignes = lignes,
                                bus = buses,
                                partie1Debut = p1Debut,
                                partie1Fin = p1Fin,
                                partie1Lignes = p1Lignes,
                                partie1Bus = p1Bus,
                                partie2Debut = p2Debut,
                                partie2Fin = p2Fin,
                                partie2Lignes = p2Lignes,
                                partie2Bus = p2Bus
                            )

                            services.add(service)
                        }
                    } catch (e: Exception) {
                        Log.e("Planning", "Erreur parse service", e)
                    }
                }

                callback(services)

            } catch (e: Exception) {
                Log.e("Planning", "Erreur générale", e)
                callback(emptyList())
            }
        }
        .addOnFailureListener { exception ->
            Log.e("Planning", "Erreur Firestore", exception)
            callback(emptyList())
        }
}

fun formatDate(dateString: String): String {
    return try {
        val parts = dateString.split("/")
        if (parts.size == 3) {
            val day = parts[0]
            val month = parts[1]
            val monthNames = listOf("", "janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
            val monthName = if (month.toIntOrNull() in 1..12) monthNames[month.toInt()] else month
            "$day $monthName"
        } else dateString
    } catch (e: Exception) {
        Log.e("Planning", "Erreur formatDate: $dateString", e)
        dateString
    }
}

fun formatJour(dateString: String): String {
    return try {
        val parts = dateString.split("/")
        if (parts.size == 3) {
            val day = parts[0].toInt()
            val month = parts[1].toInt()
            val year = parts[2].toInt()
            val date = LocalDate.of(year, month, day)
            val jour = date.format(DateTimeFormatter.ofPattern("EEEE", java.util.Locale("fr", "FR")))
            jour.replaceFirstChar { it.uppercase() }
        } else ""
    } catch (e: Exception) {
        Log.e("Planning", "Erreur formatJour: $dateString", e)
        ""
    }
}
