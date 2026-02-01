package com.stib.agent.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class NewsItem(
    val newsId: String = "",
    val titre: String = "",
    val contenu: String = "",
    val depots: List<String> = emptyList(),
    val syndicat: String = "all",
    val actif: Boolean = true,
    val createdAt: Long = 0L,
    val createdBy: String = ""
)

@Composable
fun NewsScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    var newsList by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var filteredNewsList by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var userSyndicat by remember { mutableStateOf("") }
    var userDepot by remember { mutableStateOf("") }

    // Charger les données utilisateur et les news
    LaunchedEffect(Unit) {
        try {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                // Charger les infos de l'utilisateur
                val userDoc = db.collection("users").document(userId).get().await()
                userSyndicat = userDoc.getString("syndicat") ?: "aucun"
                userDepot = userDoc.getString("depot") ?: ""

                Log.d("NewsScreen", "✅ Utilisateur: syndicat=$userSyndicat, depot=$userDepot")

                // ✅ CHARGER SANS orderBy (pour éviter les problèmes d'index)
                val newsSnapshot = db.collection("news")
                    .whereEqualTo("actif", true)
                    .get()
                    .await()

                val allNews = mutableListOf<NewsItem>()
                for (doc in newsSnapshot.documents) {
                    val timestamp = doc.getTimestamp("createdAt")
                    val createdAtMillis = timestamp?.toDate()?.time ?: System.currentTimeMillis()

                    val news = NewsItem(
                        newsId = doc.id,
                        titre = doc.getString("titre") ?: "",
                        contenu = doc.getString("contenu") ?: "",
                        depots = doc.get("depots") as? List<String> ?: listOf("all"),
                        syndicat = doc.getString("syndicat") ?: "all",
                        actif = doc.getBoolean("actif") ?: true,
                        createdAt = createdAtMillis,
                        createdBy = doc.getString("createdBy") ?: ""
                    )
                    allNews.add(news)
                }

                // ✅ TRIER PAR DATE DÉCROISSANTE EN KOTLIN
                allNews.sortByDescending { it.createdAt }

                newsList = allNews

                // ✅ FILTRER LES NEWS SELON LE SYNDICAT ET DÉPÔT DE L'UTILISATEUR
                filteredNewsList = allNews.filter { news ->
                    // Vérifier le syndicat
                    val syndicatMatch = news.syndicat == "all" || news.syndicat == userSyndicat

                    // Vérifier le dépôt
                    val depotMatch = news.depots.contains("all") || news.depots.contains(userDepot)

                    Log.d("NewsScreen", "🔍 News '${news.titre}': syndicat=$syndicatMatch, depot=$depotMatch")

                    syndicatMatch && depotMatch
                }

                Log.d("NewsScreen", "✅ ${newsList.size} news chargées, ${filteredNewsList.size} visibles pour cet utilisateur")
                isLoading = false
            }
        } catch (e: Exception) {
            Log.e("NewsScreen", "❌ Erreur: ${e.message}", e)
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFCF9))
            .padding(16.dp)
    ) {
        // Titre
        Text(
            text = "📰 Actualités",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF134252),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF0066CC))
            }
        } else if (filteredNewsList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📭",
                        fontSize = 48.sp
                    )
                    Text(
                        text = "Aucune actualité disponible",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF134252)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredNewsList) { news ->
                    NewsCard(news = news)
                }
            }
        }
    }
}

@Composable
fun NewsCard(news: NewsItem) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
    val dateString = dateFormat.format(Date(news.createdAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Titre
            Text(
                text = news.titre,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF134252)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Contenu
            Text(
                text = news.contenu,
                fontSize = 14.sp,
                color = Color(0xFF627C7D),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Informations supplémentaires
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dépôt
                if (news.depots.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF0066CC)
                    ) {
                        Text(
                            text = if (news.depots.contains("all")) "TOUS" else news.depots.take(2).joinToString(", "),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                // Date
                Text(
                    text = dateString,
                    fontSize = 11.sp,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}
