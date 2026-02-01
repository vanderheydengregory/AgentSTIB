package com.stib.agent.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stib.agent.R
import com.stib.agent.ui.viewmodels.AuthViewModel
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily

@Composable
fun HeaderComposable(
    navController: NavController? = null,
    authViewModel: AuthViewModel? = null,
    onProfileClick: (() -> Unit)? = null
) {
    var userRole by remember { mutableStateOf("") }
    var userPrenom by remember { mutableStateOf("") }
    var userNom by remember { mutableStateOf("") }
    var userMatricule by remember { mutableStateOf("") }
    var isLoggedIn by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val userId = auth.currentUser?.uid

        if (userId != null) {
            isLoggedIn = true
            db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    userRole = document.getString("role") ?: ""
                    userPrenom = document.getString("prenom") ?: ""
                    userNom = document.getString("nom") ?: ""
                    userMatricule = document.getString("matricule") ?: ""
                }
                .addOnFailureListener { e ->
                    Log.e("HeaderComposable", "Erreur chargement user", e)
                }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.header_img),
            contentDescription = "STIB Header",
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentScale = ContentScale.Crop
        )

        if (isLoggedIn && userPrenom.isNotEmpty()) {
            // 🆕 BLOC LOGIN COMPACTE AVEC AVATAR À GAUCHE
            val initials = "${userPrenom.getOrNull(0)?.uppercaseChar() ?: 'U'}${userNom.getOrNull(0)?.uppercaseChar() ?: 'U'}"

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        color = Color(0xFF0066CC).copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp)
                    .clickable {
                        Log.d("HeaderComposable", "Bloc login cliqué - navigation vers profil")
                        onProfileClick?.invoke()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // AVATAR À GAUCHE
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0066CC),
                                        Color(0xFF0052A3)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Serif
                        )
                    }

                    // INFOS CENTRÉES HORIZONTALEMENT
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = userNom,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = " ",
                                fontSize = 13.sp
                            )
                            Text(
                                text = userPrenom,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = " ($userMatricule)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            if (userRole == "admin") {
                                Text(
                                    text = " 👑",
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // BOUTON DÉCONNEXION À DROITE
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFE74C3C))
                            .clickable {
                                Log.d("HeaderComposable", "Déconnexion cliquée")
                                coroutineScope.launch {
                                    try {
                                        authViewModel?.logout()
                                        delay(300)
                                        Log.d("HeaderComposable", "Déconnexion réussie")
                                    } catch (e: Exception) {
                                        Log.e("HeaderComposable", "Erreur déconnexion: ${e.message}", e)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Déconnexion",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
