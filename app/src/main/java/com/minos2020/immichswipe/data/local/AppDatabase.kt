package com.minos2020.immichswipe.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.minos2020.immichswipe.core.AppLogger
import com.minos2020.immichswipe.data.local.dao.SwipeDecisionDao
import com.minos2020.immichswipe.data.local.dao.AlbumAssetDao
import com.minos2020.immichswipe.data.local.entity.SwipeDecisionEntity
import com.minos2020.immichswipe.data.local.entity.SyncHistoryEntity
import com.minos2020.immichswipe.data.local.entity.AlbumAssetEntity

/**
 * La base de données principale de l'application.
 * Elle centralise les accès via les DAOs.
 */
@Database(
    entities = [SwipeDecisionEntity::class, SyncHistoryEntity::class, AlbumAssetEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun swipeDecisionDao(): SwipeDecisionDao
    abstract fun albumAssetDao(): AlbumAssetDao

    companion object {
        /**
         * Migration ROOM de la version 5 vers la version 6.
         * - Ajoute la table 'album_assets' pour synchroniser les compteurs entre albums.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AppLogger.i("Database", "Exécution Migration 5 -> 6 (Ajout album_assets)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS album_assets (
                        albumId TEXT NOT NULL,
                        assetId TEXT NOT NULL,
                        PRIMARY KEY(albumId, assetId)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_album_assets_assetId ON album_assets (assetId)")
            }
        }
        /**
         * Migration ROOM de la version 2 vers la version 3.
         * - Ajoute la colonne 'fileSize'.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AppLogger.i("Database", "Exécution Migration 2 -> 3 (Ajout fileSize)")
                db.execSQL("ALTER TABLE swipe_decisions ADD COLUMN fileSize INTEGER DEFAULT NULL")
            }
        }

        /**
         * Migration ROOM de la version 3 vers la version 4.
         * - Ajoute la colonne 'userId' à la clé primaire.
         * - Comme on ne peut pas modifier la PK en SQLite, on recrée la table.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AppLogger.i("Database", "Exécution Migration 3 -> 4 (Ajout userId PK)")
                // 1. Créer la nouvelle table avec la nouvelle structure
                db.execSQL("""
                    CREATE TABLE swipe_decisions_new (
                        assetId TEXT NOT NULL,
                        albumId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        decision TEXT NOT NULL,
                        fileSize INTEGER,
                        createdAt INTEGER NOT NULL,
                        isSynced INTEGER NOT NULL,
                        PRIMARY KEY(assetId, albumId, userId)
                    )
                """.trimIndent())

                // 2. Copier les données existantes.
                // On met 'legacy_user' temporairement, il sera mis à jour au premier lancement.
                db.execSQL("""
                    INSERT INTO swipe_decisions_new (assetId, albumId, userId, decision, fileSize, createdAt, isSynced)
                    SELECT assetId, albumId, 'legacy_user', decision, fileSize, createdAt, isSynced
                    FROM swipe_decisions
                """.trimIndent())

                // 3. Supprimer l'ancienne table et renommer la nouvelle
                db.execSQL("DROP TABLE swipe_decisions")
                db.execSQL("ALTER TABLE swipe_decisions_new RENAME TO swipe_decisions")
            }
        }

        /**
         * Migration ROOM de la version 4 vers la version 5.
         * - Ajoute la table 'sync_history' pour les statistiques multi-compte.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AppLogger.i("Database", "Exécution Migration 4 -> 5 (Ajout sync_history)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        deletedCount INTEGER NOT NULL,
                        bytesSaved INTEGER NOT NULL,
                        keptCount INTEGER NOT NULL,
                        archivedCount INTEGER NOT NULL,
                        lockedCount INTEGER NOT NULL,
                        skippedCount INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                                context.applicationContext,
                                AppDatabase::class.java,
                                "immich_swipe_database"
                            )
                    // On enregistre nos scripts de migration
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
