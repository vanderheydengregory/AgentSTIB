package com.stib.agent.ui.admin

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

data class MemberData(
    val userId: String = "",
    val nom: String = "",
    val prenom: String = "",
    val email: String = "",
    val matricule: String = "",
    val depot: String = "",
    val telephone: String = "",
    val role: String = "user",
    val statut: String = "actif",
    val dateInscription: String = ""
)

enum class SortType {
    NOM_ASC, NOM_DESC, MATRICULE_ASC, MATRICULE_DESC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMembersTab() {
    val db = FirebaseFirestore.getInstance()
    var members by remember { mutableStateOf<List<MemberData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var sortType by remember { mutableStateOf(SortType.NOM_ASC) }
    var itemsPerPage by remember { mutableStateOf(10) }
    var currentPage by remember { mutableStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedMember by remember { mutableStateOf<MemberData?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showItemsPerPageMenu by remember { mutableStateOf(false) }

    // Charger les membres
    LaunchedEffect(Unit) {
        try {
            val snapshot = db.collection("users").get().await()
            val membersList = mutableListOf<MemberData>()

            for (doc in snapshot.documents) {
                val member = MemberData(
                    userId = doc.id,
                    nom = doc.getString("nom") ?: "",
                    prenom = doc.getString("prenom") ?: "",
                    email = doc.getString("email") ?: "",
                    matricule = doc.getString("matricule") ?: "",
                    depot = doc.getString("depot") ?: "",
                    telephone = doc.getString("telephone") ?: "",
                    role = doc.getString("role") ?: "user",
                    statut = doc.getString("statut") ?: "actif",
                    dateInscription = doc.getString("dateInscription") ?: ""
                )
                membersList.add(member)
            }

            members = membersList
            isLoading = false
            Log.d("AdminMembers", "✅ ${members.size} membres chargés")
        } catch (e: Exception) {
            Log.e("AdminMembers", "❌ Erreur: ${e.message}", e)
            isLoading = false
        }
    }

    // Filtrer et trier (recherche étendue)
    val filteredAndSortedMembers = members
        .filter {
            it.nom.contains(searchQuery, ignoreCase = true) ||
                    it.prenom.contains(searchQuery, ignoreCase = true) ||
                    it.matricule.contains(searchQuery, ignoreCase = true) ||
                    it.email.contains(searchQuery, ignoreCase = true) ||
                    it.role.contains(searchQuery, ignoreCase = true) ||
                    it.telephone.contains(searchQuery, ignoreCase = true) ||
                    it.depot.contains(searchQuery, ignoreCase = true)
        }
        .let { list ->
            when (sortType) {
                SortType.NOM_ASC -> list.sortedBy { it.nom }
                SortType.NOM_DESC -> list.sortedByDescending { it.nom }
                SortType.MATRICULE_ASC -> list.sortedBy { it.matricule }
                SortType.MATRICULE_DESC -> list.sortedByDescending { it.matricule }
            }
        }

    // Pagination
    val totalPages = (filteredAndSortedMembers.size + itemsPerPage - 1) / itemsPerPage
    val paginatedMembers = filteredAndSortedMembers
        .drop(currentPage * itemsPerPage)
        .take(itemsPerPage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFCF9))
            .padding(16.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF0066CC))
            }
        } else {
            // Stats compactes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactStatCard("Total", members.size.toString(), Color(0xFF0066CC))
                CompactStatCard("Admins", members.count { it.role == "admin" }.toString(), Color(0xFFFF6B6B))
                CompactStatCard("Actifs", members.count { it.statut == "actif" }.toString(), Color(0xFF00CC66))
            }

