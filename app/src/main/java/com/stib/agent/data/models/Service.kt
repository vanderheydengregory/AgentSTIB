package com.stib.agent.data.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Modèle de données représentant un service STIB avec ses alarmes
 */
data class Service(
    val id: String = "",

    // Dates (format brut + parsé)
    val dateServiceRaw: String = "",
    val dateService: LocalDate? = null,
    val dateImportRaw: String = "",
    val dateImport: LocalDate? = null,

    val serviceNumber: String = "",

    // Partie 1 (format brut + parsé)
    val partie1DebutRaw: String = "",
    val partie1Debut: LocalTime? = null,
    val partie1FinRaw: String = "",
    val partie1Fin: LocalTime? = null,
    val partie1Lignes: List<String> = emptyList(),  // ✅ ["054", "056"]
    val partie1Bus: List<String> = emptyList(),     // ✅ AJOUT ["9430", "9431"]

    // Partie 2 (optionnelle, format brut + parsé)
    val hasPartie2: Boolean = false,
    val partie2DebutRaw: String? = null,
    val partie2Debut: LocalTime? = null,
    val partie2FinRaw: String? = null,
    val partie2Fin: LocalTime? = null,
    val partie2Lignes: List<String> = emptyList(),  // ✅ CHANGÉ de ? à = emptyList()
    val partie2Bus: List<String> = emptyList(),     // ✅ AJOUT ["9432"]

    // Notes
    val notes: List<String> = emptyList(),          // ✅ AJOUT ["Note 1", "Note 2"]

    // Alarmes planifiées
    val scheduledAlarms: List<ScheduledAlarm> = emptyList()
)

/**
 * Représente une alarme planifiée pour un service
 */
data class ScheduledAlarm(
    val type: AlarmType = AlarmType.APP,
    val time: String = "",                    // "11:50"
    val minutesBefore: Int? = null,           // Pour app/departure: 10, 20, etc.
    val enabled: Boolean = true,
    val label: String = "",                   // "Réveil app 1", "Départ P1"
    val icon: String = ""                     // "🔔", "🚌", "📅"
)

/**
 * Types d'alarmes possibles
 */
enum class AlarmType {
    APP,           // Réveil app
    CLOCK,         // Horloge native
    DEPARTURE,     // Rappel de départ
    DAY_BEFORE     // Notification de la veille
}
