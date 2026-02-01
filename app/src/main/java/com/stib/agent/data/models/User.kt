package com.stib.agent.data.models

data class User(
    val email: String = "",
    val nom: String = "",
    val prenom: String = "",
    val depot: String = "",
    val telephone: String = "",
    val syndicat: String = "",
    val matricule: String = "",
    val role: String = "",  // ⭐ ADMIN important
    val dateInscription: String = ""
)
