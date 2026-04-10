package com.aiyougame.companion.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF7F7F7))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFEFEFEF))
                .padding(paddingValues)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // OpenClaw PC 联动设置
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("OpenClaw PC 联动", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "mDNS 自动发现通常无需配置。若无法连接，请手动输入 PC 的局域网 IP。",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.openClawIp,
                        onValueChange = { viewModel.setOpenClawIp(it) },
                        label = { Text("手动连接 IP 例如 192.168.x.x") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF07C160),
                            cursorColor = Color(0xFF07C160)
                        )
                    )
                    if (uiState.isSaving) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("保存中...", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 隐私和云同步设置
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("隐私与数据保护", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("开启记忆云同步", fontSize = 16.sp, color = Color(0xFF111111))
                            Text(
                                "默认关闭。开启后您的画像特征会通过加密上传至云端，聊天原文永不上传。",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = uiState.cloudSyncEnabled,
                            onCheckedChange = { viewModel.toggleCloudSync(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF07C160),
                                checkedTrackColor = Color(0xFF07C160).copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 登出按钮
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                TextButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("退出登录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
