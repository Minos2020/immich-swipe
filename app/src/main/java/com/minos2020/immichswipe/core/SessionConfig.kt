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
 * Critères de tri pour les médias dans la pile de tri.
 */
enum class SwipeSortOrder {
    DATE_DESC, // Plus récents en premier
    DATE_ASC,  // Plus anciens en premier
    SIZE_DESC, // Plus lourds en premier
    SIZE_ASC,  // Plus légers en premier
    RANDOM     // Ordre aléatoire stable
}

/**
 * Priorité de type pour le tri des médias.
 */
enum class SwipeSortPriority {
    NONE,         // Pas de priorité de type
    VIDEOS_FIRST, // Vidéos avant les photos
    PHOTOS_FIRST  // Photos avant les vidéos
}

/**
 * Définit comment l'asset doit être ouvert dans Immich.
 */
enum class ImmichOpenMode {
    APP, // Dans l'application mobile
    WEB  // Dans le navigateur web
}
