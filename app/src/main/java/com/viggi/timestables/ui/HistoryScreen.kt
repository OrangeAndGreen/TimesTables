package com.viggi.timestables.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viggi.timestables.DrillViewModel
import com.viggi.timestables.Screen
import com.viggi.timestables.domain.Outcome
import com.viggi.timestables.domain.SessionRecord
import com.viggi.timestables.domain.missedFacts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayFormat = SimpleDateFormat("EEE MMM d, h:mm a", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: DrillViewModel) {
    val sessions by vm.sessions.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = { vm.show(Screen.Setup) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Text("No sessions yet.", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            val missed = missedFacts(sessions).take(8)
            if (missed.isNotEmpty()) item {
                Column(Modifier.padding(16.dp)) {
                    Text("Most missed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        missed.joinToString("   ") { "${it.a} × ${it.b} (${it.missed}/${it.asked})" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                HorizontalDivider()
            }
            items(sessions, key = { it.id }) { s ->
                SessionRow(s) { vm.show(Screen.Detail(s.id)) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SessionRow(s: SessionRecord, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(dayFormat.format(Date(s.startedAt)), style = MaterialTheme.typography.titleMedium)
            Text(
                "${rangeText(s.settings)} · ${formatClock(s.activeMillis)} · ${reasonText(s.reason)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("✓ ${s.right}   ✗ ${s.wrong + s.timeouts}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            if (s.settings.maxScore != null) "${s.score}/${s.settings.maxScore}" else "${s.score}",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(vm: DrillViewModel, id: String) {
    val sessions by vm.sessions.collectAsState()
    val s = sessions.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s?.let { dayFormat.format(Date(it.startedAt)) } ?: "Session") },
                navigationIcon = {
                    IconButton(onClick = { vm.show(Screen.History) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        if (s == null) {
            Text("This session is gone.", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Score ${s.score}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("${reasonText(s.reason)} after ${formatClock(s.activeMillis)}")
                    Text(rangeText(s.settings) + limitsText(s.settings).let { if (it.isEmpty()) "" else " · $it" })
                    Text(
                        "✓ ${s.right} right   ✗ ${s.wrong} wrong   ⏱ ${s.timeouts} out of time",
                    )
                    s.averageRightMillis?.let { Text("${formatSeconds(it)} for a right answer, on average") }
                }
                HorizontalDivider()
            }
            items(s.attempts.size) { i ->
                val a = s.attempts[i]
                val q = a.question
                val (mark, color) = when (a.outcome) {
                    Outcome.RIGHT -> "✓" to RightGreen
                    Outcome.WRONG -> "✗" to WrongRed
                    Outcome.TIMEOUT -> "⏱" to TimeoutAmber
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(mark, color = color, modifier = Modifier.padding(end = 12.dp))
                    Text("$q = ${q.answer}", Modifier.weight(1f))
                    if (a.outcome == Outcome.WRONG) Text("said ${a.given}", color = WrongRed, modifier = Modifier.padding(end = 12.dp))
                    Text(formatSeconds(a.millis), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (confirmDelete && s != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(s.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
