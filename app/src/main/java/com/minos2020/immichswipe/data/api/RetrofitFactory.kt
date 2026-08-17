package com.minos2020.immichswipe.data.api

import com.minos2020.immichswipe.core.ConnectionLevel
import com.minos2020.immichswipe.core.DiagStatus
import com.minos2020.immichswipe.core.SessionConfig
import com.minos2020.immichswipe.core.SessionManager
import com.minos2020.immichswipe.core.AppLogger
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object RetrofitFactory {
    fun create(config: SessionConfig): ImmichApi {
        // Intercepteur pour logger les requêtes et réponses HTTP
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Intercepteur pour ajouter automatiquement la clé API dans les headers de chaque requête.
        val apiKeyInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-api-key", config.apiKey)
                .build()
            chain.proceed(request)
        }

        // SOLUTION : Intercepteur de Diagnostic intelligent sur TOUTES les requêtes réseau
        val connectivityInterceptor = Interceptor { chain ->
            val request = chain.request()
            val urlPath = request.url.encodedPath
            try {
                val response = chain.proceed(request)
                
                if (!response.isSuccessful) {
                    val errorBody = try {
                        // On "peek" le body pour ne pas le consommer et permettre à Retrofit de le lire ensuite
                        response.peekBody(4096).string()
                    } catch (_: Exception) {
                        null
                    }

                    val baseMsg = "sur $urlPath" + if (errorBody.isNullOrBlank()) "" else " : $errorBody"

                    when (response.code) {
                        401, 403 -> {
                            AppLogger.e("Retrofit", "Erreur d'authentification (${response.code}) $baseMsg")
                            SessionManager.updateStatus(ConnectionLevel.ISSUES, DiagStatus.AUTH_ERROR)
                        }
                        404 -> {
                            AppLogger.e("Retrofit", "Ressource non trouvée (404) $baseMsg. Vérifiez l'URL du serveur.")
                        }
                        502, 503, 504 -> {
                            AppLogger.e("Retrofit", "Serveur indisponible (${response.code}) $baseMsg")
                            SessionManager.updateStatus(ConnectionLevel.ISSUES, DiagStatus.UNAVAILABLE, response.code)
                        }
                        else -> {
                            AppLogger.w("Retrofit", "Réponse inattendue (${response.code}) $baseMsg")
                            SessionManager.updateStatus(ConnectionLevel.ISSUES, DiagStatus.UNEXPECTED, response.code)
                        }
                    }
                } else {
                    SessionManager.updateStatus(ConnectionLevel.ONLINE, DiagStatus.CONNECTED)
                }
                response
            } catch (e: Exception) {
                val status = when (e) {
                    is UnknownHostException -> DiagStatus.DNS_ERROR
                    is SocketTimeoutException -> DiagStatus.TIMEOUT
                    is IOException -> DiagStatus.NO_INTERNET
                    else -> DiagStatus.CONNECTION_ERROR
                }
                AppLogger.e("Retrofit", "Erreur réseau ($status) sur $urlPath: ${e.message}", e)
                SessionManager.updateStatus(ConnectionLevel.OFFLINE, status, rawMessage = e.localizedMessage)
                throw e
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(connectivityInterceptor)
            .addInterceptor(logging)
            .build()

        // Configure Retrofit avec l'URL de base, le client HTTP et le convertisseur JSON (Gson).
        // On s'assure que l'URL se termine par un slash pour Retrofit.
        val sanitizedUrl = if (config.baseUrl.endsWith("/")) config.baseUrl else "${config.baseUrl}/"
        
        val retrofit = Retrofit.Builder()
            .baseUrl(sanitizedUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ImmichApi::class.java)
    }

}
