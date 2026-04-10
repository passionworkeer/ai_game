package com.aiyougame.companion.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import com.aiyougame.companion.ui.chat.ChatMessageUi
import com.aiyougame.companion.ui.chat.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPurchase: () -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigateToProfile() }
                    ) {
                        Surface(shape = CircleShape, modifier = Modifier.size(36.dp), color = Color(0xFFE1BEE7)) {
                            Box(contentAlignment = Alignment.Center) { Text("顾", color = Color.White) }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("顾晨", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
                            Text("在线", fontSize = 12.sp, color = Color(0xFF07C160))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFE0E2)) {
                            Text(
                                "❤️ ${uiState.affectionScore}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 12.sp,
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToPurchase) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Purchase", tint = Color.Gray)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF7F7F7))
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                onInputChanged = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                        coroutineScope.launch { listState.animateScrollToItem(uiState.messages.size) }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFEFEFEF))
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(uiState.messages, key = { it.id }) { msg ->
                when {
                    msg.isToolCall -> ToolCallCard(msg.text)
                    msg.isTyping -> TypingIndicatorBubble()
                    else -> ChatBubble(msg)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessageUi) {
    val isUser = msg.isFromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(shape = CircleShape, modifier = Modifier.size(40.dp), color = Color(0xFFE1BEE7)) {
                Box(contentAlignment = Alignment.Center) { Text("顾", color = Color.White) }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) Color(0xFF95EC69) else Color.White,
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            Text(
                text = msg.text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                fontSize = 16.sp,
                color = Color(0xFF111111)
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(shape = CircleShape, modifier = Modifier.size(40.dp), color = Color(0xFFB0BEC5)) {
                Box(contentAlignment = Alignment.Center) { Text("我", color = Color.White) }
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, modifier = Modifier.size(40.dp), color = Color(0xFFE1BEE7)) {
            Box(contentAlignment = Alignment.Center) { Text("顾", color = Color.White) }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = Color.White,
            modifier = Modifier.width(60.dp).height(40.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DotAnimator(delay = 0)
                DotAnimator(delay = 150)
                DotAnimator(delay = 300)
            }
        }
    }
}

@Composable
fun DotAnimator(delay: Int) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delay, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.Gray.copy(alpha = alpha)))
}

@Composable
fun ToolCallCard(info: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.8f)) {
            Text(
                text = "✨ $info",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = Color(0xFFF7F7F7), shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("跟顾晨说些什么吧...", color = Color.LightGray) },
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSend,
                enabled = inputText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF07C160)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("发送", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
