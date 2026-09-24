package com.example.teamsync.data.repository

import kotlinx.coroutines.delay

interface AuthRepository {
    suspend fun signInWithGoogle(): Result<Unit>
}

// Simulates a successful sign-in until Credential Manager / Firebase Auth is integrated.
class FakeAuthRepository : AuthRepository {
    override suspend fun signInWithGoogle(): Result<Unit> {
        delay(600)
        return Result.success(Unit)
    }
}
