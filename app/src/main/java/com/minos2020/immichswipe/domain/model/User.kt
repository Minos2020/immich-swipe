package com.minos2020.immichswipe.domain.model

data class User(
    val id: String,
    val email: String,
    val name: String?,
    val profileImagePath: String? = null,
    val avatarColor: String? = null,
    val quotaSizeInBytes: Long? = null,
    val quotaUsageInBytes: Long? = null
)