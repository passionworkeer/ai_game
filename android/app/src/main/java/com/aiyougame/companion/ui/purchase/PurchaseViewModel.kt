package com.aiyougame.companion.ui.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyougame.companion.data.model.CharacterDto
import com.aiyougame.companion.data.model.VerifyPurchaseRequest
import com.aiyougame.companion.data.repository.CharactersRepository
import com.aiyougame.companion.data.repository.PurchaseRepository
import com.aiyougame.companion.data.prefs.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Purchase screen state holder.
 * Handles character catalog loading and in-app purchase verification.
 */
@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val charactersRepository: CharactersRepository,
    private val purchaseRepository: PurchaseRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    sealed class PurchaseUiState {
        object Idle : PurchaseUiState()
        object Loading : PurchaseUiState()
        data class Success(
            val ownedCharacters: List<CharacterDto>,
            val unlockedCharacters: List<CharacterDto>,
        ) : PurchaseUiState()
        data class Error(val message: String) : PurchaseUiState()
    }

    sealed class PurchaseResult {
        object Idle : PurchaseResult()
        object Loading : PurchaseResult()
        object Success : PurchaseResult()
        data class Error(val message: String) : PurchaseResult()
    }

    private val _uiState = MutableStateFlow<PurchaseUiState>(PurchaseUiState.Idle)
    val uiState: StateFlow<PurchaseUiState> = _uiState.asStateFlow()

    private val _purchaseResult = MutableStateFlow<PurchaseResult>(PurchaseResult.Idle)
    val purchaseResult: StateFlow<PurchaseResult> = _purchaseResult.asStateFlow()

    fun loadCharacters() {
        viewModelScope.launch {
            _uiState.value = PurchaseUiState.Loading
            charactersRepository.getCharacters()
                .onSuccess { characters ->
                    _uiState.value = PurchaseUiState.Success(
                        ownedCharacters = characters.filter { it.isOwned },
                        unlockedCharacters = characters.filter { !it.isOwned },
                    )
                }
                .onFailure { e ->
                    _uiState.value = PurchaseUiState.Error(e.message ?: "加载失败")
                }
        }
    }

    fun verifyPurchase(characterId: String, channel: String, amount: Int) {
        viewModelScope.launch {
            _purchaseResult.value = PurchaseResult.Loading
            purchaseRepository.verifyPurchase(
                VerifyPurchaseRequest(
                    characterId = characterId,
                    channel = channel,
                    channelOrderId = "order-${System.currentTimeMillis()}",
                    paidAmount = amount,
                    paidAt = System.currentTimeMillis(),
                    signature = null,
                )
            ).onSuccess {
                _purchaseResult.value = PurchaseResult.Success
                loadCharacters() // refresh list
            }.onFailure { e ->
                val msg = e.message ?: "购买失败"
                _purchaseResult.value = when {
                    msg.contains("已购买", ignoreCase = true) -> PurchaseResult.Error("已购买该角色")
                    msg.contains("金额不足", ignoreCase = true) -> PurchaseResult.Error("支付金额不足")
                    else -> PurchaseResult.Error(msg)
                }
            }
        }
    }

    fun resetPurchaseResult() {
        _purchaseResult.value = PurchaseResult.Idle
    }
}
