package com.stib.agent.ui.screens

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
import com.stib.agent.ui.settings.CalendarSettings
import com.stib.agent.ui.settings.NotificationSettings
import com.stib.agent.ui.settings.ProfilSettings
import kotlinx.coroutines.tasks.await

@Composable
fun SettingsScreen(navController: NavController) {
    val selectedTab = remember { mutableStateOf(0) }

    // ✅ CHARGER LE ROLE DE L'UTILISATEUR
    val userRole = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            try {
                val userDoc = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()

                userRole.value = userDoc.getString("role") ?: "user"
            } catch (e: Exception) {
                userRole.value = "user"
            }
        }
        isLoading.value = false
    }

    if (isLoading.value) {
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
                .background(Color(0xFFFCFCF9))
        ) {
            // ========== TABS AVEC ICÔNES + LABELS ==========
            TabRow(
                selectedTabIndex = selectedTab.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White),
                divider = { HorizontalDivider(thickness = 1.dp, color = Color(0xFFE0E0E0)) },
                containerColor = Color.White
            ) {
                // ✅ TAB PROFIL
                Tab(
                    selected = selectedTab.value == 0,
                    onClick = { selectedTab.value = 0 },
                    modifier = Modifier
                        .height(60.dp)
                        .background(if (selectedTab.value == 0) Color.White else Color(0xFFF5F5F5))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profil",
                            tint = if (selectedTab.value == 0) Color(0xFF0066CC) else Color(0xFF999999),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Profil",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab.value == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab.value == 0) Color(0xFF0066CC) else Color(0xFF627C7D)
                        )
                    }
                }

                // ✅ TAB CALENDRIER
                Tab(
                    selected = selectedTab.value == 1,
                    onClick = { selectedTab.value = 1 },
                    modifier = Modifier
                        .height(60.dp)
                        .background(if (selectedTab.value == 1) Color.White else Color(0xFFF5F5F5))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Calendrier",
                            tint = if (selectedTab.value == 1) Color(0xFF0066CC) else Color(0xFF999999),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Calendrier",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab.value == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab.value == 1) Color(0xFF0066CC) else Color(0xFF627C7D)
                        )
                    }
                }

                // ✅ TAB NOTIFICATIONS
                Tab(
                    selected = selectedTab.value == 2,
                    onClick = { selectedTab.value = 2 },
                    modifier = Modifier
                        .height(60.dp)
                        .background(if (selectedTab.value == 2) Color.White else Color(0xFFF5F5F5))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = if (selectedTab.value == 2) Color(0xFF0066CC) else Color(0xFF999999),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Notifications",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab.value == 2) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab.value == 2) Color(0xFF0066CC) else Color(0xFF627C7D)
                        )
                    }
                }

                // ✅ TAB ADMIN (VISIBLE SEULEMENT POUR LES ADMINS)
                if (userRole.value == "admin") {
                    Tab(
                        selected = selectedTab.value == 3,
                        onClick = { selectedTab.value = 3 },
                        modifier = Modifier
                            .height(60.dp)
                            .background(if (selectedTab.value == 3) Color.White else Color(0xFFF5F5F5))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Admin",
                                tint = if (selectedTab.value == 3) Color(0xFFFF6B6B) else Color(0xFF999999),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Admin",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab.value == 3) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab.value == 3) Color(0xFFFF6B6B) else Color(0xFF627C7D)
                            )
                        }
                    }
                }
            }

            // ========== CONTENU DES ONGLETS ==========
            when (selectedTab.value) {
                0 -> ProfilSettings(navController = navController)
                1 -> CalendarSettings()
                2 -> NotificationSettings()
                3 -> {
                    if (userRole.value == "admin") {
                        AdminScreen(navController = navController)
                    }
                }
            }
        }
    }
}
