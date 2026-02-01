package com.stib.agent.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.stib.agent.data.models.User
import com.stib.agent.ui.navigation.Screen
import com.stib.agent.ui.viewmodels.ProfileViewModel
import com.stib.agent.ui.viewmodels.AuthViewModel

@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val user = viewModel.user.collectAsState()
    val isLoading = viewModel.isLoading.collectAsState()
    val error = viewModel.error.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFCF9))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                when {
                    isLoading.value -> {
                        CircularProgressIndicator(color = Color(0xFF0066CC))
                    }

                    error.value != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "❌ ${error.value}",
                                fontSize = 14.sp,
                                color = Color(0xFFFF0000),
                                modifier = Modifier.padding(16.dp)
                            )

                            Button(
                                onClick = { viewModel.refreshUser() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0066CC)
                                )
                            ) {
                                Text("Réessayer")
                            }
                        }
                    }

                    user.value != null -> {
                        ProfileContent(
                            user = user.value!!,
                            onAdminDashboard = {
                                navController.navigate(Screen.Admin.route)
                            },
                            onLogout = {
                                authViewModel.logout()
                                navController.popBackStack()
                                navController.popBackStack()
                                navController.popBackStack()
                            }
                        )
                    }

                    else -> {
                        Text(
                            text = "Aucun utilisateur trouvé",
                            fontSize = 16.sp,
                            color = Color(0xFF62636D)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileContent(
    user: User,
    onAdminDashboard: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 🆕 AVATAR COMPACT
        val initials = "${user.prenom.getOrNull(0)?.uppercaseChar() ?: 'U'}${user.nom.getOrNull(0)?.uppercaseChar() ?: 'U'}"
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0066CC),
                            Color(0xFF0052A3)
                        )
                    )
                )
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Serif
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Nom complet
        Text(
            text = "${user.prenom} ${user.nom}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF134252),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // Badge ADMIN si applicable - ✅ CLIQUABLE
        if (user.role == "admin") {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
                    .background(Color(0xFFFF6B6B), RoundedCornerShape(20.dp))
                    .clickable { onAdminDashboard() }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "👑 ADMIN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        } else {
            Text(
                text = user.role,
                fontSize = 11.sp,
                color = Color(0xFF0066CC),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ✅ INFORMATIONS COMPACTES (Padding réduit)
        ProfileInfoCard("👤 Dépôt", user.depot)
        Spacer(modifier = Modifier.height(6.dp))
        ProfileInfoCard("🏢 Syndicat", user.syndicat)
        Spacer(modifier = Modifier.height(6.dp))
        ProfileInfoCard("📧 Email", user.email)
        Spacer(modifier = Modifier.height(6.dp))
        ProfileInfoCard("📱 Téléphone", user.telephone)
        Spacer(modifier = Modifier.height(6.dp))
        ProfileInfoCard("🆔 Matricule", user.matricule)
        Spacer(modifier = Modifier.height(6.dp))
        ProfileInfoCard("📅 Inscrit", user.dateInscription)

        Spacer(modifier = Modifier.height(16.dp))

        // Bouton Déconnexion
        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE74C3C)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("🚪 Déconnexion", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ProfileInfoCard(label: String, value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color(0xFF62636D),
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = value.ifEmpty { "Non renseigné" },
                fontSize = 14.sp,
                color = Color(0xFF134252),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
