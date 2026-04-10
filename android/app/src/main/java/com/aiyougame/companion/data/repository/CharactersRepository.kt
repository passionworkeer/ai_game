package com.aiyougame.companion.data.repository

import com.aiyougame.companion.data.api.AiyougameApi
import com.aiyougame.companion.data.model.CharacterDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for fetching character catalog.
 * All network operations are dispatched to IO to avoid blocking the UI thread.
 */
@Singleton
class CharactersRepository @Inject constructor(
    private val api: AiyougameApi
) {

    suspend fun getCharacters(): Result<List<CharacterDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getCharacters()
            if (response.success && response.data != null) {
                Result.success(response.data.characters)
            } else {
                Result.failure(Exception(response.error?.message ?: "Failed to retrieve character list"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
