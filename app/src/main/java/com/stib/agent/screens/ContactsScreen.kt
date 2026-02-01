package com.stib.agent.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await


data class ContactScreenData(
    val contactId: String = "",
    val nom: String = "",
    val telephones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val depots: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val notes: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen() {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    var contacts by remember { mutableStateOf<List<ContactScreenData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<ContactScreenData?>(null) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var allDepots by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDepotFilter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val depotsSnapshot = db.collection("depots").get().await()
            allDepots = depotsSnapshot.documents.mapNotNull { it.id }

            val snapshot = db.collection("contacts").get().await()
            val contactsList = mutableListOf<ContactScreenData>()

            for (doc in snapshot.documents) {
                val tags = (doc.get("tags") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                val contact = ContactScreenData(
                    contactId = doc.id,
                    nom = doc.getString("nom") ?: "",
                    telephones = (doc.get("telephones") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    emails = (doc.get("emails") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    depots = (doc.get("depots") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    tags = tags,
                    notes = doc.getString("notes") ?: ""
                )
                contactsList.add(contact)
            }

            contacts = contactsList.sortedBy { it.nom }
            isLoading = false
        } catch (e: Exception) {
            isLoading = false
        }
    }

    fun filterContacts(): List<ContactScreenData> {
        return contacts.filter { contact ->
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val query = searchQuery.lowercase()
                contact.nom.lowercase().contains(query) ||
                        contact.tags.any { it.lowercase().contains(query) } ||
                        contact.depots.any { it.lowercase().contains(query) }
            }

            val matchesDepot = if (selectedDepotFilter.isBlank()) {
                true
            } else {
                contact.depots.contains(selectedDepotFilter)
            }

            matchesSearch && matchesDepot
        }.sortedBy { it.nom }
    }

    val filteredContacts = filterContacts()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.ContactMail,
                contentDescription = null,
                tint = Color(0xFF0066CC),
                modifier = Modifier.size(28.dp)
            )
            Text(
                "Contacts utiles",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF134252)
            )
        }

            // Barre de recherche élégante
        BasicTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .shadow(4.dp, RoundedCornerShape(12.dp)),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 14.sp,
                color = Color(0xFF134252)
            ),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Rechercher",
                        tint = Color(0xFF0066CC),
                        modifier = Modifier.size(18.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Rechercher par nom, tag ou dépôt...",
                                fontSize = 14.sp,
                                color = Color(0xFFB0B0B0)
                            )
                        }
                        innerTextField()
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Effacer",
                            tint = Color(0xFFE53935),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF0066CC))
            }
        } else {
            // Filtre dépôt amélioré
            if (allDepots.isNotEmpty()) {
                var expandedDepot by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Filtre:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF666666)
                    )
                    Box {
                        Button(
                            onClick = { expandedDepot = true },
                            modifier = Modifier.height(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedDepotFilter.isEmpty()) Color(0xFF0066CC) else Color(0xFF0052A3),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (selectedDepotFilter.isEmpty()) "Tous les dépôts" else selectedDepotFilter,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        DropdownMenu(
                            expanded = expandedDepot,
                            onDismissRequest = { expandedDepot = false },
                            modifier = Modifier.fillMaxWidth(0.5f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Tous les dépôts") },
                                onClick = {
                                    selectedDepotFilter = ""
                                    expandedDepot = false
                                }
                            )
                            allDepots.forEach { depot ->
                                DropdownMenuItem(
                                    text = { Text(depot) },
                                    onClick = {
                                        selectedDepotFilter = depot
                                        expandedDepot = false
                                    }
                                )
                            }
                        }
                    }

                    if (selectedDepotFilter.isNotEmpty()) {
                        TextButton(
                            onClick = { selectedDepotFilter = "" },
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Réinitialiser",
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "${filteredContacts.size} contact${if (filteredContacts.size != 1) "s" else ""}",
                        fontSize = 12.sp,
                        color = Color(0xFF999999),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Liste des contacts
            if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFE8F0FF),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ContactMail,
                                    contentDescription = null,
                                    tint = Color(0xFF0066CC),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Text(
                            if (contacts.isEmpty()) "Aucun contact" else "Aucun résultat",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF134252)
                        )
                        Text(
                            if (contacts.isEmpty()) "Vos contacts apparaîtront ici" else "Essayez une autre recherche ou un autre filtre",
                            fontSize = 13.sp,
                            color = Color(0xFF999999),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(filteredContacts) { contact ->
                        ContactListItem(
                            contact = contact,
                            onClick = {
                                selectedContact = contact
                                showDetailSheet = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDetailSheet && selectedContact != null) {
        ContactDetailBottomSheet(
            contact = selectedContact!!,
            context = context,
            onDismiss = { showDetailSheet = false }
        )
    }
}

@Composable
fun ContactListItem(
    contact: ContactScreenData,
    onClick: () -> Unit
) {
    val isPressed = remember { mutableStateOf(false) }
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed.value) Color(0xFFF0F5FF) else Color.White,
        label = "backgroundColor"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onClickLabel = "Ouvrir les détails de ${contact.nom}"
            ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = contact.nom,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF134252),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFCCC),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailBottomSheet(
    contact: ContactScreenData,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.9f),
        containerColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        sheetMaxWidth = Dp.Unspecified,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F0FF),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    contact.nom.firstOrNull()?.uppercase() ?: "?",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0066CC)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = contact.nom,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF134252)
                            )
                            if (contact.depots.isNotEmpty()) {
                                Text(
                                    text = contact.depots.joinToString(", "),
                                    fontSize = 12.sp,
                                    color = Color(0xFF999999)
                                )
                            }
                        }
                    }
                }
            }

            // Téléphones
            if (contact.telephones.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Téléphones",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF134252)
                        )
                        contact.telephones.forEach { phone ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF0F5FF),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:$phone")
                                        }
                                        context.startActivity(intent)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF0066CC),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Phone,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Appeler",
                                            fontSize = 12.sp,
                                            color = Color(0xFF999999),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            phone,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF0066CC)
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color(0xFFCCC),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Emails
            if (contact.emails.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Emails",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF134252)
                        )
                        contact.emails.forEach { email ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:$email")
                                        }
                                        context.startActivity(intent)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF22C55E),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Email,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Email",
                                            fontSize = 12.sp,
                                            color = Color(0xFF999999),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            email,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF22C55E),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color(0xFFCCC),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tags
            if (contact.tags.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Tags",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF134252)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            contact.tags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF0066CC),
                                    modifier = Modifier
                                ) {
                                    Text(
                                        text = tag,
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Notes
            if (contact.notes.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Notes",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF134252)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFAFAFA),
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E8E8))

                        ) {
                            Text(
                                text = contact.notes,
                                fontSize = 13.sp,
                                color = Color(0xFF666666),
                                modifier = Modifier.padding(14.dp),
                                lineHeight = 1.6.sp
                            )
                        }
                    }
                }
            }
        }
    }
}