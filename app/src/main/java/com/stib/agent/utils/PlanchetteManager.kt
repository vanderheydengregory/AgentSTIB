package com.stib.agent.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream

object PlanchetteManager {

    private fun getPlanchettesDir(context: Context): File {
        val dir = File(context.filesDir, "planchettes")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getPlanchetteName(service: String, heureDebut: String, heureFin: String): String {
        // Nettoyer les caractères spéciaux pour le nom de fichier
        val cleanService = service.replace("[^a-zA-Z0-9]".toRegex(), "")
        val cleanDebut = heureDebut.replace(":", "")
        val cleanFin = heureFin.replace(":", "")
        return "${cleanService}_${cleanDebut}_${cleanFin}.jpg"
    }

    fun savePlanchette(
        context: Context,
        bitmap: Bitmap,
        service: String,
        heureDebut: String,
        heureFin: String
    ): File? {
        return try {
            val fileName = getPlanchetteName(service, heureDebut, heureFin)
            val file = File(getPlanchettesDir(context), fileName)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            android.util.Log.d("PlanchetteManager", "✅ Planchette saved: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            android.util.Log.e("PlanchetteManager", "❌ Error saving planchette", e)
            null
        }
    }

    fun getPlanchette(
        context: Context,
        service: String,
        heureDebut: String,
        heureFin: String
    ): Uri? {
        val fileName = getPlanchetteName(service, heureDebut, heureFin)
        val file = File(getPlanchettesDir(context), fileName)
        return if (file.exists()) {
            file.toUri()
        } else {
            null
        }
    }

    fun deletePlanchette(
        context: Context,
        service: String,
        heureDebut: String,
        heureFin: String
    ): Boolean {
        val fileName = getPlanchetteName(service, heureDebut, heureFin)
        val file = File(getPlanchettesDir(context), fileName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    fun planchetteExists(
        context: Context,
        service: String,
        heureDebut: String,
        heureFin: String
    ): Boolean {
        val fileName = getPlanchetteName(service, heureDebut, heureFin)
        val file = File(getPlanchettesDir(context), fileName)
        return file.exists()
    }
}
