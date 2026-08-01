package com.minos2020.immichswipe.core

data class SessionConfig(
    val baseUrl: String,
    val apiKey: String,
    val userId: String = ""
)

/**
 * Définit comment l'application doit gérer l'Audio Focus (le son par rapport aux autres apps).
 */
enum class PlaybackBehavior {
    PAUSE_OTHERS, // Coupe les autres sons (Musique)
    IGNORE        // Joue par dessus sans rien changer
}

/**
 * Position des icônes d'action sur l'écran.
 */
enum class IconPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

/**
 * Mode d'affichage des médias dans les cartes de tri.
 */
enum class CardDisplayMode {
    FILL, // Remplit toute la carte (Crop)
    FIT   // Affiche l'image entière (Fit)
}

/**
 * Ordre de tri des médias.
 */
enum class SortOrder {
    CHRONOLOGICAL_DESC, // Nouveau -> Ancien (Par défaut)
    CHRONOLOGICAL_ASC,  // Ancien -> Nouveau
    SHUFFLED            // Aléatoire
}
