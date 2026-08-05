package com.minos2020.immichswipe.data.repository

import com.minos2020.immichswipe.data.local.dao.UserAccountDao
import com.minos2020.immichswipe.data.local.entity.UserAccountEntity
import com.minos2020.immichswipe.domain.model.User
import kotlinx.coroutines.flow.Flow

class AccountRepository(private val userAccountDao: UserAccountDao) {

    val allAccounts: Flow<List<UserAccountEntity>> = userAccountDao.getAllAccounts()

    suspend fun saveAccount(baseUrl: String, apiKey: String, user: User) {
        val entity = UserAccountEntity(
            userId = user.id,
            baseUrl = baseUrl,
            apiKey = apiKey,
            userName = user.name,
            userEmail = user.email,
            avatarColor = user.avatarColor,
            lastActive = System.currentTimeMillis()
        )
        userAccountDao.insertAccount(entity)
    }

    suspend fun getAccount(userId: String): UserAccountEntity? {
        return userAccountDao.getAccountById(userId)
    }

    suspend fun deleteAccount(userId: String) {
        val account = userAccountDao.getAccountById(userId)
        if (account != null) {
            userAccountDao.deleteAccount(account)
        }
    }

    suspend fun updateLastActive(userId: String) {
        userAccountDao.updateLastActive(userId)
    }
}