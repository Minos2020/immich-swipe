package com.markvoronin.immichswipe.domain.model

data class User(
    val id: String,
    val email: String,
    val name: String?,
    val profileImagePath: String? = null,
    val avatarColor: String? = null
)