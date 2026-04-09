package com.aiyougame.companion.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色档案", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF7F7F7)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFEFEFEF))
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 头像大图占位
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
            
            Text("顾晨", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
            Text("青梅竹马 / 温柔懂你", fontSize = 14.sp, color = Color.Gray)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 好感度卡片
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
                        Text("❤️ 羁绊值: 105", fontSize = 18.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                        Text("进度: 15%", fontSize = 14.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = 0.15f,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFFD32F2F),
                        trackColor = Color(0xFFFFE0E2)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI 记忆提取内容展示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("关于你的记忆", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• 称呼你为“小鱼”", fontSize = 14.sp, color = Color.DarkGray)
                    Text("• 知道你喜欢喝奶茶", fontSize = 14.sp, color = Color.DarkGray)
                    Text("• 下午三点有个周会", fontSize = 14.sp, color = Color.DarkGray)
                }
            }
        }
    }
}
