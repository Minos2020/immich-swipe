package com.markvoronin.immichswipe.feature.auth

sealed class AuthError {
    object EmptyFields : AuthError()
    object Dns : AuthError()
    object Timeout : AuthError()
    object Refused : AuthError()
    object Auth : AuthError()
    object Forbidden : AuthError()
    object NotFound : AuthError()
    object Ssl : AuthError()
    data class Server(val code: Int) : AuthError()
    data class Unknown(val message: String?) : AuthError()
}

data class AuthUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val isLoading: Boolean = false,
    val error: AuthError? = null,
    val success: Boolean = false
)
