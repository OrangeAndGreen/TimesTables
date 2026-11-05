package com.viggi.timestables.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viggi.timestables.DrillViewModel
import com.viggi.timestables.domain.Attempt
import com.viggi.timestables.domain.Drill
import com.viggi.timestables.domain.Outcome
import com.viggi.timestables.domain.Phase

@Composable
fun DrillScreen(vm: DrillViewModel) {
    val drill by vm.drill.collectAsState()
    val typed by vm.typed.collectAsState()
    val now by vm.now.collectAsState()
    val d = drill ?: return

    val last = d.attempts.lastOrNull()
    // The background flashes green or red for a moment after each answer.
    val recent = last != null && now - last.answeredAt < 600
    val flash = when {
        d.phase is Phase.Waiting -> WrongRed.copy(alpha = 0.18f)
        recent && last!!.outcome == Outcome.RIGHT -> RightGreen.copy(alpha = 0.22f)
        recent -> WrongRed.copy(alpha = 0.22f)
        else -> Color.Transparent
    }
    val background by animateColorAsState(flash, tween(150), label = "flash")

    Surface(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(background)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(16.dp),
            ) {
                StatusBar(d, now, onPause = vm::pause, onStop = vm::stop)
                AnswerTimer(d, now)
                Spacer(Modifier.height(8.dp))
                QuestionArea(d, typed, now, Modifier.weight(1f))
                Keypad(
                    enabled = d.phase is Phase.Asking && !d.isPaused,
                    canSubmit = typed.isNotEmpty(),
                    onDigit = vm::typeDigit,
                    onBackspace = vm::backspace,
                    onSubmit = vm::submit,
                    modifier = Modifier.weight(1.2f),
                )
            }
            if (d.isPaused) PausedOverlay(onResume = vm::resume, onStop = vm::stop)
        }
    }
}

@Composable
private fun StatusBar(d: Drill, now: Long, onPause: () -> Unit, onStop: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val max = d.settings.maxScore
            Text(
                if (max != null) "${d.score} / $max" else "${d.score}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "✓ ${d.right}   ✗ ${d.wrong + d.timeouts}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val endsAt = d.endsAt
        Text(
            if (endsAt != null) formatClock(endsAt - (d.pausedAt ?: now)) + " left" else formatClock(d.activeMillis(now)),
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(onClick = onPause) { Icon(Icons.Filled.Pause, contentDescription = "Pause") }
        IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, contentDescription = "Stop") }
    }
}

@Composable
private fun AnswerTimer(d: Drill, now: Long) {
    val asking = d.phase as? Phase.Asking
    val deadline = asking?.deadline
    val limit = d.settings.answerSeconds
    if (limit == null) return
    val left = if (deadline == null) 0f else ((deadline - (d.pausedAt ?: now)).toFloat() / (limit * 1000)).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { left },
        color = if (left < 0.3f) TimeoutAmber else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(8.dp),
    )
}

@Composable
private fun QuestionArea(d: Drill, typed: String, now: Long, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val p = d.phase) {
            is Phase.Asking -> {
                Text("${p.question.a} × ${p.question.b}", fontSize = 76.sp, fontWeight = FontWeight.Bold)
                Text(
                    "= " + typed.ifEmpty { "?" },
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (typed.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                d.attempts.lastOrNull()?.let { LastAnswer(it) }
            }
            is Phase.Waiting -> {
                val m = p.missed
                Text(
                    "${m.question.a} × ${m.question.b} = ${m.question.answer}",
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold,
                    color = WrongRed,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (m.given != null) "not ${m.given}" else "out of time",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(12.dp))
                val secondsLeft = ((p.until - (d.pausedAt ?: now) + 999) / 1000).coerceAtLeast(0)
                Text("Next one in $secondsLeft…", style = MaterialTheme.typography.titleLarge)
            }
            is Phase.Finished -> Unit
        }
    }
}

/** A small reminder of the previous question, and the right answer when it was missed. */
@Composable
private fun LastAnswer(a: Attempt) {
    val q = a.question
    val (text, color) = when (a.outcome) {
        Outcome.RIGHT -> "✓ $q = ${q.answer}" to RightGreen
        Outcome.WRONG -> "✗ $q = ${q.answer}, not ${a.given}" to WrongRed
        Outcome.TIMEOUT -> "⏱ $q = ${q.answer}" to TimeoutAmber
    }
    Text(text, color = color, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Keypad(
    enabled: Boolean,
    canSubmit: Boolean,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier,
) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in row) KeyButton(enabled, { onDigit(n) }) { Text("$n", fontSize = 34.sp) }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(enabled, onBackspace, tonal = false) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
            }
            KeyButton(enabled, { onDigit(0) }) { Text("0", fontSize = 34.sp) }
            KeyButton(enabled && canSubmit, onSubmit, primary = true) {
                Icon(Icons.Filled.Check, contentDescription = "Enter")
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.KeyButton(
    enabled: Boolean,
    onClick: () -> Unit,
    tonal: Boolean = true,
    primary: Boolean = false,
    content: @Composable () -> Unit,
) {
    val modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
    val shape = RoundedCornerShape(16.dp)
    when {
        primary -> Button(onClick, modifier, enabled, shape = shape, colors = ButtonDefaults.buttonColors(containerColor = RightGreen)) { content() }
        tonal -> FilledTonalButton(onClick, modifier, enabled, shape = shape) { content() }
        else -> OutlinedButton(onClick, modifier, enabled, shape = shape) { content() }
    }
}

@Composable
private fun PausedOverlay(onResume: () -> Unit, onStop: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Paused", fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onResume, modifier = Modifier.height(64.dp)) { Text("Keep going", fontSize = 24.sp) }
            OutlinedButton(onClick = onStop) { Text("End the session") }
        }
    }
}
