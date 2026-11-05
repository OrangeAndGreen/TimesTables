package com.viggi.timestables.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viggi.timestables.Screen
import com.viggi.timestables.domain.EndReason
import com.viggi.timestables.domain.Phase

@Composable
fun ResultScreen(result: Screen.Result, onDone: () -> Unit, onAgain: () -> Unit) {
    val d = result.drill
    val end = d.phase as Phase.Finished
    val won = end.reason == EndReason.MAX_SCORE

    Surface(Modifier.fillMaxSize(), color = if (won) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (won) {
                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                    label = "scale",
                )
                Text("🎉", fontSize = 96.sp, modifier = Modifier.scale(pulse))
                Text(
                    "Congratulations!",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "You reached ${d.settings.maxScore}!",
                    style = MaterialTheme.typography.headlineSmall,
                )
            } else {
                Text(reasonText(end.reason), style = MaterialTheme.typography.headlineMedium)
                Text("${d.score}", fontSize = 96.sp, fontWeight = FontWeight.Bold)
                Text("points", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "✓ ${d.right} right   ✗ ${d.wrong} wrong" + if (d.timeouts > 0) "   ⏱ ${d.timeouts} out of time" else "",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text("in ${formatClock(d.activeMillis(end.at))}", style = MaterialTheme.typography.titleMedium)
            result.record?.averageRightMillis?.let {
                Text("${formatSeconds(it)} for a right answer, on average", style = MaterialTheme.typography.bodyMedium)
            }
            if (result.record == null) {
                Text("Nothing was answered, so this one isn't in the history.", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Go again", fontSize = 24.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}
