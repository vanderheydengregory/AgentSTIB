package com.stib.agent.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Planning : Screen("planning")
    object ImportPDF : Screen("importPdf")
    object AddService : Screen("addService")
    object Settings : Screen("settings")
    object Profile : Screen("profile")
    object EditProfile : Screen("editProfile")
    object Admin : Screen("admin")
    object UGo : Screen("ugo")
    object News : Screen("news")
    object Contacts : Screen("contacts")
    // 🆕 MESSAGERIE
    object Messaging : Screen("messaging")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
}

data class BottomNavItem(
    val label: String,
    val icon: String,
    val screen: Screen
)

val bottomNavItems = listOf(
    BottomNavItem(label = "Accueil", icon = "🏠", screen = Screen.Home),
    BottomNavItem(label = "Planning", icon = "📅", screen = Screen.Planning),
    BottomNavItem(label = "uGo", icon = "🚍", screen = Screen.UGo),
    BottomNavItem(label = "News", icon = "📰", screen = Screen.News),
    BottomNavItem(label = "Contacts", icon = "📞", screen = Screen.Contacts),
    BottomNavItem(label = "Paramètres", icon = "⚙️", screen = Screen.Settings)

)
