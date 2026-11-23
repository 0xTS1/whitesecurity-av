package id.whitesecurity.antivirus.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.whitesecurity.antivirus.BuildConfig
import id.whitesecurity.antivirus.R
import id.whitesecurity.antivirus.scanner.FindingSource
import id.whitesecurity.antivirus.scanner.ScanFinding
import id.whitesecurity.antivirus.scanner.SpywareDetector
import id.whitesecurity.antivirus.scanner.ThreatCategory
import id.whitesecurity.antivirus.ui.MainViewModel
import id.whitesecurity.antivirus.ui.ScanUiState
import id.whitesecurity.antivirus.ui.theme.BrandBlack
import id.whitesecurity.antivirus.ui.theme.SafeGreen
import id.whitesecurity.antivirus.ui.theme.ThreatRed
import id.whitesecurity.antivirus.ui.theme.WarnAmber

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onOpenPremium: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenScamShield: () -> Unit,
    onOpenCheckup: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val scanState by viewModel.scanState.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val deepScan by viewModel.deepScan.collectAsState()
    val fileScanResult by viewModel.fileScanResult.collectAsState()
    val update by viewModel.updateAvailable.collectAsState()
    val updateBusy by viewModel.updateBusy.collectAsState()
    val updateMessage by viewModel.updateMessage.collectAsState()
    val needsInstallPermission by viewModel.needsInstallPermission.collectAsState()
    val accountUser by viewModel.accountUser.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(needsInstallPermission) {
        if (needsInstallPermission) {
            viewModel.onInstallPermissionHandled()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.scanPickedFile(it) }
    }

    Scaffold(bottomBar = { PoweredByFooter() }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                BrandHeader(
                    isPremium = isPremium,
                    accountInitial = accountUser?.let { (it.name?.firstOrNull() ?: it.email.firstOrNull() ?: '?').uppercaseChar() },
                    onOpenHistory = onOpenHistory,
                    onOpenPremium = onOpenPremium,
                    onOpenAccount = onOpenAccount,
                )
            }

            update?.let { info ->
                item {
                    UpdateBanner(
                        versionName = info.versionName,
                        sizeMb = info.sizeBytes / 1_048_576f,
                        busy = updateBusy,
                        message = updateMessage,
                        onInstall = { viewModel.installUpdate() },
                    )
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(16.dp))
                    ProtectionCard()
                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.scanInstalledApps() },
                            enabled = scanState !is ScanUiState.Scanning,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.scan_installed_apps))
                        }
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("application/vnd.android.package-archive")) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.scan_file))
                        }
                    }

                    DeepScanRow(
                        isPremium = isPremium,
                        checked = deepScan,
                        onCheckedChange = { viewModel.setDeepScan(it) },
                        onLockedClick = onOpenPremium,
                    )
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(R.string.premium_tools_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                    ToolCard(
                        icon = Icons.Filled.PhonelinkLock,
                        title = stringResource(R.string.scamshield_title),
                        description = stringResource(R.string.scamshield_desc),
                        locked = !isPremium,
                        onClick = if (isPremium) onOpenScamShield else onOpenPremium,
                    )
                    Spacer(Modifier.height(8.dp))
                    ToolCard(
                        icon = Icons.Filled.GppGood,
                        title = stringResource(R.string.checkup_title),
                        description = stringResource(R.string.checkup_desc),
                        locked = !isPremium,
                        onClick = if (isPremium) onOpenCheckup else onOpenPremium,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            when (val state = scanState) {
                is ScanUiState.Scanning -> item { ScanningIndicator(state) }
                is ScanUiState.Done -> {
                    val findings = state.summary.findings
                    val threatCount = findings.count { it.malicious || it.category == ThreatCategory.SPYWARE }
                    item {
                        Text(
                            text = if (threatCount > 0) {
                                stringResource(R.string.threats_found, threatCount)
                            } else {
                                stringResource(R.string.no_threats_found)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (threatCount > 0) ThreatRed else SafeGreen,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Text(
                            stringResource(R.string.scan_history_apps_scanned, state.summary.totalScanned),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        CategorySummary(findings)
                    }
                    val interesting = findings
                        .filter { it.category != ThreatCategory.CLEAN }
                        .sortedBy { it.category.ordinal }
                    items(interesting) { finding -> FindingRow(finding, onOpenInfo = { openAppInfo(context, it) }) }
                }
                is ScanUiState.Failed -> item {
                    Text(
                        "Scan gagal: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                ScanUiState.Idle -> item {
                    Text(
                        stringResource(R.string.last_scan_none),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            fileScanResult?.let { finding ->
                item {
                    Text(
                        "Hasil scan file:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                    FindingRow(finding, onOpenInfo = null)
                }
            }
        }
    }
}

/** Dark brand band with the full WhiteSecurity logo — dark in both themes so the silver logo stays legible. */
@Composable
private fun BrandHeader(
    isPremium: Boolean,
    accountInitial: Char?,
    onOpenHistory: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandBlack)
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp),
        ) {
            IconButton(onClick = onOpenHistory) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = stringResource(R.string.scan_history_title),
                    tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                )
            }
            IconButton(onClick = onOpenPremium) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    contentDescription = "Premium",
                    tint = if (isPremium) WarnAmber else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                )
            }
            IconButton(onClick = onOpenAccount) {
                if (accountInitial != null) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$accountInitial", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = stringResource(R.string.account_signin_cta),
                        tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ws_logo_full),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.height(132.dp),
            )
            if (isPremium) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.premium_active_badge),
                    color = WarnAmber,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun UpdateBanner(
    versionName: String,
    sizeMb: Float,
    busy: Boolean,
    message: String?,
    onInstall: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(
                Modifier
                    .padding(horizontal = 12.dp)
                    .weight(1f),
            ) {
                Text(
                    stringResource(R.string.update_available_title, versionName),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    message ?: stringResource(R.string.update_available_text, "%.1f".format(sizeMb)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message != null) ThreatRed else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(onClick = onInstall) { Text(stringResource(R.string.update_install)) }
            }
        }
    }
}

