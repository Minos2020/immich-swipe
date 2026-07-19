package com.minos2020.immichswipe.data.local.entity

import androidx.room.Entity

/**
 * Représente une décision de tri (Swipe) pour un asset donné.
 *
 * La clé primaire est désormais uniquement (assetId, userId) pour permettre
 * une synchronisation bidirectionnelle parfaite entre albums et collections.
 *
 * @property assetId L'identifiant unique de la photo/vidéo sur le serveur Immich.
 * @property albumId L'identifiant de l'album où l'action a été initialement faite (informatif).
 * @property userId L'identifiant de l'utilisateur.
 * @property decision La décision prise : "KEEP", "DELETE", "SKIP", "ARCHIVE", "LOCK".
 * @property createdAt Le moment où la décision a été prise.
 * @property isSynced Indique si cette décision a été synchronisée avec le serveur Immich.
 */
@Entity(
    tableName = "swipe_decisions",
    primaryKeys = ["assetId", "userId"]
)
data class SwipeDecisionEntity(
    val assetId: String,
    val albumId: String,
    val userId: String,
    val decision: String,
    val fileSize: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
