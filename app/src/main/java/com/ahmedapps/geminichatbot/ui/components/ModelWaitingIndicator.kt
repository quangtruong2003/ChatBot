package com.ahmedapps.geminichatbot.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedapps.geminichatbot.R

@Composable
fun ModelWaitingIndicator(
    isWaitingForResponse: Boolean = true
) {
    if (!isWaitingForResponse) return
    
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = when {
        isSystemInDarkTheme() -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }
    
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.9f
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        // Icon (Avatar)
        Image(
            painter = painterResource(id = R.drawable.ic_bot),
            contentDescription = "Chatbot Avatar",
            modifier = Modifier
                .padding(top = 7.dp)
                .size(30.dp)
                .clip(CircleShape)
                .align(Alignment.Top)
        )

        Column(horizontalAlignment = Alignment.Start) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .clip(RoundedCornerShape(15.dp))
                    .background(backgroundColor)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Đang suy nghĩ...",
                        modifier = Modifier
                            .alpha(alpha),
                        fontSize = 16.sp,
                        color = if (isDarkTheme) Color.White else Color.DarkGray
                    )
                }
            }
        }
    }
} 