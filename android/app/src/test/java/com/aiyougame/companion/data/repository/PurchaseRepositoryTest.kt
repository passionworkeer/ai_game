package com.aiyougame.companion.data.repository

import com.aiyougame.companion.data.api.AiyougameApi
import com.aiyougame.companion.data.model.*
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.*
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * TDD RED: PurchaseRepository tests — define the contract for purchase verification and listing.
 */
class PurchaseRepositoryTest {

    private val mockApi = mock<AiyougameApi>()
    private val repository = PurchaseRepository(mockApi)

    @Test
    fun `verifyPurchase returns success with modelUrl on valid purchase`() = runTest {
        val request = VerifyPurchaseRequest(
            characterId = "char-001",
            channel = "google",
            channelOrderId = "order-abc",
            paidAmount = 600,
            paidAt = System.currentTimeMillis(),
            signature = "sig-xyz"
        )

        val expectedResponse = VerifyPurchaseResponse(
            purchaseId = "purchase-123",
            status = "active",
            modelUrl = "https://cdn.example.com/gaku_model.gguf"
        )

        whenever(mockApi.verifyPurchase(any()))
            .thenReturn(ApiResponse(
                success = true,
                data = expectedResponse,
                error = null
            ))

        val result = repository.verifyPurchase(request)

        assertTrue(result.isSuccess)
        assertEquals("purchase-123", result.getOrNull()?.purchaseId)
        assertEquals("active", result.getOrNull()?.status)
        assertEquals("https://cdn.example.com/gaku_model.gguf", result.getOrNull()?.modelUrl)
    }

    @Test
    fun `verifyPurchase returns failure on duplicate purchase`() = runTest {
        whenever(mockApi.verifyPurchase(any()))
            .thenReturn(ApiResponse(
                success = false,
                data = null,
                error = ApiError(code = "PUR_002", message = "Purchase already verified")
            ))

        val request = VerifyPurchaseRequest(
            characterId = "char-001",
            channel = "google",
            channelOrderId = "order-abc",
            paidAmount = 600,
            paidAt = System.currentTimeMillis(),
            signature = "sig-xyz"
        )

        val result = repository.verifyPurchase(request)

        assertTrue(result.isFailure)
        assertEquals("Purchase already verified", result.exceptionOrNull()?.message)
    }

    @Test
    fun `verifyPurchase returns failure when network throws`() = runTest {
        whenever(mockApi.verifyPurchase(any()))
            .thenThrow(RuntimeException("SSL handshake failed"))

        val request = VerifyPurchaseRequest(
            characterId = "char-001",
            channel = "google",
            channelOrderId = null,
            paidAmount = 600,
            paidAt = System.currentTimeMillis(),
            signature = null
        )

        val result = repository.verifyPurchase(request)

        assertTrue(result.isFailure)
    }

    @Test
    fun `getPurchases returns list of purchases on success`() = runTest {
        val purchases = listOf(
            PurchaseDto(
                characterId = "char-001",
                characterCode = "gaku",
                characterName = "Yuki Gaku",
                paidAt = "2026-03-01T10:00:00Z"
            )
        )

        whenever(mockApi.getPurchases())
            .thenReturn(ApiResponse(
                success = true,
                data = PurchaseListResponse(purchases),
                error = null
            ))

        val result = repository.getPurchases()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("Yuki Gaku", result.getOrNull()?.get(0)?.characterName)
    }

    @Test
    fun `getPurchases returns failure when API fails`() = runTest {
        whenever(mockApi.getPurchases())
            .thenReturn(ApiResponse(
                success = false,
                data = null,
                error = ApiError(code = "PUR_001", message = "Failed to retrieve purchases")
            ))

        val result = repository.getPurchases()

        assertTrue(result.isFailure)
        assertEquals("Failed to retrieve purchases", result.exceptionOrNull()?.message)
    }
}
