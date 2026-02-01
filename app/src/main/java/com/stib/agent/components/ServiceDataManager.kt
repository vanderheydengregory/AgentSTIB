package com.stib.agent.components

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.stib.agent.data.model.AlarmType
import com.stib.agent.data.model.ScheduledAlarm
import com.stib.agent.data.model.Service
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Gestionnaire centralisé pour les services
 * Charge depuis Firestore et parse automatiquement les dates/heures/alarmes
 */
object ServiceDataManager {

    private const val TAG = "ServiceDataManager"

    // Cache en mémoire
    private var cachedServices: List<Service>? = null

    // Formatters pour parsing
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Parse une date depuis le format "dd/MM/yyyy"
     */
    private fun parseDate(dateString: String): LocalDate? {
        return try {
            LocalDate.parse(dateString, dateFormatter)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Erreur parsing date: $dateString")
            null
        }
    }

    /**
     * Parse une heure depuis le format "HH:mm"
     */
    private fun parseTime(timeString: String): LocalTime? {
        return try {
            LocalTime.parse(timeString, timeFormatter)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Erreur parsing heure: $timeString")
            null
        }
    }

    /**
     * Parse les alarmes depuis Firestore
     */
    private fun parseScheduledAlarms(alarmsData: Any?): List<ScheduledAlarm> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val alarmsList = alarmsData as? List<Map<String, Any>> ?: return emptyList()

