package com.minos2020.immichswipe.core

import androidx.compose.ui.graphics.Color

/**
 * Retourne la couleur Compose correspondant au nom de couleur Immich.
 */
fun getAvatarColor(colorName: String?): Color {
    return when (colorName?.lowercase()) {
        "primary" -> Color(0xFFadcbfa)
        "pink" -> Color(0xFFE91E63)
        "red" -> Color(0xFFF44336)
        "yellow" -> Color(0xFFFFEB3B)
        "blue" -> Color(0xFF2196F3)
        "green" -> Color(0xFF4CAF50)
        "purple" -> Color(0xFF9C27B0)
        "orange" -> Color(0xFFFF9800)
        "gray", "grey" -> Color(0xFF9E9E9E)
        "amber" -> Color(0xFFFFC107)
        "cyan" -> Color(0xFF00BCD4)
        "indigo" -> Color(0xFF3F51B5)
        "lime" -> Color(0xFFCDDC39)
        "teal" -> Color(0xFF009688)
        else -> Color(0xFF9C27B0) // Valeur par défaut (violet)
    }
}
