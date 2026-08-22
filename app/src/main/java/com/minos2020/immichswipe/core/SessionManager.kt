package com.minos2020.immichswipe.core

import com.minos2020.immichswipe.data.api.ImmichApi
import com.minos2020.immichswipe.data.api.RetrofitFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.graphics.Color

/**
 * Représente les différents niveaux de santé de la connexion.
 */
enum class ConnectionLevel(val color: Color) {
    ONLINE(Color(0xFF4CAF50)),  // Vert
    ISSUES(Color(0xFFFF9800)),  // Orange
    OFFLINE(Color(0xFFF44336))  // Rouge
}

/**
 * Types de messages de diagnostic prédéfinis pour la traduction.
 */
enum class DiagStatus {
    CONNECTED,
    AUTH_ERROR,
    UNAVAILABLE,
    UNEXPECTED,
    DNS_ERROR,
    TIMEOUT,
    NO_INTERNET,
    CONNECTION_ERROR,
    LOGGED_OUT,
    UNKNOWN
}

data class ConnectionStatus(
    val level: ConnectionLevel = ConnectionLevel.ONLINE,
    val type: DiagStatus = DiagStatus.CONNECTED,
    val statusCode: Int? = null,
    val rawMessage: String? = null,
    val lastUpdate: Long = System.currentTimeMillis()
)

object SessionManager {

    /**
     * Graine aléatoire stable pour toute la durée de vie du processus de l'application.
     * Permet de garder le même ordre "Random" au sein d'une même session utilisateur.
     */
    val appSessionSeed: Long = System.currentTimeMillis()

    private var config: SessionConfig? = null

    var api: ImmichApi? = null
        private set

    // Flux global indiquant la santé de la connexion.
    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus = _connectionStatus.asStateFlow()

    fun updateStatus(level: ConnectionLevel, type: DiagStatus, statusCode: Int? = null, rawMessage: String? = null) {
        _connectionStatus.value = ConnectionStatus(level, type, statusCode, rawMessage)
    }

    fun initialize(config: SessionConfig) {
        this.config = config
        this.api = RetrofitFactory.create(config)
    }

    fun clear() {
        config = null
        api = null
        _connectionStatus.value = ConnectionStatus(ConnectionLevel.OFFLINE, DiagStatus.LOGGED_OUT)
    }

    fun isLoggedIn(): Boolean = api != null
    fun getBaseUrl(): String? = config?.baseUrl
    fun getApiKey(): String? = config?.apiKey
    fun getUserId(): String? = config?.userId
}
