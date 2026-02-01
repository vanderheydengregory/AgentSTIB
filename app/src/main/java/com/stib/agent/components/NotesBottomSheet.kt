package com.stib.agent.ui.components

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.stib.agent.utils.SpeechRecognitionUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesBottomSheet(
    initialNotes: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf(initialNotes) }
    var isRecording by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (results != null && results.isNotEmpty()) {
            val recognizedText = results[0]
            notes += if (notes.isEmpty()) recognizedText else "\n$recognizedText"
            isRecording = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "📝 Notes",
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = Color(0xFF134252),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Champ texte pour les notes
            TextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Ajouter des notes...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF5F5F5),
                    unfocusedContainerColor = Color(0xFFF5F5F5),
                    focusedIndicatorColor = Color(0xFF0066CC),
                    unfocusedIndicatorColor = Color(0xFFCCCCCC)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bouton mémo vocal
            Button(
                onClick = {
                    isRecording = true
                    SpeechRecognitionUtil.startSpeechRecognition(context, speechLauncher)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFA500)
                ),
                enabled = !isRecording
            ) {
                Text(
                    text = if (isRecording) "🎤 Enregistrement..." else "🎤 Ajouter mémo vocal",
                    fontSize = 14.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Boutons Sauvegarder / Annuler
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0E0E0)
                    )
                ) {
                    Text("Annuler", color = Color.Black)
                }

                Button(
                    onClick = {
                        onSave(notes)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0066CC)
                    )
                ) {
                    Text("Sauvegarder", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
