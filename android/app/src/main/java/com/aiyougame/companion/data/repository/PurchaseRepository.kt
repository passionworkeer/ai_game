package com.aiyougame.companion.data.repository

import com.aiyougame.companion.data.api.AiyougameApi
import com.aiyougame.companion.data.model.PurchaseDto
import com.aiyougame.companion.data.model.VerifyPurchaseRequest
import com.aiyougame.companion.data.model.VerifyPurchaseResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for purchase verification and purchase history.
 * All network operations are dispatched to IO to avoid blocking the UI thread.
 */
@Singleton
class PurchaseRepository @Inject constructor(
    private val api: AiyougameApi
) {

    suspend fun verifyPurchase(request: VerifyPurchaseRequest): Result<VerifyPurchaseResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.verifyPurchase(request)
                if (response.success && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception(response.error?.message ?: "Purchase verification failed"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getPurchases(): Result<List<PurchaseDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPurchases()
            if (response.success && response.data != null) {
                Result.success(response.data.purchases)
            } else {
                Result.failure(Exception(response.error?.message ?: "Failed to retrieve purchases"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
