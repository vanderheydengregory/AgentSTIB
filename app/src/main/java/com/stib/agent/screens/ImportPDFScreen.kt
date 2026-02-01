package com.stib.agent.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
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
import com.stib.agent.utils.PDFParser
import com.stib.agent.utils.ParsedService
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.stib.agent.notifications.ServiceAlarmScheduler
import com.stib.agent.data.model.Service

@Composable
fun ImportPDFScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser

    var isLoading by remember { mutableStateOf(false) }
    var parsedServices by remember { mutableStateOf<List<ParsedService>>(emptyList()) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var duplicatesCount by remember { mutableStateOf(0) }
    var newServicesCount by remember { mutableStateOf(0) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var newServices by remember { mutableStateOf<List<ParsedService>>(emptyList()) }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        android.util.Log.d("ImportPDF", "📎 Callback appelé - URI: $uri")
        if (uri != null) {
            android.util.Log.d("ImportPDF", "✅ URI valide, début du traitement")
            isLoading = true
            errorMessage = ""
            successMessage = ""
            duplicatesCount = 0
            newServicesCount = 0

            scope.launch {
                try {
                    android.util.Log.d("ImportPDF", "📄 Début parsing du PDF")
                    val services = PDFParser.parsePDFAdvanced(context, uri)
                    android.util.Log.d("ImportPDF", "✅ ${services.size} services parsés")

                    parsedServices = services
                    isLoading = false

                    if (services.isEmpty()) {
                        errorMessage = "Aucun service trouvé dans le PDF"
                        android.util.Log.w("ImportPDF", "⚠️ PDF parsé mais aucun service trouvé")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ImportPDF", "❌ Erreur complète: ${e.message}", e)
                    e.printStackTrace()
                    errorMessage = "Erreur: ${e.message}"
                    isLoading = false
                }
            }
        } else {
            android.util.Log.d("ImportPDF", "⚠️ Aucun fichier sélectionné (URI null)")
        }
    }

    // ✅ AlertDialog de confirmation avant l'import
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    "⚠️ Vérifier les données",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        "Avant d'importer, vérifiez que toutes les données sont correctes :",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(12.dp)) {
                            items(newServices.take(5)) { service ->                    Text(
                                "• ${service.date} - Service ${service.service} (${service.partie1.ligne})",
                                fontSize = 12.sp,
                                color = Color(0xFF627C7D),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            }
                            if (newServices.size > 5) {
                                item {
                                    Text(
                                        "... et ${newServices.size - 5} autres",
                                        fontSize = 12.sp,
                                        color = Color(0xFF0066CC),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "Au total: ${newServices.size} nouveaux services (${duplicatesCount} doublons ignorés)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        // Lance l'import pour de vrai
                        performImport(
                            context = context,
                            db = db,
                            currentUser = currentUser,
                            newServices = newServices,
                            scope = scope,
                            onSuccess = { message ->
                                successMessage = message
                                isImporting = false
                            },
                            onError = { message ->
                                errorMessage = message
                                isImporting = false
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("✅ Confirmer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("❌ Annuler", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFCF9))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0066CC))
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column {
                Text(
                    text = "📄 Import PDF",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Importez votre planning STIB",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFF0066CC),
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Analyse du PDF en cours...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF627C7D)
                        )
                    }
                }
            }

            parsedServices.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = "PDF",
                        tint = Color(0xFF0066CC),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Chargez votre quinzaine",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF134252)
                    )
                    Text(
                        "Sélectionnez un PDF de planning STIB",
                        fontSize = 14.sp,
                        color = Color(0xFF627C7D),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            android.util.Log.d("ImportPDF", "🖱️ Bouton cliqué")
                            pdfPicker.launch("application/pdf")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0066CC)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Upload", modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Sélectionner un PDF",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFEBEE)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Erreur",
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    errorMessage,
                                    fontSize = 13.sp,
                                    color = Color(0xFFE53935)
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "✅ PDF analysé avec succès",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    "${parsedServices.size} services trouvés",
                                    fontSize = 13.sp,
                                    color = Color(0xFF558B2F),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    parsedServices = emptyList()
                                    successMessage = ""
                                    errorMessage = ""
                                    pdfPicker.launch("application/pdf")
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF2E7D32)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Autre PDF", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        items(parsedServices) { service ->
                            ServicePreviewCard(service)
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        item {
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(16.dp)
                    ) {
                        if (successMessage.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFE8F5E9)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Succès",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            successMessage,
                                            fontSize = 14.sp,
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (duplicatesCount > 0) {
                                            Text(
                                                "$duplicatesCount doublons ignorés",
                                                fontSize = 12.sp,
                                                color = Color(0xFF558B2F),
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (errorMessage.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFEBEE)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Erreur",
                                        tint = Color(0xFFE53935),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        errorMessage,
                                        fontSize = 13.sp,
                                        color = Color(0xFFE53935)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (currentUser != null) {
                                    isImporting = true
                                    errorMessage = ""
                                    successMessage = ""
                                    duplicatesCount = 0
                                    newServicesCount = 0

                                    scope.launch {
                                        try {
                                            android.util.Log.d("ImportPDF", "🔍 Vérification des doublons...")

                                            val existingServicesSnapshot = db.collection("users")
                                                .document(currentUser.uid)
                                                .collection("services")
                                                .get()
                                                .await()

                                            android.util.Log.d("ImportPDF", "📊 ${existingServicesSnapshot.size()} services en base")

                                            val existingKeys = mutableSetOf<String>()
                                            existingServicesSnapshot.documents.forEach { doc ->
                                                val date = doc.getString("date_service")
                                                val service = doc.getString("service")
                                                if (date != null && service != null) {
                                                    val key = "$date-$service"
                                                    existingKeys.add(key)
                                                    android.util.Log.d("ImportPDF", "   Existant: $key")
                                                }
                                            }

                                            android.util.Log.d("ImportPDF", "📋 ${existingKeys.size} clés uniques en base")

                                            val filteredNewServices = mutableListOf<ParsedService>()
                                            parsedServices.forEach { service ->
                                                val key = "${service.date}-${service.service}"
                                                android.util.Log.d("ImportPDF", "🔍 Test: $key")

                                                if (existingKeys.contains(key)) {
                                                    android.util.Log.d("ImportPDF", "   ⚠️ Doublon trouvé: $key")
                                                    duplicatesCount++
                                                } else {
                                                    android.util.Log.d("ImportPDF", "   ✅ Nouveau: $key")
                                                    filteredNewServices.add(service)
                                                }
                                            }

                                            android.util.Log.d("ImportPDF", "📊 Résultat: ${filteredNewServices.size} nouveaux, $duplicatesCount doublons")

                                            if (filteredNewServices.isEmpty()) {
                                                successMessage = "Tous les services sont déjà importés"
                                                isImporting = false
                                                return@launch
                                            }

                                            // ✅ Afficher la dialog de confirmation
                                            newServices = filteredNewServices
                                            showConfirmDialog = true
                                            isImporting = false

                                        } catch (e: Exception) {
                                            errorMessage = "Erreur lors de la vérification: ${e.message}"
                                            isImporting = false
                                            android.util.Log.e("ImportPDF", "❌ Erreur: ${e.message}", e)
                                            e.printStackTrace()
                                        }
                                    }
                                } else {
                                    errorMessage = "Utilisateur non connecté"
                                    android.util.Log.e("ImportPDF", "❌ Pas d'utilisateur connecté")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0066CC)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isImporting,
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Vérification...",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    "Importer ${parsedServices.size} services",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ✅ Fonction utilitaire pour effectuer l'import
private fun performImport(
    context: android.content.Context,
    db: FirebaseFirestore,
    currentUser: com.google.firebase.auth.FirebaseUser?,
    newServices: List<ParsedService>,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    scope.launch {
        try {
            android.util.Log.d("ImportPDF", "🚀 Import de ${newServices.size} services...")

            var newServicesCount = 0

            newServices.forEach { service ->
                // ✅ Convertir ligne (string) en lignes (list) pour Firebase
                val partie1Data = hashMapOf(
                    "heure_debut" to service.partie1.heure_debut,
                    "heure_fin" to service.partie1.heure_fin,
                    "lignes" to listOf(service.partie1.ligne)
                )

                val serviceData = hashMapOf(
                    "date_service" to service.date,
                    "service" to service.service,
                    "date_import" to service.date_import,
                    "note" to service.note,
                    "planchette" to service.planchette,
                    "partie1" to partie1Data
                )

                if (service.partie2 != null) {
                    val partie2Data = hashMapOf(
                        "heure_debut" to service.partie2.heure_debut,
                        "heure_fin" to service.partie2.heure_fin,
                        "lignes" to listOf(service.partie2.ligne)
                    )
                    serviceData["partie2"] = partie2Data
                }

                db.collection("users")
                    .document(currentUser!!.uid)
                    .collection("services")
                val documentRef = db.collection("users")
                    .document(currentUser!!.uid)
                    .collection("services")
                    .add(serviceData)
                    .await()

                newServicesCount++
                android.util.Log.d("ImportPDF", "✅ Importé: ${service.service} ($newServicesCount/${newServices.size})")
                // Planifier les alarmes pour le service importé
                try {
                    val scheduler = ServiceAlarmScheduler(context)
                    scheduler.scheduleAllAlarmsForService(documentRef.id)
                    android.util.Log.d("ImportPDF", "✅ Alarmes planifiées pour ${service.service}")
                } catch (e: Exception) {
                    android.util.Log.e("ImportPDF", "❌ Erreur alarmes: ${e.message}", e)
                }

            }

            onSuccess("✅ $newServicesCount services importés")

            android.util.Log.d("ImportPDF", "🎉 Import terminé avec succès")

        } catch (e: Exception) {
            onError("Erreur lors de l'import: ${e.message}")
            android.util.Log.e("ImportPDF", "❌ Erreur import: ${e.message}", e)
            e.printStackTrace()
        }
    }
}

@Composable
fun ServicePreviewCard(service: ParsedService) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (service.partie2 != null) 80.dp else 60.dp)
                    .background(Color(0xFF0066CC), RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    service.date,
                    fontSize = 11.sp,
                    color = Color(0xFF0066CC),
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        service.service,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF134252)
                    )

                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE3F2FD), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            service.partie1.ligne,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0066CC)
                        )
                    }
                }

                Text(
                    "${service.partie1.heure_debut} - ${service.partie1.heure_fin}",
                    fontSize = 11.sp,
                    color = Color(0xFF627C7D),
                    modifier = Modifier.padding(top = 6.dp)
                )

                if (service.partie2 != null) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${service.partie2.heure_debut} - ${service.partie2.heure_fin}",
                            fontSize = 11.sp,
                            color = Color(0xFF627C7D)
                        )

                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE3F2FD), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                service.partie2.ligne,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0066CC)
                            )
                        }
                    }
                }
            }

            if (service.partie2 != null) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFF3E0), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Coupé",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                }
            }
        }
    }
}