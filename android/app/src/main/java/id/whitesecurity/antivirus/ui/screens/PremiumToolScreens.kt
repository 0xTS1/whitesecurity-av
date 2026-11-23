package id.whitesecurity.antivirus.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.whitesecurity.antivirus.R
import id.whitesecurity.antivirus.premium.CheckItem
import id.whitesecurity.antivirus.premium.CheckupResult
import id.whitesecurity.antivirus.premium.ScamRiskApp
import id.whitesecurity.antivirus.premium.ScamRiskLevel
import id.whitesecurity.antivirus.ui.MainViewModel
import id.whitesecurity.antivirus.ui.ToolState
import id.whitesecurity.antivirus.ui.theme.SafeGreen
import id.whitesecurity.antivirus.ui.theme.ThreatRed
import id.whitesecurity.antivirus.ui.theme.WarnAmber

@Composable
private fun ToolScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
        content = content,
    )
}

@Composable
private fun Running() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// --- Perisai Anti-Penipuan ---

@Composable
fun ScamShieldScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.scamShield.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { if (state is ToolState.Idle) viewModel.runScamShield() }

    ToolScaffold(stringResource(R.string.scamshield_title), onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.scamshield_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                OutlinedButton(
                    onClick = { viewModel.runScamShield() },
                    enabled = state !is ToolState.Running,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.scamshield_run)) }
            }
            when (val s = state) {
                ToolState.Idle, ToolState.Running -> item { Running() }
                is ToolState.Failed -> item { Text("Gagal: ${s.message}", color = ThreatRed) }
                is ToolState.Done -> {
                    if (s.result.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = SafeGreen.copy(alpha = 0.12f))) {
                                Text(stringResource(R.string.scamshield_clean), modifier = Modifier.padding(16.dp))
                            }
                        }
                    } else {
                        items(s.result, key = { it.packageName }) { app ->
                            ScamRiskRow(app) {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                        item {
                            Text(
                                stringResource(R.string.scamshield_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScamRiskRow(app: ScamRiskApp, onOpenInfo: () -> Unit) {
    val high = app.level == ScamRiskLevel.HIGH
    val accent = if (high) ThreatRed else WarnAmber
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(if (high) R.string.scamshield_high else R.string.scamshield_review),
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (app.sideloaded) {
                Text(
                    "⚠ " + stringResource(R.string.scamshield_sideloaded),
                    color = ThreatRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            app.signals.forEach { signal ->
                Text("• $signal", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            TextButton(onClick = onOpenInfo, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.open_app_info))
            }
        }
    }
}

// --- Pemeriksaan Keamanan Perangkat ---

@Composable
fun SecurityCheckupScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.checkup.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { if (state is ToolState.Idle) viewModel.runCheckup() }

    ToolScaffold(stringResource(R.string.checkup_title), onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (val s = state) {
                ToolState.Idle, ToolState.Running -> item { Running() }
                is ToolState.Failed -> item { Text("Gagal: ${s.message}", color = ThreatRed) }
                is ToolState.Done -> {
                    item { ScoreCard(s.result) }
                    items(s.result.items, key = { it.title }) { check ->
                        CheckRow(check) { action ->
                            runCatching {
                                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.onFailure {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    }
                    item {
                        Button(onClick = { viewModel.runCheckup() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(stringResource(R.string.checkup_rerun))
                        }
                    }
                }
            }
        }
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> SafeGreen
    score >= 50 -> WarnAmber
    else -> ThreatRed
}

@Composable
private fun ScoreCard(result: CheckupResult) {
    val color = scoreColor(result.score)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { result.score / 100f },
                    modifier = Modifier.size(84.dp),
                    color = color,
                    strokeWidth = 8.dp,
                    trackColor = color.copy(alpha = 0.2f),
                )
                Text("${result.score}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Column(Modifier.padding(start = 20.dp)) {
                Text(stringResource(R.string.checkup_score_label), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (result.issues == 0) stringResource(R.string.checkup_all_good)
                    else stringResource(R.string.checkup_issues, result.issues),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CheckRow(item: CheckItem, onFix: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (item.passed) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                contentDescription = null,
                tint = if (item.passed) SafeGreen else ThreatRed,
                modifier = Modifier.size(28.dp),
            )
            Column(
                Modifier
                    .padding(horizontal = 12.dp)
                    .weight(1f),
            ) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            val action = item.fixAction
            if (!item.passed && action != null) {
                TextButton(onClick = { onFix(action) }) { Text(stringResource(R.string.fix)) }
            }
        }
    }
}
