package com.minos2020.immichswipe.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minos2020.immichswipe.data.local.entity.SwipeDecisionEntity
import com.minos2020.immichswipe.data.local.entity.SyncHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Interface pour accéder aux données des décisions de swipe en base.
 * DAO = Data Access Object
 */
@Dao
interface SwipeDecisionDao {

    /**
     * Insère ou met à jour une décision.
     * OnConflictStrategy.REPLACE permet d'écraser une ancienne décision si on swipe à nouveau la même photo.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecision(decision: SwipeDecisionEntity)

    /**
     * Récupère toutes les décisions pour un album spécifique d'un utilisateur donné.
     * Utilise désormais la table de jointure album_assets.
     */
    @Query("""
        SELECT sd.* FROM swipe_decisions sd
        JOIN album_assets aa ON sd.assetId = aa.assetId
        WHERE aa.albumId = :albumId AND sd.userId = :userId
    """)
    fun getDecisionsForAlbum(albumId: String, userId: String): Flow<List<SwipeDecisionEntity>>

    /**
     * Récupère une décision spécifique pour un asset pour un utilisateur.
     */
    @Query("SELECT * FROM swipe_decisions WHERE assetId = :assetId AND userId = :userId")
    suspend fun getDecisionForAsset(assetId: String, userId: String): SwipeDecisionEntity?
    
    /**
     * Supprime une décision spécifique pour un asset pour un utilisateur.
     */
    @Query("DELETE FROM swipe_decisions WHERE assetId = :assetId AND userId = :userId")
    suspend fun deleteDecision(assetId: String, userId: String)

    /**
     * Supprime plusieurs décisions d'un coup pour un utilisateur.
     */
    @Query("DELETE FROM swipe_decisions WHERE assetId IN (:assetIds) AND userId = :userId")
    suspend fun deleteDecisions(assetIds: List<String>, userId: String)

    /**
     * Supprime toutes les décisions liées à une liste d'assets spécifique pour un utilisateur.
     */
    @Query("DELETE FROM swipe_decisions WHERE assetId IN (:assetIds) AND userId = :userId")
    suspend fun deleteDecisionsForAllAlbums(assetIds: List<String>, userId: String)
    
    /**
     * Compte le nombre de décisions prises pour un album spécifique d'un utilisateur.
     */
    @Query("SELECT COUNT(*) FROM swipe_decisions WHERE albumId = :albumId AND userId = :userId")
    suspend fun getDecisionCountForAlbum(albumId: String, userId: String): Int

    /**
     * Marque des décisions comme synchronisées pour un utilisateur.
     */
    @Query("UPDATE swipe_decisions SET isSynced = 1, wasSyncedSkip = (CASE WHEN decision = 'SKIP' THEN 1 ELSE 0 END) WHERE assetId IN (:assetIds) AND userId = :userId")
    suspend fun markAsSynced(assetIds: List<String>, userId: String)

    /**
     * Supprime les décisions 'SKIP' plus vieilles qu'un certain timestamp.
     * On supprime TOUS les skips (synced ou non) pour qu'ils réapparaissent dans le flux de tri.
     */
    @Query("DELETE FROM swipe_decisions WHERE decision = 'SKIP' AND createdAt < :threshold")
    suspend fun deleteExpiredSkips(threshold: Long): Int

    /**
     * Récupère toutes les décisions 'SKIP' déjà synchronisées pour un utilisateur.
     * Inclut aussi les assets qui étaient SKIP et qui sont en cours de retraitement (wasSyncedSkip = 1).
     * Trié par date de création pour maintenir l'ordre chronologique.
     */
    @Query("SELECT * FROM swipe_decisions WHERE (wasSyncedSkip = 1 OR (decision = 'SKIP' AND isSynced = 1)) AND userId = :userId ORDER BY createdAt DESC")
    fun getSyncedSkipDecisions(userId: String): Flow<List<SwipeDecisionEntity>>

    /**
     * Compte le nombre de décisions 'SKIP' synchronisées pour un utilisateur.
     */
    @Query("SELECT COUNT(*) FROM swipe_decisions WHERE (wasSyncedSkip = 1 OR (decision = 'SKIP' AND isSynced = 1)) AND userId = :userId")
    fun getSyncedSkipCount(userId: String): Flow<Int>

