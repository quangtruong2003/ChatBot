// ModelChatItem.kt
package com.ahmedapps.geminichatbot.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import fomatText.FormattedTextDisplay
import fomatText.parseFormattedText
import androidx.compose.runtime.*



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModelChatItem(
    response: String,
    isError: Boolean,
    onLongPress: (String) -> Unit,
    onImageClick: (String) -> Unit, // You might not need this
    snackbarHostState: SnackbarHostState
) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val backgroundColor = when {
        isError -> MaterialTheme.colorScheme.error
        isSystemInDarkTheme() -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }


    val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.9f
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val scope = rememberCoroutineScope()

    // ADDED: State to track if we're waiting for a response
    var isThinking by remember { mutableStateOf(true) }

    // ADDED: Update isThinking when the response changes
    LaunchedEffect(response) {
        if (response.isNotEmpty()) {
            isThinking = false
        }
    }


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
            // MODIFIED: Show "Thinking..." or the response
            if (isThinking) {
                ThinkingAnimation()
            } else {
                val formattedResponse = parseFormattedText(response) // Xử lí ở đay
                SelectionContainer {
                    FormattedTextDisplay(
                        annotatedString = formattedResponse,
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .clip(RoundedCornerShape(15.dp))
                            .background(backgroundColor)
                            .padding(12.dp),

                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }
}

// ADDED: Composable for the blinking "Thinking..." animation
@Composable
fun ThinkingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    Text(
        text = "Thinking...",
        modifier = Modifier
            .padding(start = 8.dp, top = 4.dp)
            .alpha(alpha),
        fontSize = 16.sp,
        color = if (isSystemInDarkTheme()) Color.White else Color.DarkGray
    )
}