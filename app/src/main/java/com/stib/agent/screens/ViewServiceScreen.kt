package com.stib.agent.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stib.agent.ui.components.PlanchetteFullscreenDialog
import com.stib.agent.utils.PlanchetteManager
import android.util.Log

@Composable
fun ViewServiceScreen(
    navController: NavController,
    serviceId: String
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser

    var service by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showImageDialog by remember { mutableStateOf(false) }
    var planchetteUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(serviceId) {
        if (currentUser != null) {
            db.collection("users")
                .document(currentUser.uid)
                .collection("services")
                .document(serviceId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        service = document.data

                        val serviceNum = document.getString("service") ?: ""

                        val partie1 = document.get("partie1") as? Map<*, *>
                        val heureDebut = partie1?.get("heure_debut") as? String ?: ""
                        val heureFin = partie1?.get("heure_fin") as? String ?: ""

                        planchetteUri = PlanchetteManager.getPlanchette(
                            context, serviceNum, heureDebut, heureFin
                        )

                        Log.d("ViewService", "📄 Service: $serviceNum")
                        Log.d("ViewService", "📝 Notes: ${document.get("notes")}")
                        Log.d("ViewService", "🖼️ Planchette URI: $planchetteUri")
                    }
                    isLoading = false
                }
                .addOnFailureListener { e ->
                    Log.e("ViewService", "Erreur: ${e.message}", e)
                    isLoading = false
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF0066CC)
            )
        } else if (service != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFCFCF9))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .padding(bottom = 80.dp)
            ) {
                Text(
                    text = "Détails du service",
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
                            text = service?.get("date_service") as? String ?: "N/A",
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
                            text = service?.get("service") as? String ?: "N/A",
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

                        val partie1 = service?.get("partie1") as? Map<*, *>
                        val p1Debut = partie1?.get("heure_debut") as? String ?: "N/A"
                        val p1Fin = partie1?.get("heure_fin") as? String ?: "N/A"

                        val partie2 = service?.get("partie2") as? Map<*, *>
                        val p2Debut = partie2?.get("heure_debut") as? String
                        val p2Fin = partie2?.get("heure_fin") as? String

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "$p1Debut - $p1Fin",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF134252)
                            )

                            if (p2Debut != null && p2Fin != null) {
                                Text(
                                    text = "$p2Debut - $p2Fin",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF134252)
                                )
                            }
                        }
                    }
                }

                Divider(color = Color(0xFFE0E0E0))
                Spacer(modifier = Modifier.height(20.dp))

                // ✅ AFFICHAGE FORMAT TEXTE SIMPLE
                val partie1 = service?.get("partie1") as? Map<*, *>
                val p1Lignes = (partie1?.get("lignes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val p1Bus = (partie1?.get("bus") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                val partie2 = service?.get("partie2") as? Map<*, *>
                val p2Lignes = (partie2?.get("lignes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val p2Bus = (partie2?.get("bus") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                // PARTIE 1
                Text(
                    text = "PARTIE 1",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0066CC),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Lignes: ${if (p1Lignes.isEmpty()) "Aucune" else p1Lignes.joinToString(", ")}",
                    fontSize = 13.sp,
                    color = Color(0xFF134252),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "Bus: ${if (p1Bus.isEmpty()) "Aucun" else p1Bus.joinToString(", ")}",
                    fontSize = 13.sp,
                    color = Color(0xFF134252),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // PARTIE 2 (seulement si existe)
                if (partie2 != null && (p2Lignes.isNotEmpty() || p2Bus.isNotEmpty())) {
                    Text(
                        text = "PARTIE 2",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0066CC),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "Lignes: ${if (p2Lignes.isEmpty()) "Aucune" else p2Lignes.joinToString(", ")}",
                        fontSize = 13.sp,
                        color = Color(0xFF134252),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Text(
                        text = "Bus: ${if (p2Bus.isEmpty()) "Aucun" else p2Bus.joinToString(", ")}",
                        fontSize = 13.sp,
                        color = Color(0xFF134252),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (planchetteUri != null) {
                    Text(
                        text = "📄 PLANCHETTE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0066CC),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    AsyncImage(
                        model = planchetteUri,
                        contentDescription = "Planchette",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .border(2.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                            .clickable { showImageDialog = true },
                        contentScale = ContentScale.Fit
                    )

                    Text(
                        text = "👆 Cliquez pour agrandir",
                        fontSize = 11.sp,
                        color = Color(0xFF999999),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

                val notesData = service?.get("notes")
                val notesList = when (notesData) {
                    is String -> if (notesData.isNotBlank()) listOf(notesData) else emptyList()
                    is List<*> -> notesData.filterIsInstance<String>()
                    else -> emptyList()
                }

                if (notesList.isNotEmpty()) {
                    Text(
                        text = "📝 NOTES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0066CC),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    notesList.forEach { note ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFF8E1),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = note,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 14.sp,
                                color = Color(0xFF134252),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    navController.navigate("editService/$serviceId")
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = Color(0xFF0066CC),
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Modifier"
                )
            }
        } else {
            Text(
                text = "Service introuvable",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 16.sp,
                color = Color(0xFF999999)
            )
        }
    }

    if (showImageDialog && planchetteUri != null) {
        PlanchetteFullscreenDialog(
            uri = planchetteUri!!,
            onDismiss = { showImageDialog = false }
        )
    }
}