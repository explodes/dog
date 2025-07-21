package io.explod.dog_app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.explod.dog_app.ui.theme.BrightWhite
import io.explod.dog_app.ui.theme.DarkWhite
import io.explod.dog_app.ui.theme.DarkishBlue
import io.explod.dog_app.ui.theme.DarkishGreen
import io.explod.dog_app.ui.theme.DarkishOrange
import io.explod.dog_app.ui.theme.DarkishScarlet
import io.explod.dog_app.ui.theme.DarkishYellow
import io.explod.dog_app.util.Level
import io.explod.dog_app.util.Log

@Composable
fun LogsUi(logs: List<Log>) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState) {
        items(logs) { log ->
            val (textColor, backgroundColor) =
                when (log.level) {
                    Level.DEBUG -> BrightWhite to DarkishBlue
                    Level.INFO -> BrightWhite to DarkishGreen
                    Level.WARN -> DarkWhite to DarkishYellow
                    Level.ERROR -> BrightWhite to DarkishOrange
                    Level.NEVER -> BrightWhite to DarkishScarlet
                }
            key(log.level, log.logName, log.message) {
                val logLine = with(log) { "${level.code}::$logName::$message" }
                Text(
                    text = logLine,
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    modifier =
                        Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            .fillMaxWidth()
                            .background(backgroundColor),
                )
            }
        }
    }
    // Scroll to end when logs change.
    LaunchedEffect(key1 = logs) {
        val size = logs.size
        if (size > 0) {
            listState.scrollToItem(size)
        }
    }
}
