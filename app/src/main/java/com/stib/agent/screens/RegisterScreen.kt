package com.stib.agent.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stib.agent.ui.components.HeaderComposable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun RegisterScreen(navController: NavController) {
    var nom by remember { mutableStateOf("") }
    var prenom by remember { mutableStateOf("") }
    var matricule by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var passwordConfirmVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFCF9))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderComposable(navController = navController)

        // CONTENU AVEC PADDING
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // NOM
            TextField(
                value = nom,
                onValueChange = { nom = it },
                label = { Text("Nom") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color(0xFF0066CC),
                    unfocusedIndicatorColor = Color(0xFFDDDDDD)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            // PRENOM
            TextField(
                value = prenom,
                onValueChange = { prenom = it },
                label = { Text("Prénom") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color(0xFF0066CC),
                    unfocusedIndicatorColor = Color(0xFFDDDDDD)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            // MATRICULE (maximum 6 chiffres)
            TextField(
                value = matricule,
                onValueChange = { newValue ->
                    // Ne garder que les chiffres et limiter à 6 max
                    if (newValue.all { it.isDigit() } && newValue.length <= 6) {
                        matricule = newValue
                    }
                },
                label = { Text("Matricule (max 6 chiffres)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color(0xFF0066CC),
                    unfocusedIndicatorColor = Color(0xFFDDDDDD)
                )
            )

            if (matricule.isNotEmpty() && matricule.length < 6) {
                Text(
                    text = "${matricule.length}/6 chiffres",
                    fontSize = 11.sp,
                    color = Color(0xFF0066CC),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 2.dp)
                )
            } else if (matricule.length == 6) {
                Text(
                    text = "✓ Maximum atteint",
                    fontSize = 11.sp,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // EMAIL
            TextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color(0xFF0066CC),
                    unfocusedIndicatorColor = Color(0xFFDDDDDD)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            // MOT DE PASSE
            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mot de passe") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle",
                            tint = Color(0xFF0066CC)
                        )
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color(0xFF0066CC),
                    unfocusedIndicatorColor = Color(0xFFDDDDDD)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            // CONFIRMATION
            TextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it },
                label = { Text("Confirmer") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordConfirmVisible = !passwordConfirmVisible }) {
                        Icon(
                            imageVector = if (passwordConfirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle",
                            tint = Color(0xFF0066CC)
                        )
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color(0xFF0066CC),
                    unfocusedIndicatorColor = Color(0xFFDDDDDD)
                )
            )

            if (passwordConfirm.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (password == passwordConfirm) "✓ OK" else "✗ Différents",
                    fontSize = 11.sp,
                    color = if (password == passwordConfirm) Color(0xFF4CAF50) else Color(0xFFD32F2F)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // MESSAGE DE SUCCÈS
            if (successMessage.isNotEmpty()) {
                Text(
                    text = successMessage,
                    fontSize = 14.sp,
                    color = Color(0xFF4CAF50),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // MESSAGE D'ERREUR
            if (errorMessage.isNotEmpty() && successMessage.isEmpty()) {
                Text(
                    text = "❌ $errorMessage",
                    fontSize = 12.sp,
                    color = Color(0xFFD32F2F),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    when {
                        nom.isBlank() -> errorMessage = "Nom requis"
                        prenom.isBlank() -> errorMessage = "Prénom requis"
                        matricule.isBlank() -> errorMessage = "Matricule requis"
                        email.isBlank() -> errorMessage = "Email requis"
                        !email.contains("@") -> errorMessage = "Email invalide"
                        password.isBlank() -> errorMessage = "Mot de passe requis"
                        !isPasswordStrong(password) -> errorMessage = "Mot de passe trop faible"
                        password != passwordConfirm -> errorMessage = "Mots de passe différents"
                        else -> {
                            isLoading = true
                            errorMessage = ""
                            successMessage = ""

                            // Vérifier si le matricule existe déjà
                            db.collection("users")
                                .whereEqualTo("matricule", matricule)
                                .get()
                                .addOnSuccessListener { matriculeQuery ->
                                    if (!matriculeQuery.isEmpty) {
                                        errorMessage = "Ce matricule est déjà utilisé"
                                        isLoading = false
                                        return@addOnSuccessListener
                                    }

                                    // Vérifier si le combo nom + prénom existe déjà
                                    db.collection("users")
                                        .whereEqualTo("nom", nom)
                                        .whereEqualTo("prenom", prenom)
                                        .get()
                                        .addOnSuccessListener { nameQuery ->
                                            if (!nameQuery.isEmpty) {
                                                errorMessage = "Un compte avec ce nom et prénom existe déjà"
                                                isLoading = false
                                                return@addOnSuccessListener
                                            }

                                            // Tout est OK, créer le compte
                                            auth.createUserWithEmailAndPassword(email, password)
                                                .addOnSuccessListener { authResult ->
                                                    val userId = authResult.user?.uid ?: ""

                                                    val userData = hashMapOf(
                                                        "nom" to nom,
                                                        "prenom" to prenom,
                                                        "matricule" to matricule,
                                                        "email" to email,
                                                        "role" to "user",
                                                        "dateInscription" to SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH).format(Date()),
                                                        "telephone" to "",
                                                        "depot" to ""
                                                    )

                                                    db.collection("users")
                                                        .document(userId)
                                                        .set(userData)
                                                        .addOnSuccessListener {
                                                            Log.d("RegisterScreen", "✅ Inscription réussie")
                                                            successMessage = "✅ Compte créé avec succès ! Redirection..."

                                                            MainScope().launch {
                                                                delay(2000)
                                                                isLoading = false
                                                            }
                                                        }
                                                        .addOnFailureListener { e ->
                                                            errorMessage = "Erreur : ${e.message}"
                                                            isLoading = false
                                                        }
                                                }
                                                .addOnFailureListener { e ->
                                                    errorMessage = when {
                                                        e.message?.contains("already in use") == true -> "Email déjà utilisé"
                                                        e.message?.contains("weak password") == true -> "Mot de passe trop faible"
                                                        else -> "Erreur : ${e.message}"
                                                    }
                                                    isLoading = false
                                                }
                                        }
                                        .addOnFailureListener { e ->
                                            errorMessage = "Erreur de vérification : ${e.message}"
                                            isLoading = false
                                        }
                                }
                                .addOnFailureListener { e ->
                                    errorMessage = "Erreur de vérification : ${e.message}"
                                    isLoading = false
                                }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC)),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("CRÉER UN COMPTE", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Vous avez un compte ? ", fontSize = 12.sp, color = Color(0xFF627C7D))
                Text(
                    "Connexion",
                    fontSize = 12.sp,
                    color = Color(0xFF0066CC),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.clickable {
                        navController.navigate("login") {
                            popUpTo("register") { inclusive = true }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

fun isPasswordStrong(password: String): Boolean {
    val hasUppercase = password.any { it.isUpperCase() }
    val hasLowercase = password.any { it.isLowerCase() }
    val hasDigit = password.any { it.isDigit() }
    val hasSpecialChar = password.contains("-")
    val isLongEnough = password.length >= 8

    return hasUppercase && hasLowercase && hasDigit && hasSpecialChar && isLongEnough
}
