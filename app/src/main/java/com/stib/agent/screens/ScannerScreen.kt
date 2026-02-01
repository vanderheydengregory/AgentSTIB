package com.stib.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScannerScreen(onBackClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFCF9))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📱 Scanner QR",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF134252),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Scannez les codes QR des trajets STIB",
            fontSize = 16.sp,
            color = Color(0xFF62636D),
            modifier = Modifier.padding(bottom = 48.dp)
        )

        Button(
            onClick = onBackClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("← Retour")
        }
    }
}
