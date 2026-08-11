package com.markvoronin.immichswipe.core.cache

import android.content.Context
import com.markvoronin.immichswipe.core.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gère la maintenance du cache de l'application.
 */
object CacheManager {
    private const val TAG = "CacheManager"
    private const val MAX_CACHE_SIZE = 1024 * 1024 * 1024L // 1 GB
    private const val SHARED_ASSETS_DIR = "shared_assets"
    private const val EXPIRATION_TIME_MS = 24 * 60 * 60 * 1000L // 24 Hours

    /**
     * Effectue une maintenance complète du cache.
     * À appeler au démarrage de l'application.
     */
    suspend fun performMaintenance(context: Context) = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "Démarrage de la maintenance du cache")
            
            // 1. Nettoyage des assets partagés expirés
            cleanSharedAssets(context)
            
            // 2. Vérification de la taille globale et nettoyage si nécessaire
            checkAndLimitGlobalCache(context)
            
            AppLogger.d(TAG, "Maintenance du cache terminée")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erreur lors de la maintenance du cache", e)
        }
    }

    /**
     * Supprime les fichiers temporaires de partage datant de plus de 24h.
     */
    private fun cleanSharedAssets(context: Context) {
        val sharedDir = File(context.cacheDir, SHARED_ASSETS_DIR)
        if (!sharedDir.exists()) return

        val now = System.currentTimeMillis()
        var deletedCount = 0
        var deletedSize = 0L

        sharedDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > EXPIRATION_TIME_MS) {
                val size = file.length()
                if (file.delete()) {
                    deletedCount++
                    deletedSize += size
                }
            }
        }

        if (deletedCount > 0) {
            AppLogger.i(TAG, "Nettoyage shared_assets : $deletedCount fichiers supprimés (${deletedSize / 1024} KB)")
        }
    }

    /**
     * Vérifie si le cache dépasse la limite autorisée et supprime les fichiers les plus vieux.
     */
    private fun checkAndLimitGlobalCache(context: Context) {
        val cacheDir = context.cacheDir
        val totalSize = getFolderSize(cacheDir)

        if (totalSize > MAX_CACHE_SIZE) {
            AppLogger.i(TAG, "Cache trop volumineux (${totalSize / (1024 * 1024)} MB). Lancement d'un nettoyage agressif.")
            
            // On récupère tous les fichiers (récursif) et on les trie par date
            val allFiles = getAllFiles(cacheDir).sortedBy { it.lastModified() }
            
            var currentSize = totalSize
            var deletedCount = 0
            
            // On supprime les fichiers les plus anciens jusqu'à redescendre à 70% du max
            val targetSize = (MAX_CACHE_SIZE * 0.7).toLong()
            
            for (file in allFiles) {
                if (currentSize <= targetSize) break
                
                // On ne touche pas aux fichiers critiques (ex: logs de la session actuelle)
                if (file.name == "current_logs.txt") continue
                
                val fileSize = file.length()
                if (file.delete()) {
                    currentSize -= fileSize
                    deletedCount++
                }
            }
            AppLogger.i(TAG, "Nettoyage agressif terminé : $deletedCount fichiers supprimés. Nouvelle taille : ${currentSize / (1024 * 1024)} MB")
        }
    }

    private fun getFolderSize(file: File): Long {
        var size = 0L
        if (file.isDirectory) {
            file.listFiles()?.forEach { size += getFolderSize(it) }
        } else {
            size = file.length()
        }
        return size
    }

    private fun getAllFiles(file: File): List<File> {
        val result = mutableListOf<File>()
        if (file.isDirectory) {
            file.listFiles()?.forEach { result.addAll(getAllFiles(it)) }
        } else {
            result.add(file)
        }
        return result
    }

    /**
     * Vide intégralement le cache (déclenché manuellement par l'utilisateur).
     */
    fun clearAllCache(context: Context) {
        val cacheDir = context.cacheDir
        cacheDir.listFiles()?.forEach { deleteRecursive(it) }
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { deleteRecursive(it) }
        }
        fileOrDirectory.delete()
    }
}
