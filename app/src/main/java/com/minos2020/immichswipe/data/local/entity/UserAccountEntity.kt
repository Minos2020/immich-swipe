package com.minos2020.immichswipe.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey val userId: String,
    val baseUrl: String,
    val apiKey: String,
    val userName: String?,
    val userEmail: String,
    val avatarColor: String?,
    val lastActive: Long = System.currentTimeMillis()
)