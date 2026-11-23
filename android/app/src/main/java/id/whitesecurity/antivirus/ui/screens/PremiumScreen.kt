package id.whitesecurity.antivirus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.whitesecurity.antivirus.R
import id.whitesecurity.antivirus.ui.LicenseUiState
import id.whitesecurity.antivirus.ui.MainViewModel
import id.whitesecurity.antivirus.ui.theme.SafeGreen
import id.whitesecurity.antivirus.ui.theme.ThreatRed

@Composable
fun PremiumScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val isPremium by viewModel.isPremium.collectAsState()
    val licenseState by viewModel.licenseState.collectAsState()
    var licenseKey by remember { mutableStateOf("") }

    LaunchedEffect(licenseState) {
        if (licenseState is LicenseUiState.Success) {
            // keep the success message visible briefly; nothing else to do —
            // isPremium flips true already via the StateFlow it's backed by.
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.premium_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.premium_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            SectionTitle(stringResource(R.string.free_section_title))
            FeatureRow(stringResource(R.string.free_feature_scan), unlocked = true)
            FeatureRow(stringResource(R.string.free_feature_stalkerware), unlocked = true)
            FeatureRow(stringResource(R.string.free_feature_realtime), unlocked = true)
            FeatureRow(stringResource(R.string.free_feature_cloud), unlocked = true)
            FeatureRow(stringResource(R.string.free_feature_history), unlocked = true)

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.premium_section_title))
                Spacer(Modifier.width(8.dp))
                PremiumBadge()
            }
            FeatureRow(stringResource(R.string.premium_feature_scamshield), isPremium)
            FeatureRow(stringResource(R.string.premium_feature_checkup), isPremium)
            FeatureRow(stringResource(R.string.premium_feature_deepscan), isPremium)

            Spacer(Modifier.height(24.dp))

            if (!isPremium) {
                OutlinedTextField(
                    value = licenseKey,
                    onValueChange = { licenseKey = it },
                    label = { Text(stringResource(R.string.license_key_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.activateLicense(licenseKey) },
                    enabled = licenseKey.isNotBlank() && licenseState !is LicenseUiState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (licenseState is LicenseUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    } else {
                        Text(stringResource(R.string.activate))
                    }
                }

                when (licenseState) {
                    is LicenseUiState.Error -> Text(
                        stringResource(R.string.license_invalid),
                        color = ThreatRed,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    is LicenseUiState.Success -> Text(
                        stringResource(R.string.license_valid),
                        color = SafeGreen,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    else -> Unit
                }
                Text(
                    stringResource(R.string.premium_buy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                Text(stringResource(R.string.license_valid), color = SafeGreen, fontWeight = FontWeight.SemiBold)
                viewModel.licenseKey?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
                val expires = id.whitesecurity.antivirus.util.ServerDates.formatDate(viewModel.licenseExpiresAt)
                Text(
                    if (expires != null) stringResource(R.string.license_expires, expires)
                    else stringResource(R.string.license_lifetime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun FeatureRow(text: String, unlocked: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (unlocked) SafeGreen else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