@Composable
private fun ProtectionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SafeGreen.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    stringResource(R.string.monitoring_on),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.monitoring_on_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun DeepScanRow(
    isPremium: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onLockedClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(enabled = !isPremium, onClick = onLockedClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (isPremium) R.string.deep_scan_label else R.string.deep_scan_locked),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (isPremium) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        } else {
            Icon(
                Icons.Filled.Lock,
                contentDescription = stringResource(R.string.locked_feature),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: String,
    description: String,
    locked: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (locked) {
                        Spacer(Modifier.width(8.dp))
                        PremiumBadge()
                    }
                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Icon(
                if (locked) Icons.Filled.Lock else Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
fun PremiumBadge() {
    Text(
        stringResource(R.string.premium_badge),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = BrandBlack,
        modifier = Modifier
            .background(WarnAmber, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ScanningIndicator(state: ScanUiState.Scanning) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(stringResource(R.string.scanning_in_progress))
                if (state.total > 0) {
                    Text(
                        "${state.current}/${state.total} — ${state.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        }
        if (state.total > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.current.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun openAppInfo(context: android.content.Context, packageName: String) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun categoryColor(category: ThreatCategory) = when (category) {
    ThreatCategory.MALWARE, ThreatCategory.STALKERWARE -> ThreatRed
    ThreatCategory.SPYWARE -> ThreatRed
    ThreatCategory.RISKY -> WarnAmber
    ThreatCategory.CLEAN -> SafeGreen
}

@Composable
private fun CategorySummary(findings: List<ScanFinding>) {
    val counts = listOf(ThreatCategory.MALWARE, ThreatCategory.STALKERWARE, ThreatCategory.SPYWARE, ThreatCategory.RISKY)
        .map { cat -> cat to findings.count { it.category == cat } }
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        counts.forEach { (cat, n) ->
            val color = if (n > 0) categoryColor(cat) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .background(color.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
            ) {
                Text("$n", fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.titleMedium)
                Text(cat.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FindingRow(finding: ScanFinding, onOpenInfo: ((String) -> Unit)?) {
    val color = categoryColor(finding.category)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(finding.app.appLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(finding.app.packageName, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    finding.category.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .background(color, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (finding.malicious && finding.threatName != null) {
                Text(
                    "Terdeteksi: ${finding.threatName}",
                    color = ThreatRed,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            finding.heuristicReasons.forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            }
            Text(
                adviceFor(finding.category),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 6.dp),
            )
            if (onOpenInfo != null) {
                androidx.compose.material3.TextButton(
                    onClick = { onOpenInfo(finding.app.packageName) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.open_app_info)) }
            }
        }
    }
}

@Composable
private fun adviceFor(category: ThreatCategory): String = stringResource(
    when (category) {
        ThreatCategory.MALWARE -> R.string.advice_malware
        ThreatCategory.STALKERWARE -> R.string.advice_stalkerware
        ThreatCategory.SPYWARE -> R.string.advice_spyware
        ThreatCategory.RISKY, ThreatCategory.CLEAN -> R.string.advice_risky
    }
)

@Composable
private fun PoweredByFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.powered_by),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
        Text(
            stringResource(R.string.contact_support),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
        Text(
            SpywareDetector.ATTRIBUTION,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
        Text(
            "v" + BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
    }
}
