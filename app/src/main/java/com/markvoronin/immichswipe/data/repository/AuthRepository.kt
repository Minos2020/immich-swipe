package com.markvoronin.immichswipe.data.repository

import com.markvoronin.immichswipe.core.SessionConfig
import com.markvoronin.immichswipe.data.api.RetrofitFactory
import com.markvoronin.immichswipe.domain.model.User

/**
 * Repository responsable de l'authentification.
 * Il ne dépend pas d'une API déjà initialisée car son rôle est justement 
 * de vérifier si les identifiants fournis permettent de créer une session.
 */
class AuthRepository {

    /**
     * Vérifie si les identifiants fournis sont valides en tentant un appel à l'API.
     * @return L'utilisateur connecté si succès, sinon lève une exception.
     */
    suspend fun checkCredentials(baseUrl: String, apiKey: String): User {
        // On crée une configuration temporaire avec ce que l'utilisateur a saisi
        val config = SessionConfig(baseUrl = baseUrl, apiKey = apiKey)
        
        // On demande à la factory de nous créer une instance de l'API dédiée à ce test
        val tempApi = RetrofitFactory.create(config)
        
        // On tente de récupérer le profil de l'utilisateur. 
        // Si l'URL ou la Clé est mauvaise, Retrofit lèvera une exception ici.
        return tempApi.getCurrentUser()
    }
}
