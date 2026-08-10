package com.markvoronin.immichswipe.data.repository

import com.markvoronin.immichswipe.data.api.ImmichApi
import com.markvoronin.immichswipe.domain.model.User

class UserRepository(
    private val api: ImmichApi
) {
    suspend fun getCurrentUser() = api.getCurrentUser()
}