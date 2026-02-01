package com.stib.agent

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stib.agent.components.ServiceDataManager
import com.stib.agent.ui.components.HeaderComposable
import com.stib.agent.ui.navigation.Screen
import com.stib.agent.ui.navigation.bottomNavItems
import com.stib.agent.ui.screens.*
import com.stib.agent.ui.theme.AgentSTIBV3Theme
import com.stib.agent.ui.viewmodels.AuthViewModel
import com.stib.agent.ui.viewmodels.NewsViewModel
import com.stib.agent.utils.UpdateManager
import com.stib.agent.utils.UpdateInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import com.stib.agent.notifications.utils.NotificationHelper
import com.stib.agent.notifications.ServiceAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    companion object {
        private const val CALENDAR_PERMISSION_CODE = 42
        private const val TAG = "MainActivity"
    }

    private lateinit var updateManager: UpdateManager
    private var downloadId: Long = -1
    private val showUpdateDialog = mutableStateOf<UpdateInfo?>(null)
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                val apkFile = File(
                    getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "agent-stib-update.apk"
                )
                if (apkFile.exists()) {
                    updateManager.installUpdate(apkFile)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚀 ========== APP DÉMARRÉE ==========")

        // Initialiser le système de mise à jour
        updateManager = UpdateManager(this)
        registerReceiver(
            downloadCompleteReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED
        )
        lifecycleScope.launch {
            checkUpdate()
        }

        // Créer les canaux de notification
        NotificationHelper.createNotificationChannels(this)

        // Replanifier toutes les alarmes au démarrage
        rescheduleAllAlarmsOnStartup()

        // Demander les permissions calendrier
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                ),
                CALENDAR_PERMISSION_CODE
            )
        }

        setContent {
            AgentSTIBV3Theme {
                UpdateDialog(
                    updateInfo = showUpdateDialog.value,
                    onDismiss = { showUpdateDialog.value = null },
                    onDownload = { info ->
                        downloadId = updateManager.downloadUpdate(info)
                        Toast.makeText(this, "Téléchargement en cours...", Toast.LENGTH_SHORT).show()
                        showUpdateDialog.value = null
                    }
                )
                MainApp()
            }
        }
    }

    private fun rescheduleAllAlarmsOnStartup() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                delay(2000)
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId == null) {
                    Log.d(TAG, "⚠️ Utilisateur non connecté, skip replanification")
                    return@launch
                }

                Log.d(TAG, "🔄 Replanification de toutes les alarmes pour user: $userId")
                val allServices = ServiceDataManager.getAllServices()
                Log.d(TAG, "📋 ${allServices.size} services chargés depuis ServiceDataManager")

                val scheduler = ServiceAlarmScheduler(applicationContext)
                var count = 0

                allServices.forEach { service ->
                    try {
                        if (service.dateService != null && !service.dateService.isBefore(LocalDate.now())) {
                            Log.d(TAG, "✅ Service ${service.id} (${service.serviceNumber}) est dans le futur, planification...")
                            scheduler.scheduleAllAlarmsForService(service.id)
                            count++
                            Log.d(TAG, "✅ Alarmes planifiées pour ${service.id}")
                        } else {
                            Log.d(TAG, "❌ Service ${service.id} est dans le passé, ignoré")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur replanification service ${service.id}: ${e.message}", e)
                    }
                }

                Log.d(TAG, "✅ $count services replanifiés avec succès")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur replanification globale: ${e.message}", e)
            }
        }
    }

    private suspend fun checkUpdate() {
        val updateInfo = updateManager.checkForUpdate()
        if (updateInfo != null) {
            // Au lieu d'appeler showUpdateDialog, on met à jour l'état
            showUpdateDialog.value = updateInfo
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadCompleteReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du unregister: ${e.message}")
        }
    }

    @Composable
    fun MainApp() {
        val authViewModel: AuthViewModel = viewModel()
        val isLoggedIn = authViewModel.isLoggedIn.collectAsState()
        val authNavController = rememberNavController()

        LaunchedEffect(isLoggedIn.value) {
            Log.d("MainApp", "🔄 isLoggedIn = ${isLoggedIn.value}")
        }

        if (!isLoggedIn.value) {
            Log.d("MainApp", "🎯 Affichant LoginScreen/RegisterScreen")
            NavHost(
                navController = authNavController,
                startDestination = "login"
            ) {
                composable("login") {
                    LoginScreen(
                        onLoginSuccess = {
                            authViewModel.updateLoginStatus()
                        },
                        navController = authNavController
                    )
                }
                composable("register") {
                    RegisterScreen(navController = authNavController)
                }
            }
        } else {
            Log.d("MainApp", "🎯 Affichant AppContent")
            AppContent(authViewModel = authViewModel)
        }
    }

    @Composable
    fun AppContent(authViewModel: AuthViewModel) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route
        val newsViewModel: NewsViewModel = viewModel()
        val unreadNewsCount by newsViewModel.unreadNewsCount.collectAsState()
        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()

        LaunchedEffect(Unit) {
            Log.d("AppContent", "📋 Chargement des données utilisateur...")
            val userId = auth.currentUser?.uid
            if (userId != null) {
                try {
                    val userDoc = db.collection("users").document(userId).get().await()
                    val userSyndicat = userDoc.getString("syndicat") ?: "aucun"
                    val userDepot = userDoc.getString("depot") ?: ""
                    Log.d(
                        "AppContent",
                        "✅ Démarrage NewsViewModel: syndicat=$userSyndicat, depot=$userDepot"
                    )
                    newsViewModel.startListeningToNews(userSyndicat, userDepot)
                } catch (e: Exception) {
                    Log.e("AppContent", "❌ Erreur chargement user: ${e.message}")
                }
            }
        }

        LaunchedEffect(currentRoute) {
            Log.d("AppContent", "📊 Route actuelle: $currentRoute")
            if (currentRoute == Screen.News.route) {
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    try {
                        db.collection("users")
                            .document(userId)
                            .update("lastNewsReadAt", System.currentTimeMillis())
                            .addOnSuccessListener {
                                newsViewModel.resetNewsCount()
                                Log.d("AppContent", "🔄 Compteur news réinitialisé et sauvegardé")
                            }
                            .addOnFailureListener { e ->
                                Log.e("AppContent", "❌ Erreur sauvegarde: ${e.message}")
                            }
                    } catch (e: Exception) {
                        Log.e("AppContent", "❌ Erreur: ${e.message}")
                    }
                }
            }
            Log.d("AppContent", "Current route updated: $currentRoute")
        }

        Column(modifier = Modifier.fillMaxSize()) {
            HeaderComposable(
                navController = navController,
                authViewModel = authViewModel,
                onProfileClick = {
                    Log.d("AppContent", "Profile clicked from Header - navigating to profile")
                    navController.navigate(Screen.Profile.route) {
                        launchSingleTop = true
                    }
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen()
                    }
                    composable(Screen.Planning.route) {
                        PlanningScreen(navController = navController)
                    }
                    composable(
                        "viewService/{serviceId}",
                        arguments = listOf(
                            navArgument("serviceId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val serviceId = backStackEntry.arguments?.getString("serviceId") ?: ""
                        ViewServiceScreen(
                            serviceId = serviceId,
                            navController = navController
                        )
                    }
                    composable(
                        "editService/{serviceId}",
                        arguments = listOf(
                            navArgument("serviceId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val serviceId = backStackEntry.arguments?.getString("serviceId") ?: ""
                        EditServiceScreen(
                            serviceId = serviceId,
                            navController = navController
                        )
                    }
                    composable(Screen.AddService.route) {
                        AddServiceScreen(navController = navController)
                    }
                    composable(Screen.ImportPDF.route) {
                        ImportPDFScreen()
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(navController = navController)
                    }
                    composable(Screen.Profile.route) {
                        ProfileScreen(
                            navController = navController,
                            authViewModel = authViewModel
                        )
                    }
                    composable(Screen.UGo.route) {
                        UGoScreen()
                    }
                    composable(Screen.News.route) {
                        NewsScreen(navController = navController)
                    }
                    composable(Screen.Contacts.route) {
                        ContactsScreen()
                    }
                    composable(Screen.Admin.route) {
                        AdminScreen(navController = navController)
                    }
                }
            }

            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                containerColor = Color.Transparent,
                contentColor = Color(0xFF0066CC)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .height(80.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.95f),
                                shape = RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bottomNavItems.forEach { item ->
                                val tabColor = when (item.screen.route) {
                                    "home" -> Color(0xFF0066CC)
                                    "planning" -> Color(0xFF0066CC)
                                    "import_pdf" -> Color(0xFF0066CC)
                                    "settings" -> Color(0xFF0066CC)
                                    "ugo" -> Color(0xFF0066CC)
                                    else -> Color(0xFF0066CC)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (currentRoute == item.screen.route)
                                                tabColor.copy(alpha = 0.2f)
                                            else
                                                Color.Transparent
                                        )
                                        .clickable {
                                            navController.navigate(item.screen.route) {
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(contentAlignment = Alignment.TopEnd) {
                                            Text(
                                                text = item.icon,
                                                fontSize = 24.sp,
                                                color = if (currentRoute == item.screen.route) tabColor else Color(
                                                    0xFF999999
                                                )
                                            )

                                            if (item.screen == Screen.News && unreadNewsCount > 0) {
                                                val isVisible = remember { mutableStateOf(true) }
                                                LaunchedEffect(Unit) {
                                                    while (true) {
                                                        delay(600)
                                                        isVisible.value = !isVisible.value
                                                    }
                                                }

                                                if (isVisible.value) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .align(Alignment.TopEnd)
                                                            .offset(x = 6.dp, y = (-6).dp)
                                                            .background(
                                                                Color(0xFFFF4444),
                                                                RoundedCornerShape(50)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "🆕",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = when (item.screen.route) {
                                                "home" -> "Accueil"
                                                "planning" -> "Planning"
                                                "import_pdf" -> "PDF"
                                                "settings" -> "Paramètres"
                                                "ugo" -> "UGo"
                                                "news" -> "Actualités"
                                                "contacts" -> "Contacts"
                                                "admin" -> "Admin"
                                                else -> item.screen.route.replaceFirstChar { it.uppercase() }
                                            },
                                            fontSize = 8.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (currentRoute == item.screen.route) tabColor else Color(
                                                0xFF999999
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    @Composable
    fun UpdateDialog(
        updateInfo: UpdateInfo?,
        onDismiss: () -> Unit,
        onDownload: (UpdateInfo) -> Unit
    ) {
        if (updateInfo != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    if (!updateInfo.forceUpdate) {
                        onDismiss()
                    }
                },
                icon = {
                    Text(
                        text = "🔄",
                        fontSize = 40.sp
                    )
                },
                title = {
                    Text(
                        text = "Mise à jour disponible",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0066CC)
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Version ${updateInfo.versionName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF333333)
                        )

                        if (updateInfo.releaseNotes.isNotEmpty()) {
                            Text(
                                text = updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF666666)
                            )
                        }

                        if (updateInfo.forceUpdate) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Color(0xFFFFEBEE),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "⚠️", fontSize = 20.sp)
                                Text(
                                    text = "Cette mise à jour est obligatoire",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFC62828),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onDownload(updateInfo) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0066CC)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "⬇️", fontSize = 18.sp)
                            Text(
                                text = "Télécharger",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                dismissButton = {
                    if (!updateInfo.forceUpdate) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = "Plus tard",
                                color = Color(0xFF666666),
                                fontSize = 16.sp
                            )
                        }
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}


