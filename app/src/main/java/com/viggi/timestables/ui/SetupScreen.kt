package com.viggi.timestables.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.viggi.timestables.domain.QuestionDeck

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(vm: DrillViewModel) {
    val s by vm.settings.collectAsState()
    val soundOn by vm.soundOn.collectAsState()
    val numbers = (0..20).toList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Times Tables") },
                actions = {
                    IconButton(onClick = { vm.show(Screen.History) }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "History")
                    }
                },
            )
        },
        // Start stays in reach without scrolling past the settings.
        bottomBar = {
            Button(
                onClick = vm::start,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(72.dp),
            ) {
                Text("Start", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Questions")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Picker("Tables from", s.tablesFrom, numbers, Modifier.weight(1f)) { vm.updateSettings(s.copy(tablesFrom = it)) }
                Picker("to", s.tablesTo, numbers, Modifier.weight(1f)) { vm.updateSettings(s.copy(tablesTo = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Picker("Times from", s.timesFrom, numbers, Modifier.weight(1f)) { vm.updateSettings(s.copy(timesFrom = it)) }
                Picker("to", s.timesTo, numbers, Modifier.weight(1f)) { vm.updateSettings(s.copy(timesTo = it)) }
            }
            Text(
                "${rangeText(s.normalized())}: ${QuestionDeck.all(s).size} questions, each asked once before any repeats",
                style = MaterialTheme.typography.bodySmall,
            )

            Section("When it ends")
            OptionalPicker("Top score", s.maxScore, listOf(5, 10, 15, 20, 25, 30, 40, 50), { "$it" }) {
                vm.updateSettings(s.copy(maxScore = it))
            }
            OptionalPicker("Session length", s.sessionMinutes, listOf(5, 10, 15, 20, 25, 30, 45, 60), { "$it min" }) {
                vm.updateSettings(s.copy(sessionMinutes = it))
            }
            OptionalPicker("Stop when idle for", s.idleStopMinutes, listOf(1, 2, 3, 5, 10), { "$it min" }) {
                vm.updateSettings(s.copy(idleStopMinutes = it))
            }

            Section("Answers")
            OptionalPicker("Time for each answer", s.answerSeconds, listOf(3, 5, 8, 10, 15, 20, 30), { "$it s" }) {
                vm.updateSettings(s.copy(answerSeconds = it))
            }
            OptionalPicker("Wait after a wrong answer", s.wrongWaitSeconds.takeIf { it > 0 }, listOf(1, 2, 3, 5, 10), { "$it s" }) {
                vm.updateSettings(s.copy(wrongWaitSeconds = it ?: 0))
            }
            OptionalPicker("Points off for a wrong answer", s.wrongPenalty.takeIf { it > 0 }, listOf(1, 2, 3, 5), { "$it" }) {
                vm.updateSettings(s.copy(wrongPenalty = it ?: 0))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sounds", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = soundOn, onCheckedChange = vm::setSoundOn)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Picker(label: String, value: Int, options: List<Int>, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    Dropdown(label, value, options, { "$it" }, modifier, onPick)
}

/** A dropdown whose first choice is "Off". */
@Composable
private fun OptionalPicker(label: String, value: Int?, options: List<Int>, show: (Int) -> String, onPick: (Int?) -> Unit) {
    val choices: List<Int?> = listOf(null) + (options + listOfNotNull(value)).distinct().sorted()
    Dropdown(label, value, choices, { it?.let(show) ?: "Off" }, Modifier.fillMaxWidth(), onPick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(
    label: String,
    value: T,
    options: List<T>,
    show: (T) -> String,
    modifier: Modifier,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = show(value),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(show(option)) },
                    onClick = {
                        onPick(option)
                        open = false
                    },
                )
            }
        }
    }
}
