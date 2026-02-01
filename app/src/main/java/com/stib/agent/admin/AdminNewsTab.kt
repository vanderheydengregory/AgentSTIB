package com.stib.agent.ui.admin

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class NewsData(
    val newsId: String = "",
    val titre: String = "",
    val contenu: String = "",
    val depots: List<String> = emptyList(),
    val syndicat: String = "all",
    val actif: Boolean = true,
    val createdAt: Long = 0L,
    val createdBy: String = ""
)

data class DepotsCategories(
    val metro: List<String> = emptyList(),
    val tram: List<String> = emptyList(),
    val bus: List<String> = emptyList()
)

val EMOJI_LIST = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
    "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
    "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜",
    "🤪", "😌", "😔", "😑", "😐", "😶", "🥱", "😏",
    "😒", "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤",
    "😴", "😷", "🤒", "🤕", "🤮", "🤢", "🤮", "🤮",
    "⚠️", "🔥", "💡", "✅", "❌", "✨", "🎯", "🚀",
    "📢", "📣", "📞", "💬", "💼", "📊", "📈", "📉"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminNewsTab() {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    var newsList by remember { mutableStateOf<List<NewsData>>(emptyList()) }
    var depotsCategories by remember { mutableStateOf(DepotsCategories()) }
    var syndicatList by remember { mutableStateOf<List<String>>(listOf("all")) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedNews by remember { mutableStateOf<NewsData?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var newsToDelete by remember { mutableStateOf<NewsData?>(null) }

    // Charger les catégories et news
    LaunchedEffect(Unit) {
        try {
            // Charger les dépôts (metro, tram, bus)
            val depotsDoc = db.collection("categories").document("depots").get().await()

            val metroArray = (depotsDoc.get("Metro") as? List<String>) ?: emptyList()
            val tramArray = (depotsDoc.get("Tram") as? List<String>) ?: emptyList()
            val busArray = (depotsDoc.get("Bus") as? List<String>) ?: emptyList()

            depotsCategories = DepotsCategories(
                metro = metroArray,
                tram = tramArray,
                bus = busArray
            )

            Log.d("AdminNews", "✅ Dépôts METRO: $metroArray")
            Log.d("AdminNews", "✅ Dépôts TRAM: $tramArray")
            Log.d("AdminNews", "✅ Dépôts BUS: $busArray")

            // Charger les syndicats
            val syndicatsDoc = db.collection("categories").document("Syndicat").get().await()
            val syndiList = mutableListOf("all")
            val syndicatsArray = syndicatsDoc.get("nom") as? List<String> ?: emptyList()
            syndiList.addAll(syndicatsArray)
            syndicatList = syndiList

            Log.d("AdminNews", "✅ Syndicats: $syndicatsArray")

            // Charger les news
            val newsSnapshot = db.collection("news")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            val list = mutableListOf<NewsData>()
            for (doc in newsSnapshot.documents) {
                val timestamp = doc.getTimestamp("createdAt")
                val createdAtMillis = timestamp?.toDate()?.time ?: System.currentTimeMillis()

                val news = NewsData(
                    newsId = doc.id,
                    titre = doc.getString("titre") ?: "",
                    contenu = doc.getString("contenu") ?: "",
                    depots = doc.get("depots") as? List<String> ?: listOf("all"),
                    syndicat = doc.getString("syndicat") ?: "all",
                    actif = doc.getBoolean("actif") ?: true,
                    createdAt = createdAtMillis,
                    createdBy = doc.getString("createdBy") ?: ""
                )
                list.add(news)
            }

            newsList = list
            isLoading = false
            Log.d("AdminNews", "✅ ${newsList.size} news chargées")
        } catch (e: Exception) {
            Log.e("AdminNews", "❌ Erreur: ${e.message}", e)
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF0066CC))
            }
        } else {
            // Header avec bouton créer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📰 Gestion des actualités",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF134252)
                    )
                    Text(
                        text = "${newsList.count { it.actif }} actives • ${newsList.size} total",
                        fontSize = 12.sp,
                        color = Color(0xFF999999)
                    )
                }

                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Créer", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Créer une news")
                }
            }

            // Liste des news
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(newsList) { news ->
                    NewsCardAdvanced(
                        news = news,
                        onEdit = {
                            selectedNews = news
                            showEditDialog = true
                        },
                        onToggleActive = {
                            db.collection("news")
                                .document(news.newsId)
                                .update("actif", !news.actif)
                                .addOnSuccessListener {
                                    newsList = newsList.map {
                                        if (it.newsId == news.newsId) it.copy(actif = !news.actif) else it
                                    }
                                    Log.d("AdminNews", "✅ News ${if (!news.actif) "activée" else "désactivée"}")
                                }
                        },
                        onDelete = {
                            newsToDelete = news
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }

    // Dialog de création
    if (showCreateDialog) {
        CreateNewsAdvancedDialog(
            depotsCategories = depotsCategories,
            syndicatList = syndicatList,
            onDismiss = { showCreateDialog = false },
            onCreate = { titre, contenu, depots, syndicat ->
                val newsData = hashMapOf(
                    "titre" to titre,
                    "contenu" to contenu,
                    "depots" to depots,
                    "syndicat" to syndicat,
                    "actif" to true,
                    "createdAt" to Timestamp.now(),
                    "createdBy" to (auth.currentUser?.uid ?: "")
                )

                db.collection("news")
                    .add(newsData)
                    .addOnSuccessListener { docRef ->
                        val newNews = NewsData(
                            newsId = docRef.id,
                            titre = titre,
                            contenu = contenu,
                            depots = depots,
                            syndicat = syndicat,
                            actif = true,
                            createdAt = System.currentTimeMillis(),
                            createdBy = auth.currentUser?.uid ?: ""
                        )
                        newsList = listOf(newNews) + newsList
                        showCreateDialog = false
                        Log.d("AdminNews", "✅ News créée avec dépôts: $depots")
                    }
            }
        )
    }

    // Dialog d'édition
    if (showEditDialog && selectedNews != null) {
        EditNewsAdvancedDialog(
            news = selectedNews!!,
            depotsCategories = depotsCategories,
            syndicatList = syndicatList,
            onDismiss = { showEditDialog = false },
            onSave = { updatedNews ->
                db.collection("news")
                    .document(updatedNews.newsId)
                    .update(
                        mapOf(
                            "titre" to updatedNews.titre,
                            "contenu" to updatedNews.contenu,
                            "depots" to updatedNews.depots,
                            "syndicat" to updatedNews.syndicat
                        )
                    )
                    .addOnSuccessListener {
                        newsList = newsList.map {
                            if (it.newsId == updatedNews.newsId) updatedNews else it
                        }
                        showEditDialog = false
                        Log.d("AdminNews", "✅ News modifiée")
                    }
            }
        )
    }

    // Dialog de suppression
    if (showDeleteConfirm && newsToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirmer la suppression") },
            text = { Text("Voulez-vous vraiment supprimer cette actualité ?") },
            confirmButton = {
                Button(
                    onClick = {
                        db.collection("news")
                            .document(newsToDelete!!.newsId)
                            .delete()
                            .addOnSuccessListener {
                                newsList = newsList.filter { it.newsId != newsToDelete!!.newsId }
                                showDeleteConfirm = false
                                newsToDelete = null
                                Log.d("AdminNews", "✅ News supprimée")
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C))
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
fun NewsCardAdvanced(
    news: NewsData,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
    val dateString = dateFormat.format(Date(news.createdAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (news.actif) Color.White else Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = news.titre,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF134252)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = news.contenu,
                fontSize = 14.sp,
                color = Color(0xFF627C7D),
                lineHeight = 20.sp,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Affiche les dépôts sélectionnés
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📍 Dépôts:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF627C7D))

                    if (news.depots.contains("all")) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0066CC)
                        ) {
                            Text(
                                text = "TOUS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        news.depots.forEach { depot ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF0066CC)
                            ) {
                                Text(
                                    text = depot,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Affiche le syndicat
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏢 Syndicat:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF627C7D))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF6B6B)
                    ) {
                        Text(
                            text = if (news.syndicat == "all") "TOUS" else news.syndicat,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Affiche la date et le statut
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateString,
                        fontSize = 11.sp,
                        color = Color(0xFF999999)
                    )

                    if (!news.actif) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF999999)
                        ) {
                            Text(
                                text = "DÉSACTIVÉE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Modifier", fontSize = 13.sp)
                }

                Button(
                    onClick = onToggleActive,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (news.actif) Color(0xFFFF9800) else Color(0xFF00CC66)
                    )
                ) {
                    Icon(
                        if (news.actif) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (news.actif) "Désactiver" else "Activer",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (news.actif) "Désactiver" else "Activer", fontSize = 13.sp)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Supprimer",
                        tint = Color(0xFFE74C3C)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNewsAdvancedDialog(
    depotsCategories: DepotsCategories,
    syndicatList: List<String>,
    onDismiss: () -> Unit,
    onCreate: (String, String, List<String>, String) -> Unit
) {
    var titre by remember { mutableStateOf("") }
    var contenu by remember { mutableStateOf("") }
    var selectedDepots by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSyndicat by remember { mutableStateOf("all") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showFormatBar by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📰 Créer une actualité complète") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Titre
                OutlinedTextField(
                    value = titre,
                    onValueChange = { titre = it },
                    label = { Text("Titre *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Contenu avec éditeur riche
                Column {
                    Text("Contenu *", fontSize = 12.sp, color = Color(0xFF999999), fontWeight = FontWeight.Bold)

                    if (showFormatBar) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { contenu += "**" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("B", fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { contenu += "*" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("I", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                            IconButton(
                                onClick = { contenu += "\n• " },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.FormatListBulleted, contentDescription = "Puces", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { showEmojiPicker = !showEmojiPicker },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("😀")
                            }
                        }
                    }

                    if (showEmojiPicker) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items((EMOJI_LIST.size + 7) / 8) { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        repeat(8) { col ->
                                            val index = row * 8 + col
                                            if (index < EMOJI_LIST.size) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
                                                        .clickable {
                                                            contenu += EMOJI_LIST[index]
                                                            showEmojiPicker = false
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(EMOJI_LIST[index], fontSize = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = contenu,
                        onValueChange = { contenu = it },
                        placeholder = { Text("Votre message détaillé ici...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        minLines = 5,
                        maxLines = 8
                    )
                }

                // ✅ SÉLECTION MULTIPLE DE DÉPÔTS - AVEC CHECKBOX "TOUS"
                Column {
                    Text("Dépôt(s) *", fontSize = 12.sp, color = Color(0xFF999999), fontWeight = FontWeight.Bold)
                    var expandedDepot by remember { mutableStateOf(false) }

                    OutlinedButton(
                        onClick = { expandedDepot = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (selectedDepots.isEmpty()) "Sélectionner dépôt(s)"
                            else if (selectedDepots.contains("all")) "TOUS LES DÉPÔTS"
                            else selectedDepots.joinToString(", ")
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = expandedDepot,
                        onDismissRequest = { expandedDepot = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        // Option "Tous les dépôts"
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = selectedDepots.contains("all"),
                                        onCheckedChange = {
                                            selectedDepots = if (it) listOf("all") else emptyList()
                                        }
                                    )
                                    Text("📍 Tous les dépôts", fontWeight = FontWeight.Bold)
                                }
                            },
                            onClick = { }
                        )

                        HorizontalDivider()



                        // METRO - Titre non-cliquable
                        DropdownMenuItem(
                            text = { Text("🚇 MÉTRO", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            enabled = false,
                            onClick = { }
                        )

                        depotsCategories.metro.forEach { depot ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = selectedDepots.contains(depot),
                                            onCheckedChange = {
                                                selectedDepots = if (it) {
                                                    (selectedDepots - "all") + depot
                                                } else {
                                                    selectedDepots - depot
                                                }
                                            }
                                        )
                                        Text(depot, fontSize = 12.sp)
                                    }
                                },
                                onClick = { }
                            )
                        }

                        HorizontalDivider()

                        // TRAM - Titre non-cliquable
                        DropdownMenuItem(
                            text = { Text("🚊 TRAM", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            enabled = false,
                            onClick = { }
                        )

                        depotsCategories.tram.forEach { depot ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = selectedDepots.contains(depot),
                                            onCheckedChange = {
                                                selectedDepots = if (it) {
                                                    (selectedDepots - "all") + depot
                                                } else {
                                                    selectedDepots - depot
                                                }
                                            }
                                        )
                                        Text(depot, fontSize = 12.sp)
                                    }
                                },
                                onClick = { }
                            )
                        }

                        HorizontalDivider()

                        // BUS - Titre non-cliquable
                        DropdownMenuItem(
                            text = { Text("🚌 BUS", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            enabled = false,
                            onClick = { }
                        )

                        depotsCategories.bus.forEach { depot ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = selectedDepots.contains(depot),
                                            onCheckedChange = {
                                                selectedDepots = if (it) {
                                                    (selectedDepots - "all") + depot
                                                } else {
                                                    selectedDepots - depot
                                                }
                                            }
                                        )
                                        Text(depot, fontSize = 12.sp)
                                    }
                                },
                                onClick = { }
                            )
                        }
                    }
                }

                // Sélection Syndicat
                Column {
                    Text("Syndicat(s)", fontSize = 12.sp, color = Color(0xFF999999), fontWeight = FontWeight.Bold)
                    var expandedSyndicat by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { expandedSyndicat = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedSyndicat)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = expandedSyndicat,
                            onDismissRequest = { expandedSyndicat = false }
                        ) {
                            syndicatList.forEach { syndicat ->
                                DropdownMenuItem(
                                    text = { Text(syndicat) },
                                    onClick = {
                                        selectedSyndicat = syndicat
                                        expandedSyndicat = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val depotsToSave = if (selectedDepots.isEmpty()) listOf("all") else selectedDepots
                    onCreate(titre, contenu, depotsToSave, selectedSyndicat)
                },
                enabled = titre.isNotBlank() && contenu.isNotBlank() && selectedDepots.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))
            ) {
                Text("Créer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNewsAdvancedDialog(
    news: NewsData,
    depotsCategories: DepotsCategories,
    syndicatList: List<String>,
    onDismiss: () -> Unit,
    onSave: (NewsData) -> Unit
) {
    var editedNews by remember { mutableStateOf(news) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showFormatBar by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier l'actualité") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Titre
                OutlinedTextField(
                    value = editedNews.titre,
                    onValueChange = { editedNews = editedNews.copy(titre = it) },
                    label = { Text("Titre *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Contenu avec éditeur riche
                Column {
                    Text("Contenu *", fontSize = 12.sp, color = Color(0xFF999999), fontWeight = FontWeight.Bold)

                    if (showFormatBar) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { editedNews = editedNews.copy(contenu = editedNews.contenu + "**") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("B", fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { editedNews = editedNews.copy(contenu = editedNews.contenu + "*") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("I", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                            IconButton(
                                onClick = { editedNews = editedNews.copy(contenu = editedNews.contenu + "\n• ") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.FormatListBulleted, contentDescription = "Puces", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { showEmojiPicker = !showEmojiPicker },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("😀")
                            }
                        }
                    }

                    if (showEmojiPicker) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items((EMOJI_LIST.size + 7) / 8) { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        repeat(8) { col ->
                                            val index = row * 8 + col
                                            if (index < EMOJI_LIST.size) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
                                                        .clickable {
                                                            editedNews = editedNews.copy(contenu = editedNews.contenu + EMOJI_LIST[index])
                                                            showEmojiPicker = false
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(EMOJI_LIST[index], fontSize = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editedNews.contenu,
                        onValueChange = { editedNews = editedNews.copy(contenu = it) },
                        placeholder = { Text("Votre message détaillé ici...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        minLines = 5,
                        maxLines = 8
                    )
                }

                // Sélection Dépôts
                Column {
                    Text("Dépôt(s)", fontSize = 12.sp, color = Color(0xFF999999), fontWeight = FontWeight.Bold)
                    var expandedDepot by remember { mutableStateOf(false) }

                    OutlinedButton(
                        onClick = { expandedDepot = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (editedNews.depots.contains("all")) "TOUS LES DÉPÔTS"
                            else editedNews.depots.joinToString(", ")
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = expandedDepot,
                        onDismissRequest = { expandedDepot = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = editedNews.depots.contains("all"),
                                        onCheckedChange = {
                                            editedNews = editedNews.copy(depots = if (it) listOf("all") else emptyList())
                                        }
                                    )
                                    Text("📍 Tous les dépôts", fontWeight = FontWeight.Bold)
                                }
                            },
                            onClick = { }
                        )

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = { Text("🚇 MÉTRO", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            enabled = false,
                            onClick = { }
                        )

                        depotsCategories.metro.forEach { depot ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = editedNews.depots.contains(depot),
                                            onCheckedChange = {
                                                editedNews = editedNews.copy(depots =
                                                    if (it) {
                                                        (editedNews.depots - "all") + depot
                                                    } else {
                                                        editedNews.depots - depot
                                                    }
                                                )
                                            }
                                        )
                                        Text(depot, fontSize = 12.sp)
                                    }
                                },
                                onClick = { }
                            )
                        }

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = { Text("🚊 TRAM", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            enabled = false,
                            onClick = { }
                        )

                        depotsCategories.tram.forEach { depot ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = editedNews.depots.contains(depot),
                                            onCheckedChange = {
                                                editedNews = editedNews.copy(depots =
                                                    if (it) {
                                                        (editedNews.depots - "all") + depot
                                                    } else {
                                                        editedNews.depots - depot
                                                    }
                                                )
                                            }
                                        )
                                        Text(depot, fontSize = 12.sp)
                                    }
                                },
                                onClick = { }
                            )
                        }

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = { Text("🚌 BUS", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            enabled = false,
                            onClick = { }
                        )

                        depotsCategories.bus.forEach { depot ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = editedNews.depots.contains(depot),
                                            onCheckedChange = {
                                                editedNews = editedNews.copy(depots =
                                                    if (it) {
                                                        (editedNews.depots - "all") + depot
                                                    } else {
                                                        editedNews.depots - depot
                                                    }
                                                )
                                            }
                                        )
                                        Text(depot, fontSize = 12.sp)
                                    }
                                },
                                onClick = { }
                            )
                        }
                    }
                }

                // Sélection Syndicat
                Column {
                    Text("Syndicat(s)", fontSize = 12.sp, color = Color(0xFF999999), fontWeight = FontWeight.Bold)
                    var expandedSyndicat by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { expandedSyndicat = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(editedNews.syndicat)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = expandedSyndicat,
                            onDismissRequest = { expandedSyndicat = false }
                        ) {
                            syndicatList.forEach { syndicat ->
                                DropdownMenuItem(
                                    text = { Text(syndicat) },
                                    onClick = {
                                        editedNews = editedNews.copy(syndicat = syndicat)
                                        expandedSyndicat = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(editedNews) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))
            ) {
                Text("Sauvegarder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
