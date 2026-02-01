package com.stib.agent.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.stib.agent.ui.components.HeaderComposable
import androidx.compose.foundation.background

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val email = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<String?>(null) }
    val auth = FirebaseAuth.getInstance()

    // 🆕 ÉTAT POUR LA DIALOG MOT DE PASSE OUBLIÉ
    val showForgotPasswordDialog = remember { mutableStateOf(false) }
    val forgotPasswordEmail = remember { mutableStateOf("") }
    val forgotPasswordMessage = remember { mutableStateOf<String?>(null) }
    val isSendingResetEmail = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sharedPref = context.getSharedPreferences("logindata", Context.MODE_PRIVATE)
        email.value = sharedPref.getString("savedemail", "") ?: ""
        password.value = sharedPref.getString("savedpassword", "") ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFCF9))

    ) {
        HeaderComposable(navController = navController)

        Text(
            text = "Connexion",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0066CC),
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextField(
                value = email.value,
                onValueChange = { email.value = it },
                label = { Text("Email") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color(0xFF0066CC),
                    unfocusedIndicatorColor = Color(0xFFCCCCCC)
                )
            )

            TextField(
                value = password.value,
                onValueChange = { password.value = it },
                label = { Text("Mot de passe") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color(0xFF0066CC),
                    unfocusedIndicatorColor = Color(0xFFCCCCCC)
                )
            )

            // 🆕 LIEN MOT DE PASSE OUBLIÉ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Mot de passe oublié ?",
                    fontSize = 12.sp,
                    color = Color(0xFF0066CC),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        showForgotPasswordDialog.value = true
                        forgotPasswordEmail.value = email.value
                    }
                )
            }

            if (error.value != null) {
                Text(
                    text = error.value ?: "",
                    fontSize = 12.sp,
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(
                onClick = {
                    if (email.value.isBlank() || password.value.isBlank()) {
                        error.value = "Remplis tous les champs"
                        return@Button
                    }

                    isLoading.value = true
                    error.value = null

                    val sharedPref = context.getSharedPreferences("logindata", Context.MODE_PRIVATE)
                    sharedPref.edit().apply {
                        putString("savedemail", email.value)
                        putString("savedpassword", password.value)
                        apply()
                    }

                    auth.signInWithEmailAndPassword(email.value, password.value)
                        .addOnSuccessListener {
                            isLoading.value = false
                            onLoginSuccess()
                        }
                        .addOnFailureListener { exception ->
                            isLoading.value = false
                            error.value = exception.message ?: "Erreur de connexion"
                        }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0066CC)
                ),
                enabled = !isLoading.value
            ) {
                if (isLoading.value) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text("Se connecter", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pas encore de compte ? ",
                    fontSize = 13.sp,
                    color = Color(0xFF627C7D)
                )
                Text(
                    text = "Inscription",
                    fontSize = 13.sp,
                    color = Color(0xFF0066CC),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        navController?.navigate("register")
                    }
                )
            }
        }
    }

    // 🆕 DIALOG MOT DE PASSE OUBLIÉ
    if (showForgotPasswordDialog.value) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog.value = false },
            title = {
                Text(
                    "Réinitialiser le mot de passe",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Entrez votre adresse email pour recevoir un lien de réinitialisation.",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    TextField(
                        value = forgotPasswordEmail.value,
                        onValueChange = { forgotPasswordEmail.value = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = Color(0xFF0066CC),
                            unfocusedIndicatorColor = Color(0xFFCCCCCC)
                        )
                    )

                    if (forgotPasswordMessage.value != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = forgotPasswordMessage.value ?: "",
                            fontSize = 13.sp,
                            color = if (forgotPasswordMessage.value!!.contains("succès", ignoreCase = true))
                                Color(0xFF4CAF50) else Color(0xFFE53935),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (forgotPasswordEmail.value.isBlank()) {
                            forgotPasswordMessage.value = "Veuillez entrer votre email"
                            return@Button
                        }

                        isSendingResetEmail.value = true
                        forgotPasswordMessage.value = null

                        auth.sendPasswordResetEmail(forgotPasswordEmail.value)
                            .addOnSuccessListener {
                                isSendingResetEmail.value = false
                                forgotPasswordMessage.value = "✅ Email envoyé avec succès ! Vérifiez votre boîte mail."

                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    showForgotPasswordDialog.value = false
                                }, 2000)
                            }
                            .addOnFailureListener { exception ->
                                isSendingResetEmail.value = false
                                forgotPasswordMessage.value = "❌ ${exception.message}"
                            }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0066CC)
                    ),
                    enabled = !isSendingResetEmail.value
                ) {
                    if (isSendingResetEmail.value) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text("Envoyer")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showForgotPasswordDialog.value = false }
                ) {
                    Text("Annuler")
                }
            }
        )
    }
}
