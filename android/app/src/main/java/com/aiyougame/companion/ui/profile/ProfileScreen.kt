package com.aiyougame.companion.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

import com.aiyougame.companion.ui.profile.ProfileViewModel.ProfileUiState
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色档案", fontSize = 18.sp) },
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
                .background(Color(0xFFEFEFEF))
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is ProfileUiState.Loading -> {
                    CircularProgressIndicator(color = Color(0xFF07C160))
                }
                is ProfileUiState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, fontSize = 16.sp, color = Color.Red)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadProfile() }) {
                            Text("重试")
                        }
                    }
                }
                is ProfileUiState.Success -> {
                    ProfileSuccessContent(state)
                }
                ProfileUiState.Idle -> {
                    // do nothing — will auto-load via LaunchedEffect
                }
            }
        }
    }
}

@Composable
private fun ProfileSuccessContent(state: ProfileUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFFE1BEE7)),
            contentAlignment = Alignment.Center
        ) {
            Text("顾晨", fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(state.nickname, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
        Text("青梅竹马 / 温柔懂你", fontSize = 14.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(32.dp))

        // Affection card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("心动等级", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("❤️ 羁绊值: ${state.keyEvents.size * 10 + 50}", fontSize = 18.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                    Text("进度: ${((state.keyEvents.size * 10 + 50).coerceAtMost(100) * 100 / 100)}%", fontSize = 14.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = ((state.keyEvents.size * 10 + 50).coerceAtMost(100) / 100f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFFD32F2F),
                    trackColor = Color(0xFFFFE0E2)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Profile memory card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("关于你的记忆", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
                Spacer(modifier = Modifier.height(12.dp))

                if (state.profileJson.likes?.isNotEmpty() == true) {
                    Text("称呼你为\"${state.profileJson.likes.firstOrNull() ?: "小鱼"}\"", fontSize = 14.sp, color = Color.DarkGray)
                }
                state.profileJson.currentMood?.let {
                    Text("当前心情: $it", fontSize = 14.sp, color = Color.DarkGray)
                }

                if (state.keyEvents.isEmpty()) {
                    Text("还没有关键记忆，继续聊天吧～", fontSize = 14.sp, color = Color.Gray)
                } else {
                    state.keyEvents.forEach { event ->
                        Text("• ${event.summary}", fontSize = 14.sp, color = Color.DarkGray)
                    }
                }
            }
        }
    }
}