            // Barre de contrôle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Recherche (placeholder court)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        currentPage = 0
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Rechercher...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0066CC),
                        unfocusedBorderColor = Color(0xFFE0E0E0)
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                // Tri (menu aligné à droite)
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Trier", tint = Color(0xFF0066CC))
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.widthIn(min = 180.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Nom A→Z", fontSize = 13.sp) },
                            onClick = {
                                sortType = SortType.NOM_ASC
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortType == SortType.NOM_ASC) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF0066CC))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Nom Z→A", fontSize = 13.sp) },
                            onClick = {
                                sortType = SortType.NOM_DESC
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortType == SortType.NOM_DESC) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF0066CC))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Matricule ↑", fontSize = 13.sp) },
                            onClick = {
                                sortType = SortType.MATRICULE_ASC
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortType == SortType.MATRICULE_ASC) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF0066CC))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Matricule ↓", fontSize = 13.sp) },
                            onClick = {
                                sortType = SortType.MATRICULE_DESC
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortType == SortType.MATRICULE_DESC) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF0066CC))
                                }
                            }
                        )
                    }
                }

                // Items par page
                Box {
                    TextButton(onClick = { showItemsPerPageMenu = true }) {
                        Text("$itemsPerPage/page", fontSize = 12.sp, color = Color(0xFF0066CC))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showItemsPerPageMenu,
                        onDismissRequest = { showItemsPerPageMenu = false },
                        modifier = Modifier.widthIn(min = 140.dp)
                    ) {
                        listOf(5, 10, 20, 50).forEach { count ->
                            DropdownMenuItem(
                                text = { Text("$count par page", fontSize = 13.sp) },
                                onClick = {
                                    itemsPerPage = count
                                    currentPage = 0
                                    showItemsPerPageMenu = false
                                },
                                leadingIcon = {
                                    if (itemsPerPage == count) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF0066CC))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Info résultats
            Text(
                text = "${filteredAndSortedMembers.size} membre(s) trouvé(s)",
                fontSize = 11.sp,
                color = Color(0xFF999999),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Liste des membres (tableau simplifié : Nom, Prénom, Matricule)
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF5F5F5))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Nom", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.35f))
                            Text("Prénom", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.35f))
                            Text("Matricule", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.3f))
                            Box(modifier = Modifier.width(40.dp))
                        }
                        Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                    }

                    // Lignes
                    items(paginatedMembers) { member ->
                        MemberRow(
                            member = member,
                            onEdit = {
                                selectedMember = member
                                showEditDialog = true
                            }
                        )
                        Divider(color = Color(0xFFE0E0E0), thickness = 0.5.dp)
                    }
                }
            }

            // Pagination
            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page ${currentPage + 1} / $totalPages",
                        fontSize = 12.sp,
                        color = Color(0xFF999999)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Page précédente",
                                tint = if (currentPage > 0) Color(0xFF0066CC) else Color(0xFFCCCCCC)
                            )
                        }

                        IconButton(
                            onClick = { if (currentPage < totalPages - 1) currentPage++ },
                            enabled = currentPage < totalPages - 1
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "Page suivante",
                                tint = if (currentPage < totalPages - 1) Color(0xFF0066CC) else Color(0xFFCCCCCC)
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog d'édition complet
    if (showEditDialog && selectedMember != null) {
        EditMemberFullDialog(
            member = selectedMember!!,
            onDismiss = { showEditDialog = false },
            onSave = { updatedMember ->
                db.collection("users")
                    .document(updatedMember.userId)
                    .update(
                        mapOf(
                            "nom" to updatedMember.nom,
                            "prenom" to updatedMember.prenom,
                            "matricule" to updatedMember.matricule,
                            "email" to updatedMember.email,
                            "telephone" to updatedMember.telephone,
                            "depot" to updatedMember.depot,
                            "role" to updatedMember.role,
                            "statut" to updatedMember.statut
                        )
                    )
                    .addOnSuccessListener {
                        members = members.map {
                            if (it.userId == updatedMember.userId) updatedMember else it
                        }
                        showEditDialog = false
                        Log.d("AdminMembers", "✅ Membre modifié")
                    }
            },
            onDelete = {
                db.collection("users")
                    .document(selectedMember!!.userId)
                    .delete()
                    .addOnSuccessListener {
                        members = members.filter { it.userId != selectedMember!!.userId }
                        showEditDialog = false
                        selectedMember = null
                        Log.d("AdminMembers", "✅ Membre supprimé")
                    }
            }
        )
    }
}

@Composable
fun CompactStatCard(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.height(50.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun MemberRow(member: MemberData, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = member.nom,
            fontSize = 13.sp,
            color = Color(0xFF134252),
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = member.prenom,
            fontSize = 13.sp,
            color = Color(0xFF134252),
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = member.matricule,
            fontSize = 13.sp,
            color = Color(0xFF627C7D),
            modifier = Modifier.weight(0.3f)
        )

        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Modifier",
                tint = Color(0xFF0066CC),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMemberFullDialog(
    member: MemberData,
    onDismiss: () -> Unit,
    onSave: (MemberData) -> Unit,
    onDelete: () -> Unit
) {
    var editedMember by remember { mutableStateOf(member) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Modifier ${member.prenom} ${member.nom}", fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = editedMember.nom,
                    onValueChange = { editedMember = editedMember.copy(nom = it) },
                    label = { Text("Nom", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editedMember.prenom,
                    onValueChange = { editedMember = editedMember.copy(prenom = it) },
                    label = { Text("Prénom", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editedMember.matricule,
                    onValueChange = { editedMember = editedMember.copy(matricule = it) },
                    label = { Text("Matricule", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editedMember.email,
                    onValueChange = { editedMember = editedMember.copy(email = it) },
                    label = { Text("Email", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editedMember.telephone,
                    onValueChange = { editedMember = editedMember.copy(telephone = it) },
                    label = { Text("Téléphone", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editedMember.depot,
                    onValueChange = { editedMember = editedMember.copy(depot = it) },
                    label = { Text("Dépôt", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Divider(color = Color(0xFFE0E0E0))

                // Role
                Column {
                    Text("Rôle", fontSize = 12.sp, color = Color(0xFF999999), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = editedMember.role == "user",
                            onClick = { editedMember = editedMember.copy(role = "user") },
                            label = { Text("User", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = editedMember.role == "admin",
                            onClick = { editedMember = editedMember.copy(role = "admin") },
                            label = { Text("Admin", fontSize = 12.sp) }
                        )
                    }
                }

                // Statut
                Column {
                    Text("Statut du compte", fontSize = 12.sp, color = Color(0xFF999999), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = editedMember.statut == "actif",
                            onClick = { editedMember = editedMember.copy(statut = "actif") },
                            label = { Text("Actif", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = editedMember.statut == "suspendu",
                            onClick = { editedMember = editedMember.copy(statut = "suspendu") },
                            label = { Text("Suspendu", fontSize = 12.sp) }
                        )
                    }
                }

                Divider(color = Color(0xFFE0E0E0))

                // Bouton supprimer
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Supprimer ce membre")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(editedMember) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))
            ) {
                Text("Sauvegarder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )

    // Confirmation de suppression
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("⚠️ Confirmer la suppression") },
            text = {
                Text("Voulez-vous vraiment supprimer ${member.prenom} ${member.nom} ?\n\nCette action est irréversible.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C))
                ) {
                    Text("Supprimer définitivement")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
