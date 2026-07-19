package com.minos2020.immichswipe.data.repository

import com.minos2020.immichswipe.data.local.dao.AlbumDecisionCount
import com.minos2020.immichswipe.data.local.dao.SwipeDecisionDao
import com.minos2020.immichswipe.data.local.entity.SwipeDecisionEntity
import com.minos2020.immichswipe.data.local.entity.SyncHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository qui gère les décisions de swipe.
 * Il fait le lien entre le ViewModel et le DAO (la base Room).
 */
class SwipeDecisionRepository(
    private val swipeDecisionDao: SwipeDecisionDao
) {
    /**
     * Observe le compte des décisions pour tous les albums d'un utilisateur.
     */
    fun getAllAlbumDecisionCounts(userId: String): Flow<List<AlbumDecisionCount>> {
        return swipeDecisionDao.getAllAlbumDecisionCounts(userId)
    }

    fun getGlobalUniqueTreatedCount(userId: String): Flow<Int> {
        return swipeDecisionDao.getGlobalUniqueTreatedCount(userId)
    }

    fun getAllDecisionsForUser(userId: String): Flow<List<SwipeDecisionEntity>> {
        return swipeDecisionDao.getAllDecisionsForUser(userId)
    }

    /**
     * Enregistre un nouveau swipe en base locale.
     */
    suspend fun saveDecision(assetId: String, albumId: String, userId: String, decision: String, fileSize: Long? = null, isSynced: Boolean = false) {
        val entity = SwipeDecisionEntity(
            assetId = assetId,
            albumId = albumId,
            userId = userId,
            decision = decision,
            fileSize = fileSize,
            createdAt = System.currentTimeMillis(),
            isSynced = isSynced
        )
        swipeDecisionDao.insertDecision(entity)
    }

    /**
     * Marque plusieurs assets comme synchronisés pour un utilisateur.
     */
    suspend fun markAsSynced(assetIds: List<String>, userId: String) {
        if (assetIds.isNotEmpty()) {
            swipeDecisionDao.markAsSynced(assetIds, userId)
        }
    }

    /**
     * Récupère toutes les décisions d'un album pour un utilisateur sous forme de Flow.
     */
    fun getDecisionsForAlbum(albumId: String, userId: String): Flow<List<SwipeDecisionEntity>> {
        return swipeDecisionDao.getDecisionsForAlbum(albumId, userId)
    }

    /**
     * Supprime une décision (si l'utilisateur veut annuler un swipe par exemple).
     */
    suspend fun removeDecision(assetId: String, albumId: String, userId: String) {
        swipeDecisionDao.deleteDecision(assetId, albumId, userId)
    }

    /**
     * Supprime plusieurs décisions d'un coup pour un album donné.
     */
    suspend fun removeDecisions(assetIds: List<String>, albumId: String, userId: String) {
        swipeDecisionDao.deleteDecisions(assetIds, albumId, userId)
    }

    /**
     * Supprime toutes les décisions liées à une liste d'assets spécifique, pour un utilisateur.
     */
    suspend fun removeDecisionsFromAllAlbums(assetIds: List<String>, userId: String) {
        swipeDecisionDao.deleteDecisionsForAllAlbums(assetIds, userId)
    }

    /**
     * Nettoie les décisions d'un album pour un utilisateur.
     */
    suspend fun clearAlbumDecisions(albumId: String, userId: String) {
        swipeDecisionDao.deleteDecisionsForAlbum(albumId, userId)
    }

    /**
     * Récupère toutes les décisions 'SKIP' synchronisées pour un utilisateur.
     */
    fun getSyncedSkipDecisions(userId: String): Flow<List<SwipeDecisionEntity>> {
        return swipeDecisionDao.getSyncedSkipDecisions(userId)
    }

    /**
     * Récupère le nombre de 'SKIP' synchronisés pour un utilisateur.
     */
    fun getSyncedSkipCount(userId: String): Flow<Int> {
        return swipeDecisionDao.getSyncedSkipCount(userId)
    }

    /**
     * Migre les anciennes décisions (sans userId) vers l'utilisateur actuel.
     */
    suspend fun migrateLegacyDecisions(userId: String) {
        swipeDecisionDao.migrateLegacyData(userId)
    }

    /**
     * Supprime les SKIP expirés de la base de données.
     */
    suspend fun cleanExpiredSkips(lifespanDays: Long) {
        if (lifespanDays <= 0) return
        val threshold = System.currentTimeMillis() - (lifespanDays * 24 * 60 * 60 * 1000L)
        swipeDecisionDao.deleteExpiredSkips(threshold)
    }

    /**
     * Enregistre un historique de synchronisation.
     */
    suspend fun saveSyncHistory(
        userId: String,
        deletedCount: Int,
        bytesSaved: Long,
        keptCount: Int,
        archivedCount: Int,
        lockedCount: Int,
        skippedCount: Int
    ) {
        val history = SyncHistoryEntity(
            userId = userId,
            deletedCount = deletedCount,
            bytesSaved = bytesSaved,
            keptCount = keptCount,
            archivedCount = archivedCount,
            lockedCount = lockedCount,
            skippedCount = skippedCount
        )
        swipeDecisionDao.insertSyncHistory(history)
    }

    /**
     * Récupère l'historique complet pour un utilisateur.
     */
    fun getSyncHistory(userId: String) = swipeDecisionDao.getSyncHistory(userId)

    // --- Opérations d'administration ---

    suspend fun clearAllData() {
        swipeDecisionDao.deleteAllDecisions()
        swipeDecisionDao.deleteAllSyncHistory()
    }

    suspend fun clearUserData(userId: String) {
        swipeDecisionDao.deleteAllDecisionsForUser(userId)
        swipeDecisionDao.deleteAllSyncHistoryForUser(userId)
    }

    suspend fun getAllDecisionsRaw() = swipeDecisionDao.getAllDecisionsRaw()
    suspend fun getAllDecisionsForUserRaw(userId: String) = swipeDecisionDao.getAllDecisionsForUserRaw(userId)
    suspend fun getAllSyncHistoryRaw() = swipeDecisionDao.getAllSyncHistoryRaw()
    suspend fun getAllSyncHistoryForUserRaw(userId: String) = swipeDecisionDao.getAllSyncHistoryForUserRaw(userId)

    suspend fun importData(decisions: List<SwipeDecisionEntity>, history: List<SyncHistoryEntity>) {
        swipeDecisionDao.insertDecisions(decisions)
        swipeDecisionDao.insertSyncHistoryList(history)
    }
}