    /**
     * Migre les données d'une version précédente (sans userId) vers l'utilisateur actuel.
     */
    @Query("UPDATE swipe_decisions SET userId = :userId WHERE userId = 'legacy_user'")
    suspend fun migrateLegacyData(userId: String)

    /**
     * Insère une entrée dans l'historique de synchronisation.
     */
    @Insert
    suspend fun insertSyncHistory(history: SyncHistoryEntity)

    /**
     * Récupère tout l'historique de synchronisation pour un utilisateur.
     */
    @Query("SELECT * FROM sync_history WHERE userId = :userId ORDER BY timestamp DESC")
    fun getSyncHistory(userId: String): Flow<List<SyncHistoryEntity>>

    @Query("SELECT COUNT(DISTINCT assetId) FROM swipe_decisions WHERE userId = :userId")
    fun getGlobalUniqueTreatedCount(userId: String): Flow<Int>

    /**
     * Récupère le nombre d'assets orphelins triés globalement.
     * Pour cela, on a besoin de savoir si l'asset est un orphelin (c'est complexe en SQL pur
     * sans la liste des orphelins, donc on va peut-être gérer ça au niveau ViewModel/Repo).
     */

    @Query("SELECT * FROM swipe_decisions WHERE userId = :userId")
    fun getAllDecisionsForUser(userId: String): Flow<List<SwipeDecisionEntity>>

    /**
     * Récupère les statistiques de décisions pour tous les albums d'un utilisateur sous forme de Flow.
     * Utilise désormais la table album_assets pour inclure les décisions prises dans d'autres albums.
     */
    @Query("""
        SELECT aa.albumId, 
               COUNT(DISTINCT sd.assetId) as totalCount, 
               SUM(CASE WHEN sd.isSynced = 0 AND (sd.wasSyncedSkip = 0 OR aa.albumId = 'virtual_skipped_synced') THEN 1 ELSE 0 END) as unsyncedCount
        FROM album_assets aa
        JOIN swipe_decisions sd ON aa.assetId = sd.assetId AND aa.userId = sd.userId
        WHERE sd.userId = :userId AND (:includeArchived = 1 OR aa.isArchived = 0)
        GROUP BY aa.albumId
    """)
    fun getAllAlbumDecisionCounts(userId: String, includeArchived: Boolean): Flow<List<AlbumDecisionCount>>

    // --- Opérations d'administration de la base de données ---

    @Query("DELETE FROM swipe_decisions")
    suspend fun deleteAllDecisions()

    @Query("DELETE FROM swipe_decisions WHERE userId = :userId")
    suspend fun deleteAllDecisionsForUser(userId: String)

    @Query("DELETE FROM sync_history")
    suspend fun deleteAllSyncHistory()

    @Query("DELETE FROM sync_history WHERE userId = :userId")
    suspend fun deleteAllSyncHistoryForUser(userId: String)

    @Query("SELECT * FROM swipe_decisions")
    suspend fun getAllDecisionsRaw(): List<SwipeDecisionEntity>

    @Query("SELECT * FROM swipe_decisions WHERE userId = :userId")
    suspend fun getAllDecisionsForUserRaw(userId: String): List<SwipeDecisionEntity>

    @Query("SELECT * FROM sync_history")
    suspend fun getAllSyncHistoryRaw(): List<SyncHistoryEntity>

    @Query("SELECT * FROM sync_history WHERE userId = :userId")
    suspend fun getAllSyncHistoryForUserRaw(userId: String): List<SyncHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecisions(decisions: List<SwipeDecisionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncHistoryList(history: List<SyncHistoryEntity>)

    /**
     * Supprime les décisions pour les photos qui n'existent plus sur le serveur.
     * Se base sur la table de cache album_assets qui doit être à jour.
     */
    @Query("""
        DELETE FROM swipe_decisions 
        WHERE userId = :userId 
        AND assetId NOT IN (SELECT DISTINCT assetId FROM album_assets WHERE userId = :userId)
    """)
    suspend fun removeGhostDecisions(userId: String): Int
}

/**
 * Objet pour transporter les statistiques par album.
 */
data class AlbumDecisionCount(
    val albumId: String,
    val totalCount: Int,
    val unsyncedCount: Int
)
