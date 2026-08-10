package com.markvoronin.immichswipe.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.markvoronin.immichswipe.core.AppLogger
import com.markvoronin.immichswipe.data.local.dao.SwipeDecisionDao
import com.markvoronin.immichswipe.data.local.dao.AlbumAssetDao
import com.markvoronin.immichswipe.data.local.dao.UserAccountDao
import com.markvoronin.immichswipe.data.local.entity.SwipeDecisionEntity
import com.markvoronin.immichswipe.data.local.entity.SyncHistoryEntity
import com.markvoronin.immichswipe.data.local.entity.AlbumAssetEntity
import com.markvoronin.immichswipe.data.local.entity.UserAccountEntity

/**
 * La base de données principale de l'application.
 * Elle centralise les accès via les DAOs.
 */
@Database(
    entities = [SwipeDecisionEntity::class, SyncHistoryEntity::class, AlbumAssetEntity::class, UserAccountEntity::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun swipeDecisionDao(): SwipeDecisionDao
    abstract fun albumAssetDao(): AlbumAssetDao
    abstract fun userAccountDao(): UserAccountDao

    companion object {
        /**
         * Migration ROOM de la version 9 vers la version 10.
         * - Modifie la table 'album_assets' pour inclure 'userId' dans la clé primaire.
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AppLogger.i("Database", "Exécution Migration 9 -> 10 (Modification album_assets)")
                // 1. Supprimer l'ancienne table (on repart à zéro car on n'a pas les userId)
                db.execSQL("DROP TABLE IF EXISTS album_assets")
                
                // 2. Créer la nouvelle table avec userId
                db.execSQL("""
                    CREATE TABLE album_assets (
                        albumId TEXT NOT NULL,
                        assetId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        PRIMARY KEY(albumId, assetId, userId)
                    )
                """.trimIndent())
                
                // 3. Recréer les index
                db.execSQL("CREATE INDEX IF NOT EXISTS index_album_assets_assetId ON album_assets (assetId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_album_assets_userId ON album_assets (userId)")
            }
        }

        /**
         * Migration ROOM de la version 8 vers la version 9.
         * - Ajoute la table 'user_accounts' pour le multi-compte.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AppLogger.i("Database", "Exécution Migration 8 -> 9 (Ajout user_accounts)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_accounts (
                        userId TEXT NOT NULL,
                        baseUrl TEXT NOT NULL,
                        apiKey TEXT NOT NULL,
                        userName TEXT,
                        userEmail TEXT NOT NULL,
                        avatarColor TEXT,
                        lastActive INTEGER NOT NULL,
                        PRIMARY KEY(userId)
                    )
                """.trimIndent())
            }
        }

        /**
         * Migration ROOM de la version 7 vers la version 8.
         * - Ajoute la colonne 'wasSyncedSkip' à 'swipe_decisions'.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AppLogger.i("Database", "Exécution Migration 7 -> 8 (Ajout wasSyncedSkip)")
                db.execSQL("ALTER TABLE swipe_decisions ADD COLUMN wasSyncedSkip INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration ROOM de la version 6 vers la version 7.
         * - Modifie la clé primaire de 'swipe_decisions' pour retirer 'albumId'.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AppLogger.i("Database", "Exécution Migration 6 -> 7 (Modification PK swipe_decisions)")
                // 1. Créer la nouvelle table sans albumId dans la PK
                db.execSQL("""
                    CREATE TABLE swipe_decisions_new (
                        assetId TEXT NOT NULL,
                        albumId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        decision TEXT NOT NULL,
                        fileSize INTEGER,
                        createdAt INTEGER NOT NULL,
                        isSynced INTEGER NOT NULL,
                        PRIMARY KEY(assetId, userId)
                    )
                """.trimIndent())

                // 2. Copier les données uniques par (assetId, userId)
                // En cas de conflit (plusieurs albums pour une photo), on garde la plus récente
                db.execSQL("""
                    INSERT OR REPLACE INTO swipe_decisions_new (assetId, albumId, userId, decision, fileSize, createdAt, isSynced)
                    SELECT assetId, albumId, userId, decision, fileSize, createdAt, isSynced
                    FROM swipe_decisions
                    GROUP BY assetId, userId
                """.trimIndent())

                // 3. Remplacer l'ancienne table
                db.execSQL("DROP TABLE swipe_decisions")
                db.execSQL("ALTER TABLE swipe_decisions_new RENAME TO swipe_decisions")
            }
        }
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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
