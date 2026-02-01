package com.stib.agent.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun UGoScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
        .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.OpenInBrowser,
            contentDescription = "uGo",
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 24.dp),
            tint = Color(0xFF1a6496)
        )

        Text(
            text = "uGo - Liens rapide",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )

        Text(
            text = "Accédez au portail uGo directement ou vous en avez besoin",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 40.dp),
            textAlign = TextAlign.Center
        )

        // Bouton 1 : uGo my apps
        Button(
            onClick = { openLink(context, "https://myapps.stib-mivb.be/sap/bc/ui2/flp?sap-language=FR") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1a6496)
            )
        ) {
            Icon(
                imageVector = Icons.Filled.OpenInBrowser,
                contentDescription = "uGo my apps",
                modifier = Modifier.padding(end = 12.dp),
                tint = Color.White
            )
            Text(
                "uGo my apps",
                fontSize = 16.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bouton 2 : Portail conducteur
        Button(
            onClick = { openLink(context, "https://portail.stib-mivb.be/irj/portal/drivers") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E5090)
            )
        ) {
            Icon(
                imageVector = Icons.Filled.OpenInBrowser,
                contentDescription = "Portail conducteur",
                modifier = Modifier.padding(end = 12.dp),
                tint = Color.White
            )
            Text(
                "Portail conducteur",
                fontSize = 16.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bouton 3 : Prochaine quinzaine
        Button(
            onClick = { openLink(context, "https://myapps.stib-mivb.be/sap/bc/ui2/flp?sap-language=FR&appState=lean#CYR048-display") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E5090)
            )
        ) {
            Icon(
                imageVector = Icons.Filled.OpenInBrowser,
                contentDescription = "Prochaine quinzaine",
                modifier = Modifier.padding(end = 12.dp),
                tint = Color.White
            )
            Text(
                "Prochaine quinzaine",
                fontSize = 16.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bouton 2 : Quinzaine en cours
        Button(
            onClick = { openLink(context, "https://myapps.stib-mivb.be/sap/bc/ui2/flp?sap-language=FR&appState=lean#CYR047-display") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E5090)
            )
        ) {
            Icon(
                imageVector = Icons.Filled.OpenInBrowser,
                contentDescription = "Quinzaine en cours",
                modifier = Modifier.padding(end = 12.dp),
                tint = Color.White
            )
            Text(
                "Quinzaine en cours",
                fontSize = 16.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bouton 5 : Portail permutation
        Button(
            onClick = { openLink(context, "https://myapps.stib-mivb.be/sap/bc/ui2/flp?sap-language=FR&appState=lean#CYR371-display") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E5090)
            )
        ) {
            Icon(
                imageVector = Icons.Filled.OpenInBrowser,
                contentDescription = "Portail permutation",
                modifier = Modifier.padding(end = 12.dp),
                tint = Color.White
            )
            Text(
                "Permutation",
                fontSize = 16.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF0F4F8)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "💡 Info",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF1a6496),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Vous serez redirigé vers le portail dans votre navigateur car la Stib bloque le portail in app. Vous restez connecté automatiquement grâce aux cookies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Justify
                )
            }
        }
    }
}

private fun openLink(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(url)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("UGoScreen", "Erreur ouverture lien: ${e.message}")
    }
}
