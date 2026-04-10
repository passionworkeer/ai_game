package com.aiyougame.companion.data.model

/**
 * Generic API response envelope used by all endpoints.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ApiError?
)

data class ApiError(
    val code: String,
    val message: String
)

// ---------------------------------------------------------------------------
// Auth DTOs
// ---------------------------------------------------------------------------

data class DeviceRegisterRequest(
    val deviceId: String,
    val clientVersion: String,
    val platform: String
)

data class DeviceRegisterResponse(
    val token: String,
    val expiresAt: Long,
    val userId: String
)

// ---------------------------------------------------------------------------
// Characters DTOs
// ---------------------------------------------------------------------------

data class CharacterListResponse(
    val characters: List<CharacterDto>
)

data class CharacterDto(
    val id: String,
    val code: String,
    val name: String,
    val description: String,
    val price: Int,
    val previewUrl: String?,
    val assetsUrl: String,
    val isOwned: Boolean
)

// ---------------------------------------------------------------------------
// Purchase DTOs
// ---------------------------------------------------------------------------

data class VerifyPurchaseRequest(
    val characterId: String,
    val channel: String,
    val channelOrderId: String?,
    val paidAmount: Int,
    val paidAt: Long,
    val signature: String?
)

data class VerifyPurchaseResponse(
    val purchaseId: String,
    val status: String,
    val modelUrl: String
)

data class PurchaseListResponse(
    val purchases: List<PurchaseDto>
)

data class PurchaseDto(
    val characterId: String,
    val characterCode: String,
    val characterName: String,
    val paidAt: String
)

// ---------------------------------------------------------------------------
// Sync / Profile DTOs
// ---------------------------------------------------------------------------

data class SyncProfileRequest(
    val nickname: String?,
    val profileJson: ProfileJson?
)

data class ProfileJson(
    val likes: List<String>?,
    val dislikes: List<String>?,
    val currentMood: String?,
    val importantDates: Map<String, String>?
)

data class SyncProfileResponse(
    val nickname: String,
    val profileJson: ProfileJson,
    val keyEvents: List<KeyEventDto>,
    val updatedAt: Long
)

data class KeyEventDto(
    val summary: String,
    val timestamp: Long,
    val category: String
)
