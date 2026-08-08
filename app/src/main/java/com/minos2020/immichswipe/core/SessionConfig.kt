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
    SHUFFLED,           // Aléatoire
    SIZE_DESC,          // Plus gros -> Plus petit
    SIZE_ASC,           // Plus petit -> Plus gros
    TYPE_VIDEO_FIRST,   // Vidéos -> Photos (Date)
    TYPE_PHOTO_FIRST,   // Photos -> Vidéos (Date)
    TYPE_VIDEO_FIRST_SHUFFLED, // Vidéos -> Photos (Aléatoire)
    TYPE_PHOTO_FIRST_SHUFFLED  // Photos -> Vidéos (Aléatoire)
}

/**
 * Catégories de tri.
 */
enum class SortCategory {
    TIME,
    SIZE,
    TYPE
}
