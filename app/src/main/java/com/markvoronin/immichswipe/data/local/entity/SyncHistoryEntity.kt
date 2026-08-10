package com.markvoronin.immichswipe.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Représente un historique de synchronisation (validation du résumé).
 * Permet de calculer des statistiques globales et temporelles par utilisateur.
 */
@Entity(tableName = "sync_history")
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deletedCount: Int = 0,
    val bytesSaved: Long = 0,
    val keptCount: Int = 0,
    val archivedCount: Int = 0,
    val lockedCount: Int = 0,
    val skippedCount: Int = 0
)
