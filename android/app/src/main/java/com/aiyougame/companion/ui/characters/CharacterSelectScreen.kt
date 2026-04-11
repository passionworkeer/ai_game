package com.aiyougame.companion.ui.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiyougame.companion.data.model.CharacterDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterSelectScreen(
    viewModel: CharacterSelectViewModel = hiltViewModel(),
    onCharacterSelected: (characterCode: String) -> Unit,
    onNavigateToPurchase: (characterId: String) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择角色") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // Shopping cart: navigate to purchase screen
                    IconButton(onClick = { onNavigateToPurchase("ade85259-2bd0-44e9-a27b-2c7fdb460524") }) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "购买角色",
                            tint = Color(0xFF07C160)
                        )
                    }
                    // Debug shortcut: directly enter chat (skip character selection)
                    // TODO: Remove before production release
                    @Suppress("DEPRECATION")
                    IconButton(onClick = { onCharacterSelected("gu_chen") }) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "进入聊天",
                            tint = Color(0xFF07C160)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is CharacterSelectViewModel.UiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is CharacterSelectViewModel.UiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadCharacters() }) {
                            Text("重试")
                        }
                    }
                }
                is CharacterSelectViewModel.UiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (state.ownedCharacters.isNotEmpty()) {
                            item {
                                Text(
                                    "已解锁",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(
                            items = state.ownedCharacters,
                            key = { it.id }
                        ) { character ->
                                CharacterCard(
                                    character = character,
                                    isOwned = true,
                                    onClick = {
                                        viewModel.selectCharacter(character)
                                        onCharacterSelected(character.code)
                                    }
                                )
                            }
                        }
                        if (state.unlockedCharacters.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "未解锁",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(
                            items = state.unlockedCharacters,
                            key = { it.id }
                        ) { character ->
                                CharacterCard(
                                    character = character,
                                    isOwned = false,
                                    onClick = { onNavigateToPurchase(character.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: CharacterDto,
    isOwned: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isOwned) Color(0xFFE1BEE7) else Color.Gray

    // Use Surface with onClick instead of Card to ensure reliable tap response
    // with Android emulator `input tap` command inside LazyColumn.
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isOwned) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(borderColor)
                    .border(2.dp, borderColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = character.name.take(1),
                    fontSize = 28.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = character.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                if (!isOwned) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${character.price / 100}元",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Use IconButton explicitly for maximum tap reliability with input tap
                if (isOwned) {
                    FilledTonalButton(
                        onClick = onClick,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("开始聊天", fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = onClick,
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF07C160)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("购买", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
