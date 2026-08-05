package com.minos2020.immichswipe

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.minos2020.immichswipe.core.AppTheme
import com.minos2020.immichswipe.core.AppLogger
import com.minos2020.immichswipe.feature.home.HomeScreen
import com.minos2020.immichswipe.core.SessionManager
import com.minos2020.immichswipe.ui.theme.ImmichSwipeTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minos2020.immichswipe.data.repository.SessionRepository
import com.minos2020.immichswipe.data.repository.AuthRepository
import com.minos2020.immichswipe.data.repository.AlbumRepository
import com.minos2020.immichswipe.data.repository.AssetRepository
import com.minos2020.immichswipe.data.repository.SwipeDecisionRepository
import com.minos2020.immichswipe.data.repository.AccountRepository
import com.minos2020.immichswipe.data.local.AppDatabase
import com.minos2020.immichswipe.feature.auth.AuthScreen
import com.minos2020.immichswipe.feature.auth.AuthViewModelFactory
import com.minos2020.immichswipe.feature.common.LoadingScreen
import com.minos2020.immichswipe.feature.home.HomeViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.init(applicationContext)
        AppLogger.i("MainActivity", "Application démarrée")
        enableEdgeToEdge()
        
        // On verrouille l'application en mode Portrait par défaut.
        // On ne le fait qu'une seule fois au démarrage pour permettre 
        // les changements dynamiques ensuite.
        if (savedInstanceState == null) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val sessionRepository = SessionRepository(applicationContext)
        val authRepository = AuthRepository()
        
        // Initialisation de la base de données Room et des Repositories
        val database = AppDatabase.getDatabase(applicationContext)
        val swipeDecisionRepository = SwipeDecisionRepository(database.swipeDecisionDao())
        val accountRepository = AccountRepository(database.userAccountDao())

        setContent {
            val appViewModel: AppViewModel = viewModel(
                factory = AppViewModelFactory(sessionRepository)
            )
            val state by appViewModel.uiState.collectAsState()

            // Détermination du thème à appliquer
            val useDarkTheme = when (state.themeMode) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ImmichSwipeTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // On utilise AnimatedContent directement sans Scaffold parent 
                    // car chaque écran (Home, Auth) possède son propre Scaffold
                    AnimatedContent(
                        targetState = state,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                        },
                        label = "ScreenTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { targetState ->
                        when {
                            targetState.isLoading -> {
                                LoadingScreen()
                            }

                            targetState.isLoggedIn -> {
                                val activeUserId = targetState.activeUserId
                                val api = SessionManager.api
                                val baseUrl = SessionManager.getBaseUrl()
                                val apiKey = SessionManager.getApiKey()
                                
                                if (api != null && baseUrl != null && apiKey != null && activeUserId != null) {
                                    // On utilise l'ID utilisateur + URL + Clé API comme clé pour forcer
                                    // le rafraîchissement total si la session change.
                                    val sessionKey = "$activeUserId-$baseUrl-$apiKey"
                                    
                                    key(sessionKey) {
                                        val albumRepository = remember(sessionKey) { AlbumRepository(api) }
                                        val assetRepository = remember(sessionKey) { 
                                            AssetRepository(
                                                api, 
                                                database.albumAssetDao()
                                            ) 
                                        }

                                        HomeScreen(
                                            viewModel = viewModel(
                                                key = sessionKey,
                                                factory = HomeViewModelFactory(
                                                    sessionRepository, 
                                                    albumRepository, 
                                                    swipeDecisionRepository,
                                                    assetRepository,
                                                    accountRepository
                                                )
                                            ),
                                            assetRepository = assetRepository,
                                            swipeDecisionRepository = swipeDecisionRepository,
                                            sessionKey = sessionKey
                                        )
                                    }
                                } else {
                                    // Sécurité : si l'API n'est plus là mais qu'on est noté connecté,
                                    // on affiche un chargement le temps que l'état se synchronise.
                                    LoadingScreen()
                                }
                            }

                            else -> {
                                AuthScreen(
                                    viewModel = viewModel(
                                        factory = AuthViewModelFactory(sessionRepository, authRepository, accountRepository)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
