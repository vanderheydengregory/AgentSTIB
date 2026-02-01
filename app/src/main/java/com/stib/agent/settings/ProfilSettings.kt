package com.stib.agent.ui.settings

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserDepotsCategories(
    val metro: List<String> = emptyList(),
    val tram: List<String> = emptyList(),
    val bus: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilSettings(navController: NavController) {
    var prenom by remember { mutableStateOf("") }
    var nom by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var telephone by remember { mutableStateOf("+32") }
    var depot by remember { mutableStateOf("") }
    var syndicat by remember { mutableStateOf("aucun") }

    var isLoading by remember { mutableStateOf(true) }
    var saveMessage by remember { mutableStateOf("") }

    var depotsCategories by remember { mutableStateOf(UserDepotsCategories()) }
    var syndicatList by remember { mutableStateOf<List<String>>(listOf("aucun")) }

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()

// ✅ AUTOSAVE - Sauvegarder automatiquement
    LaunchedEffect(prenom, nom, email, telephone, depot, syndicat) {
        if (!isLoading && prenom.isNotBlank() && nom.isNotBlank() && email.isNotBlank() && depot.isNotBlank()) {
            delay(1000) // Attendre 1 seconde après la dernière modification

            try {
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    val updates = hashMapOf<String, Any>(
                        "prenom" to prenom,
                        "nom" to nom,
                        "email" to email,
                        "telephone" to telephone,
                        "depot" to depot,
                        "syndicat" to syndicat
                    )

                    db.collection("users")
                        .document(userId)
                        .update(updates)
                        .await()

                    saveMessage = "✅ Sauvegarde automatique"
                    Log.d("ProfilSettings", "✅ Autosave réussi")

                    delay(2000)
                    saveMessage = ""
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // ← AJOUTER CETTE LIGNE : Ignorer les annulations de coroutine
                throw e
            } catch (e: Exception) {
                saveMessage = "❌ Erreur: ${e.message}"
                Log.e("ProfilSettings", "❌ Erreur autosave", e)
            }
        }
    }


    // Charger les données initiales
    LaunchedEffect(Unit) {
        try {
            // Charger les dépôts
            val depotsDoc = db.collection("categories").document("depots").get().await()
            val metroArray = (depotsDoc.get("Metro") as? List<String>) ?: emptyList()
            val tramArray = (depotsDoc.get("Tram") as? List<String>) ?: emptyList()
            val busArray = (depotsDoc.get("Bus") as? List<String>) ?: emptyList()

            depotsCategories = UserDepotsCategories(
                metro = metroArray,
                tram = tramArray,
                bus = busArray
            )

            // Charger les syndicats
            val syndicatsDoc = db.collection("categories").document("Syndicat").get().await()
            val syndiList = mutableListOf("aucun")
            val syndicatsArray = syndicatsDoc.get("nom") as? List<String> ?: emptyList()
            syndiList.addAll(syndicatsArray)
            syndicatList = syndiList

            // Charger les données utilisateur
            val userId = auth.currentUser?.uid
            if (userId != null) {
                val document = db.collection("users").document(userId).get().await()

                prenom = document.getString("prenom") ?: ""
                nom = document.getString("nom") ?: ""
                email = document.getString("email") ?: ""
                val storedPhone = document.getString("telephone") ?: ""
                depot = document.getString("depot") ?: ""
                syndicat = document.getString("syndicat") ?: "aucun"

                telephone = if (storedPhone.isNotEmpty()) {
                    if (storedPhone.startsWith("+32")) storedPhone else "+32$storedPhone"
                } else {
                    "+32"
                }

                isLoading = false
            }
        } catch (e: Exception) {
            saveMessage = "❌ Erreur de chargement"
            isLoading = false
            Log.e("ProfilSettings", "❌ Erreur chargement", e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFCF9))
    ) {
        // ========== MESSAGE DE SAUVEGARDE ==========
        if (saveMessage.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (saveMessage.contains("✅")) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = saveMessage,
                    color = if (saveMessage.contains("✅")) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isLoading) {
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // ========== PRÉNOM & NOM (ROW COMPACT) ==========
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Prénom",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF62636D),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        TextField(
                            value = prenom,
                            onValueChange = {
                            prenom = it.lowercase().replaceFirstChar { char -> char.uppercase() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedIndicatorColor = Color(0xFF0066CC),
                                unfocusedIndicatorColor = Color(0xFFDDDDDD)
                            ),
                            singleLine = true
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nom",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF62636D),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        TextField(
                            value = nom,
                            onValueChange = {
                            nom = it.lowercase().replaceFirstChar { char -> char.uppercase() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedIndicatorColor = Color(0xFF0066CC),
                                unfocusedIndicatorColor = Color(0xFFDDDDDD)
                            ),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ========== EMAIL ==========
                Text(
                    text = "Email",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF62636D),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                TextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color(0xFF0066CC),
                        unfocusedIndicatorColor = Color(0xFFDDDDDD)
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ========== TÉLÉPHONE ==========
                Text(
                    text = "Téléphone",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF62636D),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFFE8F0FF),
                                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Text(
                            text = "+32",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0066CC)
                        )
                    }

                    TextField(
                        value = telephone.removePrefix("+32"),
                        onValueChange = { newValue ->
                            val digitsOnly = newValue.filter { it.isDigit() }
                            telephone = "+32" + digitsOnly.take(9)
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = Color(0xFF0066CC),
                            unfocusedIndicatorColor = Color(0xFFDDDDDD)
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("XXX XX XX XX", fontSize = 14.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ========== DÉPÔT & SYNDICAT (ROW COMPACT) ==========
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // DÉPÔT
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dépôt",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF62636D),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        var expandedDepot by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expandedDepot = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF0066CC)
                                )
                            ) {
                                Text(
                                    if (depot.isEmpty()) "Sélectionner" else depot,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            }

                            DropdownMenu(
                                expanded = expandedDepot,
                                onDismissRequest = { expandedDepot = false },
                                modifier = Modifier.fillMaxWidth(0.5f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("🚇 MÉTRO", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                    enabled = false,
                                    onClick = { }
                                )
                                depotsCategories.metro.forEach { depo ->
                                    DropdownMenuItem(
                                        text = { Text("  └─ $depo", fontSize = 12.sp) },
                                        onClick = {
                                            depot = depo
                                            expandedDepot = false
                                        }
                                    )
                                }

                                Divider()

                                DropdownMenuItem(
                                    text = { Text("🚊 TRAM", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                    enabled = false,
                                    onClick = { }
                                )
                                depotsCategories.tram.forEach { depo ->
                                    DropdownMenuItem(
                                        text = { Text("  └─ $depo", fontSize = 12.sp) },
                                        onClick = {
                                            depot = depo
                                            expandedDepot = false
                                        }
                                    )
                                }

                                Divider()

                                DropdownMenuItem(
                                    text = { Text("🚌 BUS", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                    enabled = false,
                                    onClick = { }
                                )
                                depotsCategories.bus.forEach { depo ->
                                    DropdownMenuItem(
                                        text = { Text("  └─ $depo", fontSize = 12.sp) },
                                        onClick = {
                                            depot = depo
                                            expandedDepot = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // SYNDICAT
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Syndicat",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF62636D),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        var expandedSyndicat by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expandedSyndicat = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF0066CC)
                                )
                            ) {
                                Text(
                                    when (syndicat) {
                                        "aucun" -> "❌ Aucun"
                                        else -> syndicat
                                    },
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            }

                            DropdownMenu(
                                expanded = expandedSyndicat,
                                onDismissRequest = { expandedSyndicat = false }
                            ) {
                                syndicatList.forEach { synd ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (synd) {
                                                    "aucun" -> "❌ Aucun"
                                                    else -> synd
                                                },
                                                fontSize = 12.sp
                                            )
                                        },
                                        onClick = {
                                            syndicat = synd
                                            expandedSyndicat = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
