package com.stib.agent.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.rememberAsyncImagePainter
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.stib.agent.utils.PlanchetteManager

// Fonction utilitaire pour trouver l'Activity depuis n'importe quel Context
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun PlanchetteSection(
    service: String,
    heureDebut: String,
    heureFin: String,
    onPlanchetteChanged: () -> Unit
) {
    val context = LocalContext.current
    var planchetteUri by remember { mutableStateOf<Uri?>(null) }
    var showFullscreen by remember { mutableStateOf(false) }

    // Charger la planchette si elle existe
    LaunchedEffect(service, heureDebut, heureFin) {
        planchetteUri = PlanchetteManager.getPlanchette(context, service, heureDebut, heureFin)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        if (planchetteUri != null) {
            // Afficher la planchette existante
            PlanchettePreview(
                uri = planchetteUri!!,
                onClick = { showFullscreen = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bouton supprimer
            Button(
                onClick = {
                    val deleted = PlanchetteManager.deletePlanchette(
                        context, service, heureDebut, heureFin
                    )
                    if (deleted) {
                        planchetteUri = null
                        onPlanchetteChanged()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Supprimer la planchette", fontSize = 14.sp)
            }
        } else {
            // Boutons scanner et importer
            PlanchetteActionButtons(
                service = service,
                heureDebut = heureDebut,
                heureFin = heureFin,
                onPlanchetteSaved = { uri ->
                    planchetteUri = uri
                    onPlanchetteChanged()
                }
            )
        }
    }

    // Dialog plein écran avec zoom
    if (showFullscreen && planchetteUri != null) {
        PlanchetteFullscreenDialog(
            uri = planchetteUri!!,
            onDismiss = { showFullscreen = false }
        )
    }
}

@Composable
fun PlanchettePreview(
    uri: Uri,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(Color(0xFFF5F5F5))
    ) {
        Image(
            painter = rememberAsyncImagePainter(uri),
            contentDescription = "Planchette",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Badge "Cliquer pour agrandir"
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "👆 Cliquer pour agrandir",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PlanchetteActionButtons(
    service: String,
    heureDebut: String,
    heureFin: String,
    onPlanchetteSaved: (Uri) -> Unit
) {
    val context = LocalContext.current

    // Scanner ML Kit
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.firstOrNull()?.let { page ->
                page.imageUri?.let { imageUri ->
                    try {
                        val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
                            val source = android.graphics.ImageDecoder.createSource(
                                context.contentResolver, imageUri
                            )
                            android.graphics.ImageDecoder.decodeBitmap(source)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                        }

                        PlanchetteManager.savePlanchette(
                            context, bitmap, service, heureDebut, heureFin
                        )?.let { file ->
                            onPlanchetteSaved(file.toUri())
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PlanchetteScanner", "Error saving scanned image", e)
                    }
                }
            }
        }
    }

    // Galerie
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    val source = android.graphics.ImageDecoder.createSource(
                        context.contentResolver, uri
                    )
                    android.graphics.ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }

                PlanchetteManager.savePlanchette(
                    context, bitmap, service, heureDebut, heureFin
                )?.let { file ->
                    onPlanchetteSaved(file.toUri())
                }
            } catch (e: Exception) {
                android.util.Log.e("PlanchetteGallery", "Error saving gallery image", e)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Bouton Scanner
        Button(
            onClick = {
                // ✅ CORRECTION ICI : utilisation de findActivity()
                val activity = context.findActivity()

                if (activity == null) {
                    android.util.Log.e("PlanchetteScanner", "❌ Activity non trouvée")
                    return@Button
                }

                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(false)
                    .setPageLimit(1)
                    .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
                    )
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()

                val scanner = GmsDocumentScanning.getClient(options)
                scanner.getStartScanIntent(activity)  // ✅ Utilise activity au lieu de context as Activity
                    .addOnSuccessListener { intentSender ->
                        scannerLauncher.launch(
                            IntentSenderRequest.Builder(intentSender).build()
                        )
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("PlanchetteScanner", "Error starting scanner", e)
                    }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0066CC)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Scanner")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scanner la planchette", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        // Bouton Galerie
        OutlinedButton(
            onClick = {
                galleryLauncher.launch("image/*")
            },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF0066CC)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Image, contentDescription = "Galerie")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Importer depuis la galerie", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PlanchetteFullscreenDialog(
    uri: Uri,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Image avec zoom et pan
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = "Planchette plein écran",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)

                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentScale = ContentScale.Fit
            )

            // Bouton fermer
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Fermer",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Instructions zoom
            if (scale == 1f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Pincez pour zoomer • Glissez pour déplacer",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
