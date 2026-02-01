package com.stib.agent.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.stib.agent.ui.admin.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser
    var isAdmin by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }

    // Vérifier le rôle admin
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            try {
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val role = userDoc.getString("role") ?: "user"
                isAdmin = (role == "admin")
                isLoading = false

                Log.d("AdminScreen", "Role vérifié: $role, isAdmin: $isAdmin")

                if (!isAdmin) {
                    // Rediriger si pas admin
                    navController.popBackStack()
                }
            } catch (e: Exception) {
                Log.e("AdminScreen", "Erreur vérification admin: ${e.message}", e)
                isLoading = false
                navController.popBackStack()
            }
        } else {
            Log.e("AdminScreen", "Aucun utilisateur connecté")
            isLoading = false
            navController.popBackStack()
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFCFCF9)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF0066CC))
        }
        return
    }

    if (!isAdmin) {
        // Message d'erreur si accès refusé
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFCFCF9)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🚫",
                    fontSize = 64.sp
                )
                Text(
                    text = "Accès refusé",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF134252)
                )
                Text(
                    text = "Vous devez être administrateur",
                    fontSize = 14.sp,
                    color = Color(0xFF999999)
                )
            }
        }
        return
    }

    // Interface admin
    val tabs = listOf(
        "Membres" to Icons.Default.Person,
        "News" to Icons.Default.Notifications,
        "Contacts" to Icons.Default.Phone
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFCF9))
    ) {
        // TabRow personnalisé
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color(0xFF0066CC),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color(0xFF0066CC)
                )
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            tabs.forEachIndexed { index, (title, icon) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(title, fontSize = 14.sp)
                        }
                    },
                    selectedContentColor = Color(0xFF0066CC),
                    unselectedContentColor = Color(0xFF999999)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Contenu selon l'onglet sélectionné
        when (selectedTab) {
            0 -> AdminMembersTab()
            1 -> AdminNewsTab()
            2 -> AdminContactsTab()
        }
    }
}
