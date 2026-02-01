package com.stib.agent.utils

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stib.agent.ui.screens.PlanningService
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object CalendarSyncUtil {
    private const val TAG = "CalendarSyncUtil"

    fun syncAllServicesOnStartup(context: Context, userId: String, calendarId: Long) {
        try {
            if (calendarId < 0) {
                Log.d(TAG, "❌ Calendrier non sélectionné, skip sync startup")
                return
            }

            Log.d(TAG, "🚀 Startup sync - récupération des services...")
            val db = FirebaseFirestore.getInstance()

            db.collection("users")
                .document(userId)
                .collection("services")
                .get()
                .addOnSuccessListener { snapshot ->
                    // 📊 Compter TOUS les documents
                    Log.d(TAG, "📊 TOTAL documents Firestore: ${snapshot.documents.size}")

                    // 🔄 ÉTAPE 1: MIGRATION - Nettoyer les anciens eventId
                    Log.d(TAG, "🔄 Migration: nettoyage des anciens eventId...")
                    var migrationCount = 0

                    for (doc in snapshot.documents) {
                        val oldEventId = doc.getLong("eventId")
                        if (oldEventId != null) {
                            doc.reference.update("eventId", null)
                            migrationCount++
                        }
                    }

                    if (migrationCount > 0) {
                        Log.d(TAG, "✅ Migration: $migrationCount anciens eventId supprimés")
                    }

                    // 🔄 ÉTAPE 2: SYNCHRONISATION NORMALE
                    var synced = 0
                    var skippedP1 = 0
                    var skippedP2 = 0
                    var createdP1 = 0
                    var createdP2 = 0

                    for (doc in snapshot.documents) {
                        try {
                            val dateService = doc.getString("date_service") ?: ""
                            val serviceNum = doc.getString("service") ?: ""

                            // 📊 Logger CHAQUE service avec sa date
                            Log.d(TAG, "📋 Traitement: Service=$serviceNum, Date=$dateService, DocID=${doc.id}")

                            // Lire partie1 et partie2
                            val partie1 = doc.get("partie1") as? Map<*, *>
                            val p1Debut = partie1?.get("heure_debut") as? String ?: ""
                            val p1Fin = partie1?.get("heure_fin") as? String ?: ""
                            val p1Lignes = (partie1?.get("lignes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            val p1Bus = (partie1?.get("bus") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                            val partie2 = doc.get("partie2") as? Map<*, *>
                            val p2Debut = partie2?.get("heure_debut") as? String
                            val p2Fin = partie2?.get("heure_fin") as? String
                            val p2Lignes = (partie2?.get("lignes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            val p2Bus = (partie2?.get("bus") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                            // 📊 Logger si Partie 2 existe
                            val hasPartie2 = p2Debut != null && p2Fin != null && p2Fin.isNotEmpty()
                            Log.d(TAG, "   ↳ Partie1: $p1Debut-$p1Fin, Partie2: ${if (hasPartie2) "$p2Debut-$p2Fin" else "AUCUNE"}")

                            val service = PlanningService(
                                id = doc.id,
                                date = doc.getString("date") ?: "",
                                jour = doc.getString("jour") ?: "",
                                service = doc.getString("service") ?: "",
                                dateservice = doc.getString("date_service") ?: "",
                                lignes = p1Lignes + p2Lignes,
                                bus = p1Bus + p2Bus,
                                partie1Debut = p1Debut,
                                partie1Fin = p1Fin,
                                partie1Lignes = p1Lignes,
                                partie1Bus = p1Bus,
                                partie2Debut = p2Debut,
                                partie2Fin = p2Fin,
                                partie2Lignes = p2Lignes,
                                partie2Bus = p2Bus
                            )

                            // ✅ LIRE LES DEUX eventId SÉPARÉMENT
                            val eventIdPartie1 = doc.getLong("eventIdPartie1")
                            val eventIdPartie2 = doc.getLong("eventIdPartie2")

                            var needsSyncP1 = false
                            var needsSyncP2 = false

                            // Vérifier PARTIE 1
                            if (eventIdPartie1 != null && eventIdPartie1 > 0) {
                                if (eventExistsInCalendar(context, eventIdPartie1)) {
                                    Log.d(TAG, "✅ ${service.service} ($dateService) P1 déjà synchro (eventId: $eventIdPartie1)")
                                    skippedP1++
                                } else {
                                    Log.d(TAG, "⚠️ ${service.service} ($dateService) P1 eventId $eventIdPartie1 supprimé, recréation...")
                                    needsSyncP1 = true
                                }
                            } else {
                                Log.d(TAG, "➕ ${service.service} ($dateService) P1 sans eventId, création...")
                                needsSyncP1 = true
                            }

                            // Vérifier PARTIE 2 (si elle existe)
                            if (hasPartie2) {
                                if (eventIdPartie2 != null && eventIdPartie2 > 0) {
                                    if (eventExistsInCalendar(context, eventIdPartie2)) {
                                        Log.d(TAG, "✅ ${service.service} ($dateService) P2 déjà synchro (eventId: $eventIdPartie2)")
                                        skippedP2++
                                    } else {
                                        Log.d(TAG, "⚠️ ${service.service} ($dateService) P2 eventId $eventIdPartie2 supprimé, recréation...")
                                        needsSyncP2 = true
                                    }
                                } else {
                                    Log.d(TAG, "➕ ${service.service} ($dateService) P2 sans eventId, création...")
                                    needsSyncP2 = true
                                }
                            }

                            // Synchroniser si nécessaire
                            if (needsSyncP1 || needsSyncP2) {
                                val (newEventId1, newEventId2) = insertEventInCalendar(
                                    context,
                                    service,
                                    calendarId,
                                    createPartie1 = needsSyncP1,
                                    createPartie2 = needsSyncP2 && hasPartie2
                                )

                                if (newEventId1 != null) createdP1++
                                if (newEventId2 != null) createdP2++

                                if (newEventId1 != null || newEventId2 != null) {
                                    updateEventIdsInFirestore(db, userId, service.id, newEventId1, newEventId2)
                                    synced++
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erreur sync service: ${e.message}", e)
                        }
                    }

                    // ✅ RAPPORT FINAL
                    Log.d(TAG, "✅ Sync startup terminée:")
                    Log.d(TAG, "   📊 Total docs Firestore: ${snapshot.documents.size}")
                    Log.d(TAG, "   ⏭️ P1 skipped: $skippedP1, P2 skipped: $skippedP2")
                    Log.d(TAG, "   ➕ P1 créés: $createdP1, P2 créés: $createdP2")
                    Log.d(TAG, "   ✅ Services synchros: $synced")

                    // 🔧 NETTOYAGE CONDITIONNEL DES ORPHELINS
                    if (createdP1 == 0 && createdP2 == 0) {
                        Log.d(TAG, "🧹 Aucune création, lancement cleanup...")
                        cleanupOrphanEvents(context, calendarId, userId)
                    } else {
                        Log.d(TAG, "⏭️ Événements créés ($createdP1 P1, $createdP2 P2), skip cleanup")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Erreur récupération services: ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sync startup: ${e.message}", e)
        }
    }
    // 🔄 NOUVELLE FONCTION: Migrer tous les événements vers un nouveau calendrier
    fun migrateToNewCalendar(
        context: Context,
        userId: String,
        oldCalendarId: Long,
        newCalendarId: Long,
        onComplete: (success: Boolean, message: String) -> Unit
    ) {
        try {
            Log.d(TAG, "🔄 ========== MIGRATION DE CALENDRIER ==========")
            Log.d(TAG, "   Ancien calendrier: $oldCalendarId")
            Log.d(TAG, "   Nouveau calendrier: $newCalendarId")

            val db = FirebaseFirestore.getInstance()

            // ÉTAPE 1: Supprimer TOUS les événements STIB de l'ancien calendrier
            Log.d(TAG, "🗑️ ÉTAPE 1: Suppression des événements de l'ancien calendrier...")
            cleanupAllStibEvents(context, oldCalendarId)

            // ÉTAPE 2: Reset tous les eventId dans Firestore
            Log.d(TAG, "🔄 ÉTAPE 2: Reset des eventId dans Firestore...")
            resetEventIdsForAllServices(userId) { count ->
                Log.d(TAG, "✅ $count eventIds réinitialisés")

                // ÉTAPE 3: Re-synchroniser dans le nouveau calendrier
                Log.d(TAG, "🔄 ÉTAPE 3: Création des événements dans le nouveau calendrier...")

                // Petit délai pour laisser le temps à Firestore de se mettre à jour
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    syncAllServicesOnStartup(context, userId, newCalendarId)

                    Log.d(TAG, "✅ ========== MIGRATION TERMINÉE ==========")
                    onComplete(true, "✅ Migration réussie! $count services transférés")
                }, 1000)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur migration: ${e.message}", e)
            onComplete(false, "❌ Erreur: ${e.message}")
        }
    }

    private fun eventExistsInCalendar(context: Context, eventId: Long): Boolean {
        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                "${CalendarContract.Events._ID} = ?",
                arrayOf(eventId.toString()),
                null
            )

            cursor?.use {
                return it.moveToFirst()
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur vérification événement: ${e.message}")
            return false
        }
    }

    private fun eventAlreadyExists(
        context: Context,
        service: PlanningService,
        isPartie2: Boolean,
        calendarId: Long
    ): Boolean {
        try {
            val startTime = if (isPartie2) service.partie2Debut else service.partie1Debut

            val lignes = if (isPartie2) service.partie2Lignes else service.partie1Lignes
            val lignesStr = if (lignes.isNotEmpty()) {
                " L${lignes.joinToString(" L")}"
            } else {
                ""
            }

            val hasTwoParts = service.partie2Debut != null && service.partie2Fin != null && service.partie2Fin.isNotEmpty()

            val expectedTitle = if (hasTwoParts) {
                if (isPartie2) {
                    "STIB ${service.service} P2$lignesStr"
                } else {
                    "STIB ${service.service} P1$lignesStr"
                }
            } else {
                "STIB ${service.service}$lignesStr"
            }

            val startMillis = parseServiceToMillis(service.dateservice, startTime ?: service.partie1Debut)

            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DTSTART),
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} = ?",
                arrayOf(calendarId.toString(), expectedTitle),
                null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val existingStart = it.getLong(1)

                    val existingDate = java.util.Calendar.getInstance().apply { timeInMillis = existingStart }
                    val targetDate = java.util.Calendar.getInstance().apply { timeInMillis = startMillis }

                    val sameDay = existingDate.get(java.util.Calendar.YEAR) == targetDate.get(java.util.Calendar.YEAR) &&
                            existingDate.get(java.util.Calendar.DAY_OF_YEAR) == targetDate.get(java.util.Calendar.DAY_OF_YEAR)

                    if (sameDay) {
                        val existingEventId = it.getLong(0)
                        Log.d(TAG, "⚠️ Événement déjà présent pour ${service.dateservice}: $expectedTitle (ID: $existingEventId)")
                        return true
                    }
                }
            }

            return false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur vérification doublon: ${e.message}")
            return false
        }
    }

    private fun updateEventIdsInFirestore(
        db: FirebaseFirestore,
        userId: String,
        serviceId: String,
        eventIdPartie1: Long?,
        eventIdPartie2: Long?
    ) {
        val updates = mutableMapOf<String, Any>(
            "calendarSyncedAt" to System.currentTimeMillis()
        )

        if (eventIdPartie1 != null) {
            updates["eventIdPartie1"] = eventIdPartie1
        }

        if (eventIdPartie2 != null) {
            updates["eventIdPartie2"] = eventIdPartie2
        }

        db.collection("users")
            .document(userId)
            .collection("services")
            .document(serviceId)
            .update(updates)
            .addOnSuccessListener {
                Log.d(TAG, "✅ eventIds sauvegardés: P1=$eventIdPartie1, P2=$eventIdPartie2")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Erreur sauvegarde eventIds: ${e.message}")
            }
    }

    private fun insertEventInCalendar(
        context: Context,
        service: PlanningService,
        calendarId: Long,
        createPartie1: Boolean = true,
        createPartie2: Boolean = true
    ): Pair<Long?, Long?> {
        try {
            var eventId1: Long? = null
            var eventId2: Long? = null

            if (createPartie1) {
                if (eventAlreadyExists(context, service, false, calendarId)) {
                    Log.d(TAG, "⏭️ PARTIE 1 déjà présente, récupération ID...")

                    val lignesStr = if (service.partie1Lignes.isNotEmpty()) {
                        " L${service.partie1Lignes.joinToString(" L")}"
                    } else {
                        ""
                    }

                    val hasTwoParts = service.partie2Debut != null && service.partie2Fin != null && service.partie2Fin.isNotEmpty()

                    val expectedTitle = if (hasTwoParts) {
                        "STIB ${service.service} P1$lignesStr"
                    } else {
                        "STIB ${service.service}$lignesStr"
                    }

                    val startMillis = parseServiceToMillis(service.dateservice, service.partie1Debut)
                    val cursor = context.contentResolver.query(
                        CalendarContract.Events.CONTENT_URI,
                        arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DTSTART),
                        "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} = ?",
                        arrayOf(calendarId.toString(), expectedTitle),
                        null
                    )
                    cursor?.use {
                        while (it.moveToNext()) {
                            val existingStart = it.getLong(1)
                            val existingDate = java.util.Calendar.getInstance().apply { timeInMillis = existingStart }
                            val targetDate = java.util.Calendar.getInstance().apply { timeInMillis = startMillis }

                            val sameDay = existingDate.get(java.util.Calendar.YEAR) == targetDate.get(java.util.Calendar.YEAR) &&
                                    existingDate.get(java.util.Calendar.DAY_OF_YEAR) == targetDate.get(java.util.Calendar.DAY_OF_YEAR)

                            if (sameDay) {
                                eventId1 = it.getLong(0)
                                Log.d(TAG, "✅ P1 ID existant récupéré: $eventId1")
                                break
                            }
                        }
                    }
                } else {
                    eventId1 = createSingleEvent(
                        context = context,
                        service = service,
                        calendarId = calendarId,
                        isPartie2 = false,
                        startTime = service.partie1Debut,
                        endTime = service.partie1Fin,
                        lignes = service.partie1Lignes,
                        bus = service.partie1Bus
                    )
                }
            }

            if (createPartie2 && service.partie2Debut != null && service.partie2Fin != null && service.partie2Fin.isNotEmpty()) {
                if (eventAlreadyExists(context, service, true, calendarId)) {
                    Log.d(TAG, "⏭️ PARTIE 2 déjà présente, récupération ID...")

                    val lignesStr = if (service.partie2Lignes.isNotEmpty()) {
                        " L${service.partie2Lignes.joinToString(" L")}"
                    } else {
                        ""
                    }

                    val expectedTitle = "STIB ${service.service} P2$lignesStr"

                    val startMillis = parseServiceToMillis(service.dateservice, service.partie2Debut)
                    val cursor = context.contentResolver.query(
                        CalendarContract.Events.CONTENT_URI,
                        arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DTSTART),
                        "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} = ?",
                        arrayOf(calendarId.toString(), expectedTitle),
                        null
                    )
                    cursor?.use {
                        while (it.moveToNext()) {
                            val existingStart = it.getLong(1)
                            val existingDate = java.util.Calendar.getInstance().apply { timeInMillis = existingStart }
                            val targetDate = java.util.Calendar.getInstance().apply { timeInMillis = startMillis }

                            val sameDay = existingDate.get(java.util.Calendar.YEAR) == targetDate.get(java.util.Calendar.YEAR) &&
                                    existingDate.get(java.util.Calendar.DAY_OF_YEAR) == targetDate.get(java.util.Calendar.DAY_OF_YEAR)

                            if (sameDay) {
                                eventId2 = it.getLong(0)
                                Log.d(TAG, "✅ P2 ID existant récupéré: $eventId2")
                                break
                            }
                        }
                    }
                } else {
                    eventId2 = createSingleEvent(
                        context = context,
                        service = service,
                        calendarId = calendarId,
                        isPartie2 = true,
                        startTime = service.partie2Debut,
                        endTime = service.partie2Fin,
                        lignes = service.partie2Lignes,
                        bus = service.partie2Bus
                    )

                    if (eventId2 != null) {
                        Log.d(TAG, "✅ Événement PARTIE 2 créé: $eventId2")
                    }
                }
            }

            return Pair(eventId1, eventId2)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur insert événement: ${e.message}", e)
            return Pair(null, null)
        }
    }

    private fun createSingleEvent(
        context: Context,
        service: PlanningService,
        calendarId: Long,
        isPartie2: Boolean,
        startTime: String,
        endTime: String,
        lignes: List<String>,
        bus: List<String>
    ): Long? {
        try {
            val startMillis = parseServiceToMillis(service.dateservice, startTime)
            val endMillis = parseServiceToMillis(service.dateservice, endTime)

            val lignesStr = if (lignes.isNotEmpty()) {
                " L${lignes.joinToString(" L")}"
            } else {
                ""
            }

            val hasTwoParts = service.partie2Debut != null && service.partie2Fin != null && service.partie2Fin.isNotEmpty()

            val title = if (hasTwoParts) {
                if (isPartie2) {
                    "STIB ${service.service} P2$lignesStr"
                } else {
                    "STIB ${service.service} P1$lignesStr"
                }
            } else {
                "STIB ${service.service}$lignesStr"
            }

            val description = buildString {
                append("Date: ${service.dateservice}\n")
                append("Horaires: ${formatTime(startTime)} - ${formatTime(endTime)}")
                if (lignes.isNotEmpty()) {
                    append("\nLignes: L${lignes.joinToString(" L")}")
                }
                if (bus.isNotEmpty()) {
                    append("\nBus: ${bus.joinToString(", ")}")
                }
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, "Europe/Brussels")
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            val uri = context.contentResolver.insert(
                CalendarContract.Events.CONTENT_URI,
                values
            )

            val eventId = uri?.lastPathSegment?.toLongOrNull()
            if (eventId != null && eventId > 0) {
                Log.d(TAG, "✅ Événement créé: $eventId ($title)")
                return eventId
            } else {
                Log.e(TAG, "❌ Erreur insert: URI est null")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur création événement: ${e.message}", e)
            return null
        }
    }

    fun updateEventInCalendar(
        context: Context,
        eventId: Long,
        service: PlanningService
    ) {
        try {
            val startMillis = parseServiceToMillis(service.dateservice, service.partie1Debut)

            val endMillis = if (service.partie2Fin != null && service.partie2Fin.isNotEmpty()) {
                parseServiceToMillis(service.dateservice, service.partie2Fin)
            } else {
                parseServiceToMillis(service.dateservice, service.partie1Fin)
            }

            val lignesStr = if (service.lignes.isNotEmpty()) {
                " L${service.lignes.joinToString(" L")}"
            } else {
                ""
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, "STIB ${service.service}$lignesStr")
                put(CalendarContract.Events.DESCRIPTION, buildEventDescription(service))
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
            }

            val uri = CalendarContract.Events.CONTENT_URI.buildUpon()
                .appendPath(eventId.toString())
                .build()

            val updated = context.contentResolver.update(uri, values, null, null)
            if (updated > 0) {
                Log.d(TAG, "✅ Événement mis à jour: $eventId")
            } else {
                Log.e(TAG, "❌ Erreur update: aucune ligne mise à jour")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur update événement: ${e.message}", e)
        }
    }

    fun deleteEventFromCalendar(context: Context, eventId: Long) {
        try {
            val uri = CalendarContract.Events.CONTENT_URI.buildUpon()
                .appendPath(eventId.toString())
                .build()

            val deleted = context.contentResolver.delete(uri, null, null)
            if (deleted > 0) {
                Log.d(TAG, "✅ Événement supprimé: $eventId")
            } else {
                Log.e(TAG, "❌ Erreur delete: aucune ligne supprimée")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur delete événement: ${e.message}", e)
        }
    }

    fun deleteServiceEventFromCalendar(context: Context, serviceId: String, userId: String) {
        try {
            val db = FirebaseFirestore.getInstance()

            db.collection("users")
                .document(userId)
                .collection("services")
                .document(serviceId)
                .get()
                .addOnSuccessListener { doc ->
                    val eventIdPartie1 = doc.getLong("eventIdPartie1")
                    val eventIdPartie2 = doc.getLong("eventIdPartie2")

                    if (eventIdPartie1 != null && eventIdPartie1 > 0) {
                        Log.d(TAG, "🗑️ Suppression P1 eventId: $eventIdPartie1")
                        deleteEventFromCalendar(context, eventIdPartie1)
                    }

                    if (eventIdPartie2 != null && eventIdPartie2 > 0) {
                        Log.d(TAG, "🗑️ Suppression P2 eventId: $eventIdPartie2")
                        deleteEventFromCalendar(context, eventIdPartie2)
                    }

                    doc.reference.update(
                        mapOf(
                            "eventIdPartie1" to null,
                            "eventIdPartie2" to null
                        )
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting service event: ${e.message}", e)
        }
    }

    fun cleanupOrphanEvents(context: Context, targetCalendarId: Long, userId: String) {
        try {
            Log.d(TAG, "🧹 Nettoyage des événements orphelins...")
            val db = FirebaseFirestore.getInstance()

            db.collection("users")
                .document(userId)
                .collection("services")
                .get()
                .addOnSuccessListener { snapshot ->
                    val validEventIds = mutableSetOf<Long>()

                    snapshot.documents.forEach { doc ->
                        val eventIdP1 = doc.getLong("eventIdPartie1")
                        val eventIdP2 = doc.getLong("eventIdPartie2")

                        if (eventIdP1 != null && eventIdP1 > 0) {
                            validEventIds.add(eventIdP1)
                        }

                        if (eventIdP2 != null && eventIdP2 > 0) {
                            validEventIds.add(eventIdP2)
                        }
                    }

                    Log.d(TAG, "📋 ${validEventIds.size} eventIds valides dans Firestore")

                    val contentResolver = context.contentResolver
                    val cursor = contentResolver.query(
                        CalendarContract.Events.CONTENT_URI,
                        arrayOf(
                            CalendarContract.Events._ID,
                            CalendarContract.Events.TITLE
                        ),
                        "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} LIKE ?",
                        arrayOf(targetCalendarId.toString(), "STIB%"),
                        null
                    )

                    var deletedCount = 0
                    var keptCount = 0
                    cursor?.use {
                        while (it.moveToNext()) {
                            val eventId = it.getLong(0)
                            val title = it.getString(1)

                            if (!validEventIds.contains(eventId)) {
                                Log.d(TAG, "🗑️ Suppression événement orphelin: $title (ID: $eventId)")
                                deleteEventFromCalendar(context, eventId)
                                deletedCount++
                            } else {
                                Log.d(TAG, "✅ Événement valide conservé: $title (ID: $eventId)")
                                keptCount++
                            }
                        }
                    }

                    Log.d(TAG, "✅ Nettoyage terminé:")
                    Log.d(TAG, "   ✅ Conservés: $keptCount")
                    Log.d(TAG, "   🗑️ Supprimés: $deletedCount")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Erreur cleanup: ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur cleanup: ${e.message}", e)
        }
    }

    fun resetEventIdsForAllServices(userId: String, onComplete: (Int) -> Unit) {
        val db = FirebaseFirestore.getInstance()

        Log.d(TAG, "🔄 Reset de TOUS les eventId...")

        db.collection("users")
            .document(userId)
            .collection("services")
            .get()
            .addOnSuccessListener { snapshot ->
                var count = 0
                val total = snapshot.documents.size

                if (total == 0) {
                    Log.d(TAG, "⚠️ Aucun service trouvé")
                    onComplete(0)
                    return@addOnSuccessListener
                }

                snapshot.documents.forEach { doc ->
                    doc.reference.update(
                        mapOf(
                            "eventId" to null,
                            "eventIdPartie1" to null,
                            "eventIdPartie2" to null
                        )
                    ).addOnSuccessListener {
                        count++
                        Log.d(TAG, "✅ Reset ${doc.getString("service")} (${doc.getString("date_service")})")

                        if (count == total) {
                            Log.d(TAG, "✅ Reset terminé: $count services nettoyés")
                            onComplete(count)
                        }
                    }.addOnFailureListener { e ->
                        count++
                        Log.e(TAG, "❌ Erreur reset ${doc.id}: ${e.message}")

                        if (count == total) {
                            onComplete(count)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Erreur reset: ${e.message}", e)
                onComplete(0)
            }
    }

    fun cleanupAllStibEvents(context: Context, targetCalendarId: Long) {
        try {
            Log.d(TAG, "🗑️ Suppression de TOUS les événements STIB...")
            val contentResolver = context.contentResolver

            val cursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE),
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} LIKE ?",
                arrayOf(targetCalendarId.toString(), "STIB%"),
                null
            )

            var deletedCount = 0
            cursor?.use {
                while (it.moveToNext()) {
                    val eventId = it.getLong(0)
                    val title = it.getString(1)
                    Log.d(TAG, "🗑️ Suppression: $title (ID: $eventId)")
                    deleteEventFromCalendar(context, eventId)
                    deletedCount++
                }
            }

            Log.d(TAG, "✅ Suppression terminée: $deletedCount événements supprimés")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur suppression: ${e.message}", e)
        }
    }

    fun debugListAllStibEvents(context: Context, calendarId: Long) {
        try {
            Log.d(TAG, "🔍 ========== DEBUG: TOUS LES ÉVÉNEMENTS STIB ==========")
            val contentResolver = context.contentResolver

            val cursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DESCRIPTION
                ),
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.TITLE} LIKE ?",
                arrayOf(calendarId.toString(), "STIB%"),
                "${CalendarContract.Events.DTSTART} ASC"
            )

            var count = 0
            cursor?.use {
                while (it.moveToNext()) {
                    val eventId = it.getLong(0)
                    val title = it.getString(1)
                    val dtStart = it.getLong(2)
                    val description = it.getString(3) ?: ""

                    val date = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE)
                        .format(java.util.Date(dtStart))

                    count++
                    Log.d(TAG, "📅 Event #$count:")
                    Log.d(TAG, "   ID: $eventId")
                    Log.d(TAG, "   Titre: $title")
                    Log.d(TAG, "   Date: $date")
                    Log.d(TAG, "   Description: ${description.take(50)}...")
                    Log.d(TAG, "   ---")
                }
            }

            Log.d(TAG, "✅ Total événements STIB trouvés: $count")
            Log.d(TAG, "🔍 ========== FIN DEBUG ==========")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur debug: ${e.message}", e)
        }
    }

    fun debugListAllFirestoreServices(userId: String) {
        val db = FirebaseFirestore.getInstance()

        Log.d(TAG, "🔍 ========== DEBUG: TOUS LES SERVICES FIRESTORE ==========")

        db.collection("users")
            .document(userId)
            .collection("services")
            .get()
            .addOnSuccessListener { snapshot ->
                var count = 0
                snapshot.documents.forEach { doc ->
                    count++
                    val service = doc.getString("service") ?: "?"
                    val date = doc.getString("date") ?: "?"
                    val dateService = doc.getString("date_service") ?: "?"
                    val eventIdP1 = doc.getLong("eventIdPartie1")
                    val eventIdP2 = doc.getLong("eventIdPartie2")

                    Log.d(TAG, "📋 Service #$count:")
                    Log.d(TAG, "   DocID: ${doc.id}")
                    Log.d(TAG, "   Service: $service")
                    Log.d(TAG, "   Date: $date")
                    Log.d(TAG, "   DateService: $dateService")
                    Log.d(TAG, "   EventIdP1: $eventIdP1")
                    Log.d(TAG, "   EventIdP2: $eventIdP2")
                    Log.d(TAG, "   ---")
                }

                Log.d(TAG, "✅ Total services Firestore: $count")
                Log.d(TAG, "🔍 ========== FIN DEBUG ==========")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Erreur debug Firestore: ${e.message}", e)
            }
    }

    private fun buildEventTitle(service: PlanningService): String {
        val lignesStr = if (service.lignes.isNotEmpty()) {
            " L${service.lignes.joinToString(" L")}"
        } else {
            ""
        }
        return "STIB ${service.service}$lignesStr"
    }

    private fun buildEventDescription(service: PlanningService): String {
        return buildString {
            append("Date: ${service.dateservice}\n")
            append("Horaires: ${formatTime(service.partie1Debut)} - ${formatTime(service.partie1Fin)}")
            if (service.lignes.isNotEmpty()) {
                append("\nLignes: L${service.lignes.joinToString(" L")}")
            }
            if (service.bus.isNotEmpty()) {
                append("\nBus: ${service.bus.joinToString(", ")}")
            }
        }
    }

    private fun formatTime(time: String): String {
        return if (time.length == 4) {
            "${time.substring(0, 2)}:${time.substring(2)}"
        } else {
            time
        }
    }

    private fun parseServiceToMillis(dateStr: String, timeStr: String): Long {
        try {
            val dateParts = dateStr.split("/")
            if (dateParts.size != 3) {
                Log.e(TAG, "❌ Format date invalide: $dateStr")
                return System.currentTimeMillis()
            }

            val day = dateParts[0].toInt()
            val month = dateParts[1].toInt()
            val year = dateParts[2].toInt()

            val cleanTime = timeStr.replace(":", "")
            if (cleanTime.length != 4) {
                Log.e(TAG, "❌ Format heure invalide: $timeStr (cleaned: $cleanTime)")
                return System.currentTimeMillis()
            }

            val hour = cleanTime.substring(0, 2).toInt()
            val minute = cleanTime.substring(2, 4).toInt()

            Log.d(TAG, "📅 Parsing: $day/$month/$year à $hour:$minute")

            val date = LocalDate.of(year, month, day)
            val time = LocalTime.of(hour, minute)

            val zonedDateTime = java.time.LocalDateTime.of(date, time)
                .atZone(ZoneId.of("Europe/Brussels"))

            val millis = zonedDateTime.toInstant().toEpochMilli()

            Log.d(TAG, "✅ Parsed: $millis (${java.util.Date(millis)})")
            return millis
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur parse date/heure '$dateStr' + '$timeStr': ${e.message}", e)
            return System.currentTimeMillis()
        }
    }

    fun getAvailableCalendars(context: Context): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()

        try {
            val contentResolver = context.contentResolver

            val cursor = contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME
                ),
                null,
                null,
                null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val calendarId = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                    val displayName = it.getString(it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME))
                    val accountName = it.getString(it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME))

                    calendars.add(CalendarInfo(calendarId, displayName, accountName))
                }
            }

            Log.d(TAG, "✅ ${calendars.size} calendriers trouvés")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur récupération calendriers: ${e.message}")
        }

        return calendars
    }
}

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String
)