            alarmsList.mapNotNull { alarmMap ->
                try {
                    val typeStr = alarmMap["type"] as? String ?: return@mapNotNull null
                    val type = when {
                        typeStr.startsWith("app") -> AlarmType.APP
                        typeStr == "clock" -> AlarmType.CLOCK
                        typeStr.startsWith("departure") -> AlarmType.DEPARTURE
                        typeStr == "veille" -> AlarmType.DAY_BEFORE
                        else -> AlarmType.APP
                    }

                    ScheduledAlarm(
                        type = type,
                        time = alarmMap["time"] as? String ?: "",
                        minutesBefore = (alarmMap["minutesBefore"] as? Long)?.toInt(),
                        enabled = alarmMap["enabled"] as? Boolean ?: true,
                        label = alarmMap["label"] as? String ?: "",
                        icon = alarmMap["icon"] as? String ?: ""
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erreur parsing alarme: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Erreur parsing scheduledAlarms: ${e.message}")
            emptyList()
        }
    }

    /**
     * ✅ FONCTION CENTRALISÉE POUR PARSER UN DOCUMENT FIRESTORE
     */
    private fun parseFirestoreDocument(doc: DocumentSnapshot): Service? {
        return try {
            val serviceId = doc.id
            val dateServiceRaw = doc.getString("date_service") ?: ""
            val dateImportRaw = doc.getString("date_import") ?: ""
            val serviceNumber = doc.getString("service") ?: ""

            val dateService = parseDate(dateServiceRaw)
            val dateImport = parseDate(dateImportRaw)

            // ✅ PARTIE 1
            @Suppress("UNCHECKED_CAST")
            val partie1Map = doc.get("partie1") as? Map<String, Any>
            val partie1DebutRaw = partie1Map?.get("heure_debut") as? String ?: ""
            val partie1FinRaw = partie1Map?.get("heure_fin") as? String ?: ""
            val partie1Debut = parseTime(partie1DebutRaw)
            val partie1Fin = parseTime(partie1FinRaw)
            val partie1Lignes = (partie1Map?.get("lignes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val partie1Bus = (partie1Map?.get("bus") as? List<*>)?.filterIsInstance<String>() ?: emptyList() // ✅ AJOUT

            // ✅ PARTIE 2
            @Suppress("UNCHECKED_CAST")
            val partie2Map = doc.get("partie2") as? Map<String, Any>
            val hasPartie2 = partie2Map != null
            val partie2DebutRaw = partie2Map?.get("heure_debut") as? String
            val partie2FinRaw = partie2Map?.get("heure_fin") as? String
            val partie2Debut = partie2DebutRaw?.let { parseTime(it) }
            val partie2Fin = partie2FinRaw?.let { parseTime(it) }
            val partie2Lignes = (partie2Map?.get("lignes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val partie2Bus = (partie2Map?.get("bus") as? List<*>)?.filterIsInstance<String>() ?: emptyList() // ✅ AJOUT

            // ✅ NOTES
            val notesData = doc.get("notes")
            val notes = when (notesData) {
                is String -> if (notesData.isNotBlank()) listOf(notesData) else emptyList()
                is List<*> -> notesData.filterIsInstance<String>()
                else -> emptyList()
            }

            val scheduledAlarms = parseScheduledAlarms(doc.get("scheduledAlarms"))

            Service(
                id = serviceId,
                dateServiceRaw = dateServiceRaw,
                dateService = dateService,
                dateImportRaw = dateImportRaw,
                dateImport = dateImport,
                serviceNumber = serviceNumber,
                partie1DebutRaw = partie1DebutRaw,
                partie1Debut = partie1Debut,
                partie1FinRaw = partie1FinRaw,
                partie1Fin = partie1Fin,
                partie1Lignes = partie1Lignes,
                partie1Bus = partie1Bus,           // ✅ AJOUT
                hasPartie2 = hasPartie2,
                partie2DebutRaw = partie2DebutRaw,
                partie2Debut = partie2Debut,
                partie2FinRaw = partie2FinRaw,
                partie2Fin = partie2Fin,
                partie2Lignes = partie2Lignes,
                partie2Bus = partie2Bus,           // ✅ AJOUT
                notes = notes,                     // ✅ AJOUT
                scheduledAlarms = scheduledAlarms
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur parsing service ${doc.id}: ${e.message}")
            null
        }
    }

    /**
     * Convertit les alarmes en format Firestore
     */
    private fun alarmsToFirestoreFormat(alarms: List<ScheduledAlarm>): List<Map<String, Any?>> {
        return alarms.map { alarm ->
            mapOf(
                "type" to when (alarm.type) {
                    AlarmType.APP -> "app"
                    AlarmType.CLOCK -> "clock"
                    AlarmType.DEPARTURE -> "departure"
                    AlarmType.DAY_BEFORE -> "veille"
                },
                "time" to alarm.time,
                "minutesBefore" to alarm.minutesBefore,
                "enabled" to alarm.enabled,
                "label" to alarm.label,
                "icon" to alarm.icon
            )
        }
    }

    /**
     * Récupère un service par son ID
     */
    suspend fun getService(serviceId: String): Service? {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Log.w(TAG, "⚠️ User non connecté")
            return null
        }

        return try {
            val firestore = FirebaseFirestore.getInstance()
            val doc = firestore.collection("users")
                .document(userId)
                .collection("services")
                .document(serviceId)
                .get()
                .await()

            if (!doc.exists()) {
                Log.w(TAG, "⚠️ Service $serviceId introuvable")
                return null
            }

            val service = parseFirestoreDocument(doc)

            if (service != null) {
                Log.d(TAG, "✅ Service chargé: $serviceId")
                Log.d(TAG, "   date: ${service.dateServiceRaw} → ${service.dateService}")
                Log.d(TAG, "   service: ${service.serviceNumber}")
                Log.d(TAG, "   P1: ${service.partie1DebutRaw} → ${service.partie1Debut}")
                Log.d(TAG, "   P1 lignes: ${service.partie1Lignes}")
                Log.d(TAG, "   P1 bus: ${service.partie1Bus}")
                if (service.hasPartie2) {
                    Log.d(TAG, "   P2: ${service.partie2DebutRaw} → ${service.partie2Debut}")
                    Log.d(TAG, "   P2 lignes: ${service.partie2Lignes}")
                    Log.d(TAG, "   P2 bus: ${service.partie2Bus}")
                }
                Log.d(TAG, "   notes: ${service.notes.size}")
                Log.d(TAG, "   alarmes: ${service.scheduledAlarms.size} planifiées")
            }

            service

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement service $serviceId: ${e.message}")
            null
        }
    }

    /**
     * Sauvegarde ou met à jour les alarmes d'un service
     */
    suspend fun updateScheduledAlarms(serviceId: String, alarms: List<ScheduledAlarm>): Boolean {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Log.w(TAG, "⚠️ User non connecté")
            return false
        }

        return try {
            val firestore = FirebaseFirestore.getInstance()

            firestore.collection("users")
                .document(userId)
                .collection("services")
                .document(serviceId)
                .update("scheduledAlarms", alarmsToFirestoreFormat(alarms))
                .await()

            Log.d(TAG, "✅ Alarmes mises à jour pour service $serviceId")

            // Invalider le cache
            cachedServices = null

            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur mise à jour alarmes: ${e.message}")
            false
        }
    }

    /**
     * Supprime les alarmes d'un service
     */
    suspend fun deleteScheduledAlarms(serviceId: String): Boolean {
        return updateScheduledAlarms(serviceId, emptyList())
    }

    /**
     * Récupère tous les services de l'utilisateur
     */
    suspend fun getAllServices(): List<Service> {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Log.w(TAG, "⚠️ User non connecté")
            return emptyList()
        }

        return try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("services")
                .get()
                .await()

            val services = snapshot.documents.mapNotNull { doc ->
                parseFirestoreDocument(doc)
            }

            // Mettre en cache
            cachedServices = services

            Log.d(TAG, "✅ ${services.size} services chargés")
            services

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement services: ${e.message}")
            emptyList()
        }
    }

    /**
     * ✅ REFRESH LE CACHE (après modification)
     */
    fun refreshServices() {
        cachedServices = null
        Log.d(TAG, "🔄 Cache invalidé")
    }

    /**
     * Récupère les services depuis le cache ou Firestore
     */
    fun getAllServicesSync(): List<Service> {
        return cachedServices ?: emptyList()
    }

    /**
     * Version callback pour récupérer un service
     */
    fun getServiceAsync(serviceId: String, callback: (Service?) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Log.w(TAG, "⚠️ User non connecté")
            callback(null)
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("users")
            .document(userId)
            .collection("services")
            .document(serviceId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Log.w(TAG, "⚠️ Service $serviceId introuvable")
                    callback(null)
                    return@addOnSuccessListener
                }

                val service = parseFirestoreDocument(doc)

                if (service != null) {
                    Log.d(TAG, "✅ Service chargé async: $serviceId")
                }

                callback(service)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Erreur Firestore: ${e.message}")
                callback(null)
            }
    }
}
