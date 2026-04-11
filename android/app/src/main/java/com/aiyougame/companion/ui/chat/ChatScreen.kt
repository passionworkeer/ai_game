package com.aiyougame.companion.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Stop
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
import com.aiyougame.companion.speech.VoiceRecognitionManager
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
    onNavigateToCharacterSelect: () -> Unit,
    onBack: () -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    var voiceState by remember { mutableStateOf<VoiceRecognitionManager.State>(VoiceRecognitionManager.State.Idle) }
    var showVoicePermissionDenied by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()

    // CRITICAL FIX: Use Hilt-injected VoiceRecognitionManager from ChatViewModel.
    // Previous manual `remember { VoiceRecognitionManager(...) }` bypassed Hilt DI,
    // causing native resource leaks and multiple AudioRecord instances.
    val voiceManager = viewModel.voiceRecognitionManager

    // Collect voice state from the Hilt-managed singleton
    LaunchedEffect(viewModel) {
        viewModel.voiceState.collect { state ->
            voiceState = state
            if (state is com.aiyougame.companion.speech.VoiceRecognitionManager.State.Done) {
                inputText = state.text
                viewModel.voiceRecognitionManager.reset()
            }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceManager.startRecording()
        } else {
            showVoicePermissionDenied = true
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
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
                    IconButton(onClick = onNavigateToCharacterSelect) {
                        Icon(Icons.Default.Person, contentDescription = "选择角色", tint = Color(0xFF07C160))
                    }
                    IconButton(onClick = onNavigateToPurchase) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "购买角色", tint = Color.Gray)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color.Gray)
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
                },
                voiceState = voiceState,
                onVoiceClick = {
                    when (voiceState) {
                        is VoiceRecognitionManager.State.Idle -> {
                            if (voiceManager.hasPermission()) {
                                voiceManager.startRecording()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        is VoiceRecognitionManager.State.Recording -> {
                            voiceManager.stopRecording()
                        }
                        is VoiceRecognitionManager.State.Recognizing -> {
                            // Do nothing while recognizing
                        }
                        is VoiceRecognitionManager.State.Done -> {
                            // Text already filled in inputText
                        }
                        is VoiceRecognitionManager.State.Error -> {
                            voiceManager.reset()
                        }
                    }
                },
                onVoiceCancel = { voiceManager.cancel() }
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

    if (showVoicePermissionDenied) {
        AlertDialog(
            onDismissRequest = { showVoicePermissionDenied = false },
            title = { Text("麦克风权限") },
            text = { Text("语音输入需要麦克风权限，请在设置中开启。") },
            confirmButton = {
                TextButton(onClick = { showVoicePermissionDenied = false }) {
                    Text("确定")
                }
            }
        )
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
    voiceState: VoiceRecognitionManager.State,
    onVoiceClick: () -> Unit,
    onVoiceCancel: () -> Unit,
) {
    val isRecording = voiceState is VoiceRecognitionManager.State.Recording
    val isRecognizing = voiceState is VoiceRecognitionManager.State.Recognizing
    val isDone = voiceState is VoiceRecognitionManager.State.Done
    val isError = voiceState is VoiceRecognitionManager.State.Error

    val micColor by animateColorAsState(
        targetValue = if (isRecording) Color(0xFFFF5252) else if (isError) Color(0xFFFF9800) else Color(0xFF757575),
        animationSpec = tween(300),
        label = "micColor"
    )

    // Pulse animation for recording state
    val pulseScale by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

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
                placeholder = {
                    Text(
                        when {
                            isRecording -> "正在录音..."
                            isRecognizing -> "识别中..."
                            isDone -> "识别完成"
                            isError -> "识别失败"
                            else -> "跟顾晨说些什么吧..."
                        },
                        color = Color.LightGray
                    )
                },
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                maxLines = 4,
                enabled = !isRecording && !isRecognizing
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Voice button
            if (isRecording || isRecognizing) {
                IconButton(
                    onClick = if (isRecording) onVoiceClick else {{}},
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size((40 * pulseScale).dp)
                                .clip(CircleShape)
                                .background(micColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "停止录音",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF757575),
                            strokeWidth = 2.dp
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "语音输入",
                        tint = micColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Button(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isRecording && !isRecognizing,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF07C160)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("发送", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
