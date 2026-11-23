package id.whitesecurity.antivirus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.whitesecurity.antivirus.R
import id.whitesecurity.antivirus.data.local.ScanHistoryEntity
import id.whitesecurity.antivirus.ui.MainViewModel
import id.whitesecurity.antivirus.ui.theme.SafeGreen
import id.whitesecurity.antivirus.ui.theme.ThreatRed
import java.text.DateFormat
import java.util.Date

@Composable
fun ScanHistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val history by viewModel.scanHistory.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (history.isEmpty()) {
                Text(
                    stringResource(R.string.scan_history_empty),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            } else {
                LazyColumn {
                    items(history, key = { it.id }) { entry -> HistoryRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: ScanHistoryEntity) {
    val formatted = remember(entry.scannedAtEpochMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(entry.scannedAtEpochMs))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(formatted, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.scan_history_apps_scanned, entry.totalScanned),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Text(
                text = if (entry.threatCount > 0) {
                    stringResource(R.string.threats_found, entry.threatCount)
                } else {
                    stringResource(R.string.no_threats_found)
                },
                color = if (entry.threatCount > 0) ThreatRed else SafeGreen,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
