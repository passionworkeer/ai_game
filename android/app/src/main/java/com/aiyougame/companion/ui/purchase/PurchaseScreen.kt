package com.aiyougame.companion.ui.purchase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiyougame.companion.data.model.CharacterDto
import com.aiyougame.companion.ui.purchase.PurchaseViewModel.PurchaseUiState
import com.aiyougame.companion.ui.purchase.PurchaseViewModel.PurchaseResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(
    viewModel: PurchaseViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val purchaseResult by viewModel.purchaseResult.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCharacters()
    }

    // Handle purchase result dialog
    when (val result = purchaseResult) {
        is PurchaseResult.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetPurchaseResult() },
                title = { Text("购买成功") },
                text = { Text("角色已解锁，快去聊天吧～") },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetPurchaseResult() }) {
                        Text("好的")
                    }
                }
            )
        }
        is PurchaseResult.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetPurchaseResult() },
                title = { Text("购买失败") },
                text = { Text(result.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetPurchaseResult() }) {
                        Text("确定")
                    }
                }
            )
        }
        else -> { /* no dialog */ }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("解锁陪伴", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF7F7F7))
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is PurchaseUiState.Loading -> {
                    CircularProgressIndicator(color = Color(0xFF07C160))
                }
                is PurchaseUiState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, fontSize = 16.sp, color = Color.Red)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadCharacters() }) {
                            Text("重试")
                        }
                    }
                }
                is PurchaseUiState.Success -> {
                    PurchaseSuccessContent(
                        state = state,
                        purchaseResult = purchaseResult,
                        onPurchase = { charId, price ->
                            viewModel.verifyPurchase(charId, "alipay", price)
                        }
                    )
                }
                PurchaseUiState.Idle -> {
                    // auto-loads via LaunchedEffect
                }
            }
        }
    }
}

@Composable
private fun PurchaseSuccessContent(
    state: PurchaseUiState.Success,
    purchaseResult: PurchaseResult,
    onPurchase: (String, Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "解锁「顾晨」的完整世界",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "· 端侧离线大模型驱动\n· 完整记忆提取系统\n· OpenClaw 物理世界联动\n· 绝对隐私保护",
                fontSize = 16.sp,
                color = Color.Gray,
                lineHeight = 24.sp,
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Unlocked characters
        if (state.unlockedCharacters.isNotEmpty()) {
            item {
                Text("可解锁角色", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
            }
            items(state.unlockedCharacters) { character ->
                CharacterPurchaseCard(
                    character = character,
                    isLoading = purchaseResult is PurchaseResult.Loading,
                    onPurchase = { onPurchase(character.id, character.price) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Owned characters
        if (state.ownedCharacters.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("已拥有角色", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
            }
            items(state.ownedCharacters) { character ->
                CharacterOwnedCard(character)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
private fun CharacterPurchaseCard(
    character: CharacterDto,
    isLoading: Boolean,
    onPurchase: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F7)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(character.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
                    Text(character.description, fontSize = 12.sp, color = Color.Gray)
                }
                Text("¥${character.price / 100.0}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF07C160))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onPurchase,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF07C160)),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("立即解锁 ¥${character.price / 100.0}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CharacterOwnedCard(character: CharacterDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(character.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
                Text(character.description, fontSize = 12.sp, color = Color.Gray)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF07C160)) {
                Text(
                    "已拥有",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
    }
}
