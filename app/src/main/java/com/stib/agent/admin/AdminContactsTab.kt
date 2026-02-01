package com.stib.agent.ui.admin

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class ContactData(
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
fun AdminContactsTab() {
    val db = FirebaseFirestore.getInstance()

    var contacts by remember { mutableStateOf<List<ContactData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<ContactData?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<ContactData?>(null) }
    var allDepots by remember { mutableStateOf<List<String>>(emptyList()) }
    var allTags by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Charger les contacts, dépôts et tags
    LaunchedEffect(Unit) {
        try {
            val depotsSnapshot = db.collection("depots").get().await()
            allDepots = depotsSnapshot.documents.mapNotNull { it.id }

            val snapshot = db.collection("contacts").get().await()
            val contactsList = mutableListOf<ContactData>()
            val tagsSet = mutableSetOf<String>()

            for (doc in snapshot.documents) {
                val tags = (doc.get("tags") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                tagsSet.addAll(tags)

                val contact = ContactData(
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
            allTags = tagsSet
            isLoading = false
            Log.d("AdminContacts", "✅ ${contacts.size} contacts chargés")
        } catch (e: Exception) {
            Log.e("AdminContacts", "❌ Erreur: ${e.message}", e)
            isLoading = false
        }
    }

    // Fonction de filtrage avancée
    fun filterContacts(): List<ContactData> {
        if (searchQuery.isBlank()) return contacts.sortedBy { it.nom }

        val query = searchQuery.lowercase()
        return contacts.filter { contact ->
            contact.nom.lowercase().contains(query) ||
                    contact.tags.any { it.lowercase().contains(query) } ||
                    contact.depots.any { it.lowercase().contains(query) }
        }.sortedBy { it.nom }
    }

    val filteredContacts = filterContacts()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF0066CC))
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                // Barre de recherche
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 15.sp,
                            color = Color(0xFF134252)
                        ),
                        decorationBox = { innerTextField ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Recherche",
                                    tint = Color(0xFF0066CC),
                                    modifier = Modifier.size(20.dp)
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Nom, tags, dépôt...",
                                            fontSize = 15.sp,
                                            color = Color(0xFFC0C0C0)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Effacer",
                            tint = Color(0xFFE53935),
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Liste des contacts
                if (filteredContacts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ContactMail,
                                contentDescription = null,
                                tint = Color(0xFFDDD),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (contacts.isEmpty()) "Aucun contact" else "Aucun résultat",
                                fontSize = 15.sp,
                                color = Color(0xFF999999),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredContacts) { contact ->
                            AdminContactListItem(
                                contact = contact,
                                onEdit = {
                                    selectedContact = contact
                                    showEditDialog = true
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = Color(0xFF0066CC),
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Ajouter", modifier = Modifier.size(28.dp))
        }
    }

    // Dialogs
    if (showCreateDialog) {
        CreateContactDialog(
            allDepots = allDepots,
            allTags = allTags.toList(),
            onDismiss = { showCreateDialog = false },
            onCreate = { newContact ->
                val contactData = hashMapOf(
                    "nom" to newContact.nom,
                    "telephones" to newContact.telephones,
                    "emails" to newContact.emails,
                    "depots" to newContact.depots,
                    "tags" to newContact.tags,
                    "notes" to newContact.notes
                )

                db.collection("contacts")
                    .add(contactData)
                    .addOnSuccessListener { docRef ->
                        contacts = (contacts + newContact.copy(contactId = docRef.id)).sortedBy { it.nom }
                        val newTags = allTags.toMutableSet()
                        newTags.addAll(newContact.tags)
                        allTags = newTags
                        showCreateDialog = false
                        Log.d("AdminContacts", "✅ Contact créé")
                    }
                    .addOnFailureListener { e ->
                        Log.e("AdminContacts", "❌ Erreur création: ${e.message}")
                    }
            }
        )
    }

    if (showEditDialog && selectedContact != null) {
        EditContactDialog(
            contact = selectedContact!!,
            allDepots = allDepots,
            allTags = allTags.toList(),
            onDismiss = { showEditDialog = false },
            onSave = { updatedContact ->
                db.collection("contacts")
                    .document(updatedContact.contactId)
                    .update(
                        mapOf(
                            "nom" to updatedContact.nom,
                            "telephones" to updatedContact.telephones,
                            "emails" to updatedContact.emails,
                            "depots" to updatedContact.depots,
                            "tags" to updatedContact.tags,
                            "notes" to updatedContact.notes
                        )
                    )
                    .addOnSuccessListener {
                        contacts = contacts.map {
                            if (it.contactId == updatedContact.contactId) updatedContact else it
                        }.sortedBy { it.nom }
                        val newTags = allTags.toMutableSet()
                        newTags.addAll(updatedContact.tags)
                        allTags = newTags
                        showEditDialog = false
                        Log.d("AdminContacts", "✅ Contact modifié")
                    }
                    .addOnFailureListener { e ->
                        Log.e("AdminContacts", "❌ Erreur modification: ${e.message}")
                    }
            },
            onDelete = {
                contactToDelete = selectedContact
                showDeleteConfirm = true
            }
        )
    }

    if (showDeleteConfirm && contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirmer la suppression", fontWeight = FontWeight.Bold) },
            text = { Text("Voulez-vous supprimer ${contactToDelete!!.nom} ?") },
            confirmButton = {
                Button(
                    onClick = {
                        db.collection("contacts")
                            .document(contactToDelete!!.contactId)
                            .delete()
                            .addOnSuccessListener {
                                contacts = contacts.filter { it.contactId != contactToDelete!!.contactId }
                                showDeleteConfirm = false
                                showEditDialog = false
                                contactToDelete = null
                                selectedContact = null
                                Log.d("AdminContacts", "✅ Contact supprimé")
                            }
                            .addOnFailureListener { e ->
                                Log.e("AdminContacts", "❌ Erreur suppression: ${e.message}")
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Supprimer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
fun AdminContactListItem(
    contact: ContactData,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = contact.nom,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF134252),
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Modifier",
                tint = Color(0xFF0066CC),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateContactDialog(
    allDepots: List<String>,
    allTags: List<String>,
    onDismiss: () -> Unit,
    onCreate: (ContactData) -> Unit
) {
    var nom by remember { mutableStateOf("") }
    var telephones by remember { mutableStateOf(listOf("")) }
    var emails by remember { mutableStateOf(listOf("")) }
    var selectedDepots by remember { mutableStateOf(setOf<String>()) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var notes by remember { mutableStateOf("") }
    var showDepotDropdown by remember { mutableStateOf(false) }
    var showTagDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un contact", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Nom
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it },
                    label = { Text("Nom *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Téléphones
                Text("Téléphones", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF999999))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    telephones.forEachIndexed { index, phone ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = {
                                    telephones = telephones.toMutableList().apply { set(index, it) }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                placeholder = { Text("+32 2 123 45 67") }
                            )
                            if (index > 0) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Supprimer",
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable {
                                            telephones = telephones.toMutableList().apply { removeAt(index) }
                                        }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { telephones = telephones + "" },
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+Téléphone", fontSize = 12.sp)
                    }
                }

                // Emails
                Text("Emails", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF999999))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    emails.forEachIndexed { index, email ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = {
                                    emails = emails.toMutableList().apply { set(index, it) }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                placeholder = { Text("email@example.com") }
                            )
                            if (index > 0) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Supprimer",
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable {
                                            emails = emails.toMutableList().apply { removeAt(index) }
                                        }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { emails = emails + "" },
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+Email", fontSize = 12.sp)
                    }
                }

                // Dépôts multi-sélection
                Text("Dépôts", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF999999))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showDepotDropdown = !showDepotDropdown },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (selectedDepots.isEmpty()) "Sélectionner dépôts"
                            else "${selectedDepots.size} dépôt${if (selectedDepots.size > 1) "s" else ""}",
                            color = Color(0xFF134252),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ExpandMore, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showDepotDropdown,
                        onDismissRequest = { showDepotDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tous") },
                            onClick = { selectedDepots = setOf("All"); showDepotDropdown = false }
                        )
                        allDepots.forEach { depot ->
                            DropdownMenuItem(
                                text = { Text(depot) },
                                onClick = {
                                    selectedDepots = if (selectedDepots.contains(depot)) {
                                        selectedDepots - depot
                                    } else {
                                        selectedDepots + depot
                                    }
                                }
                            )
                        }
                    }
                }
                if (selectedDepots.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedDepots.forEach { depot ->
                            Chip(
                                modifier = Modifier.height(32.dp),
                                label = { Text(depot, fontSize = 12.sp) },
                                onClose = { selectedDepots = selectedDepots - depot }
                            )
                        }
                    }
                }

                // Tags
                Text("Tags", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF999999))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showTagDropdown = !showTagDropdown },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (selectedTags.isEmpty()) "Sélectionner tags"
                            else "${selectedTags.size} tag${if (selectedTags.size > 1) "s" else ""}",
                            color = Color(0xFF134252),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ExpandMore, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showTagDropdown,
                        onDismissRequest = { showTagDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        allTags.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag) },
                                onClick = {
                                    selectedTags = if (selectedTags.contains(tag)) {
                                        selectedTags - tag
                                    } else {
                                        selectedTags + tag
                                    }
                                }
                            )
                        }
                    }
                }
                if (selectedTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedTags.forEach { tag ->
                            Chip(
                                modifier = Modifier.height(32.dp),
                                label = { Text(tag, fontSize = 12.sp) },
                                onClose = { selectedTags = selectedTags - tag }
                            )
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nom.isNotBlank()) {
                        onCreate(
                            ContactData(
                                nom = nom,
                                telephones = telephones.filter { it.isNotBlank() },
                                emails = emails.filter { it.isNotBlank() },
                                depots = selectedDepots.toList(),
                                tags = selectedTags.toList(),
                                notes = notes
                            )
                        )
                    }
                },
                enabled = nom.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Créer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("Annuler")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditContactDialog(
    contact: ContactData,
    allDepots: List<String>,
    allTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (ContactData) -> Unit,
    onDelete: () -> Unit
) {
    var editedContact by remember { mutableStateOf(contact) }
    var telephones by remember { mutableStateOf(contact.telephones.ifEmpty { listOf("") }) }
    var emails by remember { mutableStateOf(contact.emails.ifEmpty { listOf("") }) }
    var selectedDepots by remember { mutableStateOf(contact.depots.toSet()) }
    var selectedTags by remember { mutableStateOf(contact.tags.toSet()) }
    var showDepotDropdown by remember { mutableStateOf(false) }
    var showTagDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier le contact", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = editedContact.nom,
                    onValueChange = { editedContact = editedContact.copy(nom = it) },
                    label = { Text("Nom *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Text("Téléphones", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF999999))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    telephones.forEachIndexed { index, phone ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = {
                                    telephones = telephones.toMutableList().apply { set(index, it) }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            if (index > 0) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Supprimer",
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable {
                                            telephones = telephones.toMutableList().apply { removeAt(index) }
                                        }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { telephones = telephones + "" },
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+Téléphone", fontSize = 12.sp)
                    }
                }

                Text("Emails", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF999999))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    emails.forEachIndexed { index, email ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = {
                                    emails = emails.toMutableList().apply { set(index, it) }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            if (index > 0) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Supprimer",
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable {
                                            emails = emails.toMutableList().apply { removeAt(index) }
                                        }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { emails = emails + "" },
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+Email", fontSize = 12.sp)
                    }
                }

                Text("Dépôts", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF999999))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showDepotDropdown = !showDepotDropdown },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (selectedDepots.isEmpty()) "Sélectionner dépôts"
                            else "${selectedDepots.size} dépôt${if (selectedDepots.size > 1) "s" else ""}",
                            color = Color(0xFF134252),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ExpandMore, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showDepotDropdown,
                        onDismissRequest = { showDepotDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        allDepots.forEach { depot ->
                            DropdownMenuItem(
                                text = { Text(depot) },
                                onClick = {
                                    selectedDepots = if (selectedDepots.contains(depot)) {
                                        selectedDepots - depot
                                    } else {
                                        selectedDepots + depot
                                    }
                                }
                            )
                        }
                    }
                }
                if (selectedDepots.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedDepots.forEach { depot ->
                            Chip(
                                modifier = Modifier.height(32.dp),
                                label = { Text(depot, fontSize = 12.sp) },
                                onClose = { selectedDepots = selectedDepots - depot }
                            )
                        }
                    }
                }

                Text("Tags", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFF999999))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showTagDropdown = !showTagDropdown },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (selectedTags.isEmpty()) "Sélectionner tags"
                            else "${selectedTags.size} tag${if (selectedTags.size > 1) "s" else ""}",
                            color = Color(0xFF134252),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ExpandMore, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showTagDropdown,
                        onDismissRequest = { showTagDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        allTags.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag) },
                                onClick = {
                                    selectedTags = if (selectedTags.contains(tag)) {
                                        selectedTags - tag
                                    } else {
                                        selectedTags + tag
                                    }
                                }
                            )
                        }
                    }
                }
                if (selectedTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedTags.forEach { tag ->
                            Chip(
                                modifier = Modifier.height(32.dp),
                                label = { Text(tag, fontSize = 12.sp) },
                                onClose = { selectedTags = selectedTags - tag }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = editedContact.notes,
                    onValueChange = { editedContact = editedContact.copy(notes = it) },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                }

                Button(
                    onClick = {
                        onSave(editedContact.copy(
                            telephones = telephones.filter { it.isNotBlank() },
                            emails = emails.filter { it.isNotBlank() },
                            depots = selectedDepots.toList(),
                            tags = selectedTags.toList()
                        ))
                    },
                    modifier = Modifier.weight(4f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Sauvegarder", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun Chip(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = modifier
            .background(Color(0xFF0066CC), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        label()
        Icon(
            Icons.Default.Close,
            contentDescription = "Supprimer",
            tint = Color.White,
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = onClose)
        )
    }
}