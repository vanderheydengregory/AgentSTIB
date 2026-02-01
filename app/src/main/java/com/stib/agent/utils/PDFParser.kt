package com.stib.agent.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ServicePartie(
    val heure_debut: String,
    val heure_fin: String,
    val ligne: String
)

data class ParsedService(
    val date: String,
    val service: String,
    val partie1: ServicePartie,
    val partie2: ServicePartie? = null,  // null si service normal
    val bus: List<String> = emptyList(),
    val date_import: String,
    val note: String = "",
    val planchette: String = ""
)

object PDFParser {
    private const val TAG = "PDFParser"

    // ✅ REGEX CORRIGÉE : numéro de ligne maintenant OPTIONNEL
    private val serviceLine = Regex("""(\d{2}/\d{2}/\d{2})\s+(\d{5})(?:\s+(\d{3}))?""")
    // REGEX pour extraire TOUTES les heures (format "D HH:MM")
    private val timePattern = Regex("""D\s+(\d{2}):(\d{2})""")
    // REGEX pour extraire les numéros de ligne (3 chiffres)
    private val lignePattern = Regex("""\b(\d{3})\b""")

    fun parsePDFAdvanced(context: Context, uri: Uri): List<ParsedService> {
        try {
            Log.d(TAG, "🔧 Initialisation PDFBox")
            PDFBoxResourceLoader.init(context)

            Log.d(TAG, "📂 Ouverture du fichier")
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Impossible d'ouvrir le fichier PDF")

            Log.d(TAG, "📖 Chargement du document PDF")
            val document = PDDocument.load(inputStream)
            Log.d(TAG, "📄 Nombre de pages: ${document.numberOfPages}")

            val pdfStripper = PDFTextStripper()
            Log.d(TAG, "🔍 Extraction du texte...")
            val text = pdfStripper.getText(document)
            document.close()

            Log.d(TAG, "✅ Texte extrait: ${text.length} caractères")

            if (text.isEmpty()) {
                Log.e(TAG, "❌ Le texte extrait est vide !")
                return emptyList()
            }

            return parseTextAdvanced(text)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur parsing PDF avancé: ${e.message}", e)
            e.printStackTrace()
            throw e
        }
    }

    private fun parseTextAdvanced(text: String): List<ParsedService> {
        val services = mutableListOf<ParsedService>()
        val lines = text.lines()
        var servicesFound = 0

        Log.d(TAG, "🔍 Parsing avancé de ${lines.size} lignes")

        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEachIndexed

            // Cherche la date + service + ligne (optionnel)
            val serviceMatch = serviceLine.find(trimmedLine)

            if (serviceMatch != null) {
                servicesFound++
                Log.d(TAG, "🎯 Match service #$servicesFound ligne $index: $trimmedLine")

                val groups = serviceMatch.groupValues
                val dateStr = groups[1] // 26/01/26
                val serviceNum = groups[2] // 41343
                val ligneEnTete = groups.getOrNull(3) // Peut être null si absent

                // Extraire TOUTES les heures (format D HH:MM)
                val allTimes = timePattern.findAll(trimmedLine).map {
                    "${it.groupValues[1]}:${it.groupValues[2]}"
                }.toList()

                // ✅ AMÉLIORÉ : Extraire toutes les lignes, en ignorant la première si elle est le numéro de service
                val allLignes = lignePattern.findAll(trimmedLine).map {
                    it.groupValues[1]
                }.filter { ligne ->
                    // Éviter de considérer le numéro de service comme une ligne
                    ligne != serviceNum
                }.toList()

                // ✅ Si une ligne est présente en en-tête, la placer en premier
                val lignesOrdonnees = if (ligneEnTete != null && ligneEnTete.isNotEmpty()) {
                    listOf(ligneEnTete) + allLignes.filter { it != ligneEnTete }
                } else {
                    allLignes
                }

                Log.d(TAG, "   📅 Date: $dateStr")
                Log.d(TAG, "   🚌 Service: $serviceNum")
                Log.d(TAG, "   🕐 Horaires trouvés: ${allTimes.size} → $allTimes")
                Log.d(TAG, "   🚏 Lignes trouvées: ${lignesOrdonnees.size} → $lignesOrdonnees")

                val parsedDate = convertDate(dateStr)
                if (parsedDate == null) {
                    Log.e(TAG, "   ❌ Date invalide: $dateStr")
                    return@forEachIndexed
                }

                // Si 4 heures ou plus → Service coupé (2 parties)
                if (allTimes.size >= 4) {
                    Log.d(TAG, "   ✂️ Service coupé détecté !")

                    val ligne1 = if (lignesOrdonnees.isNotEmpty()) lignesOrdonnees[0] else "000"
                    val ligne2 = if (lignesOrdonnees.size >= 2) lignesOrdonnees[1] else ligne1

                    services.add(
                        ParsedService(
                            date = parsedDate,
                            service = serviceNum,
                            partie1 = ServicePartie(
                                heure_debut = allTimes[0],
                                heure_fin = allTimes[1],
                                ligne = ligne1
                            ),
                            partie2 = ServicePartie(
                                heure_debut = allTimes[2],
                                heure_fin = allTimes[3],
                                ligne = ligne2
                            ),
                            date_import = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        )
                    )
                    Log.d(TAG, "   ✅ Service avec 2 parties créé")

                } else if (allTimes.size >= 2) {
                    // Service normal (2 heures seulement)
                    val ligneNum = if (lignesOrdonnees.isNotEmpty()) lignesOrdonnees[0] else "000"

                    services.add(
                        ParsedService(
                            date = parsedDate,
                            service = serviceNum,
                            partie1 = ServicePartie(
                                heure_debut = allTimes[0],
                                heure_fin = allTimes[1],
                                ligne = ligneNum
                            ),
                            partie2 = null,  // Pas de partie 2
                            date_import = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        )
                    )
                    Log.d(TAG, "   ✅ Service simple créé (partie 1 uniquement)")

                } else {
                    Log.w(TAG, "   ⚠️ Pas assez d'horaires trouvés (${allTimes.size})")
                }
            }
        }

        Log.d(TAG, "📊 Total: ${services.size} services créés")
        return services.sortedBy { it.date }
    }

    private fun convertDate(dateStr: String): String? {
        return try {
            val parts = dateStr.split("/")
            if (parts.size != 3) return null

            val day = parts[0]
            val month = parts[1]
            var year = parts[2].toInt()
            year = if (year < 50) 2000 + year else 1900 + year

            "$day/$month/$year"
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur conversion date: $dateStr", e)
            null
        }
    }
}