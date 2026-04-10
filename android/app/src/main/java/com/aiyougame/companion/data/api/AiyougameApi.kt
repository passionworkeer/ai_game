package com.aiyougame.companion.data.api

import com.aiyougame.companion.data.model.*
import retrofit2.http.*

/**
 * Retrofit interface for the Aiyougame backend API v1.
 * Base URL is configured in NetworkModule to point to http://10.0.2.2:3000/api/v1/
 * (10.0.2.2 is the Android emulator's alias for the host machine's localhost).
 */
interface AiyougameApi {

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    @POST("auth/device")
    suspend fun deviceRegister(
        @Body request: DeviceRegisterRequest
    ): ApiResponse<DeviceRegisterResponse>

    // -------------------------------------------------------------------------
    // Characters
    // -------------------------------------------------------------------------

    @GET("characters")
    suspend fun getCharacters(): ApiResponse<CharacterListResponse>

    // -------------------------------------------------------------------------
    // Purchase
    // -------------------------------------------------------------------------

    @POST("purchase/verify")
    suspend fun verifyPurchase(
        @Body request: VerifyPurchaseRequest
    ): ApiResponse<VerifyPurchaseResponse>

    @GET("purchases")
    suspend fun getPurchases(): ApiResponse<PurchaseListResponse>

    // -------------------------------------------------------------------------
    // Sync / Profile
    // -------------------------------------------------------------------------

    @GET("sync/{userId}")
    suspend fun getSyncProfile(
        @Path("userId") userId: String
    ): ApiResponse<SyncProfileResponse>

    @POST("sync/{userId}")
    suspend fun updateSyncProfile(
        @Path("userId") userId: String,
        @Body request: SyncProfileRequest
    ): ApiResponse<SyncProfileResponse>
}
