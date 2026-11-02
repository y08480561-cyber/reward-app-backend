package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Secure in-app leaderboard screen
 */
@Composable
fun LeaderboardScreen(
    leaderboard: List<com.example.model.LeaderboardEntry>,
    isLoading: Boolean,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.ui.theme.NavyPrimary)
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth().shadow(6.dp),
            color = com.example.ui.theme.NavySecondary
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت", tint = Color.White)
                }
                Text("جدول رتبه‌بندی", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = com.example.ui.theme.GoldAccent)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                items(leaderboard) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = if (entry.isCurrentUser) com.example.ui.theme.GoldAccent.copy(alpha = 0.2f) else Color.White)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${entry.rank}", fontWeight = FontWeight.Bold)
                            Text(entry.displayName, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                            Text("${entry.totalScore} امتیاز", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
