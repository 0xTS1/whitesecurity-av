package id.whitesecurity.antivirus.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import id.whitesecurity.antivirus.BuildConfig
import id.whitesecurity.antivirus.R
import id.whitesecurity.antivirus.data.remote.dto.AccountLicenseDto
import id.whitesecurity.antivirus.data.remote.dto.AccountOrderDto
import id.whitesecurity.antivirus.data.remote.dto.MeResponse
import id.whitesecurity.antivirus.ui.MainViewModel
import id.whitesecurity.antivirus.ui.ToolState
import id.whitesecurity.antivirus.ui.theme.BrandBlack
import id.whitesecurity.antivirus.ui.theme.SafeGreen
import id.whitesecurity.antivirus.ui.theme.ThreatRed
import id.whitesecurity.antivirus.ui.theme.WarnAmber
import id.whitesecurity.antivirus.util.ServerDates
import java.text.NumberFormat
import java.util.Locale

/** Entry point for the "account" route: login/register when signed out, dashboard when signed in. */
@Composable
fun AccountScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val user by viewModel.accountUser.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (user == null) R.string.account_login_title else R.string.account_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) } },
                actions = {
                    if (user != null) {
                        IconButton(onClick = { viewModel.loadAccount() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.account_refresh))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (user == null) AuthForm(viewModel) else AccountDashboard(viewModel)
        }
    }
}

private fun openWeb(context: android.content.Context, path: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.UPDATE_BASE_URL.trimEnd('/') + path))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

// --- Masuk / Daftar ---

@Composable
private fun AuthForm(viewModel: MainViewModel) {
    val state by viewModel.authState.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 = masuk, 1 = daftar
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val running = state is ToolState.Running
    val registering = tab == 1
    val canSubmit = email.contains('@') && password.length >= (if (registering) 8 else 1) && !running

    LaunchedEffect(tab) { viewModel.resetAuthState() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.account_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 16.dp),
        )
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.account_tab_login)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.account_tab_register)) })
        }
        Spacer(Modifier.height(16.dp))

        if (registering) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.account_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text(stringResource(R.string.account_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(stringResource(R.string.account_password)) },
            supportingText = { if (registering) Text(stringResource(R.string.account_password_hint)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        (state as? ToolState.Failed)?.let {
            Text(it.message, color = ThreatRed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = { if (registering) viewModel.register(name, email, password) else viewModel.login(email, password) },
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            if (running) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(if (registering) R.string.account_tab_register else R.string.account_tab_login))
        }

        if (!registering) {
            TextButton(onClick = { openWeb(context, "/antivirus/login") }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.account_forgot))
            }
        }
    }
}

// --- Dashboard akun ---

@Composable
private fun AccountDashboard(viewModel: MainViewModel) {
    val user by viewModel.accountUser.collectAsState()
    val me by viewModel.me.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val activating by viewModel.activatingKey.collectAsState()
    val message by viewModel.accountMessage.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadAccount() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ProfileCard(user?.name, user?.email ?: "", isPremium) }

        message?.let { msg ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        TextButton(onClick = { viewModel.clearAccountMessage() }) { Text("OK") }
                    }
                }
            }
        }

        when (val s = me) {
            ToolState.Idle, ToolState.Running -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is ToolState.Failed -> item {
                Text(s.message, color = ThreatRed)
                OutlinedButton(onClick = { viewModel.loadAccount() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.account_retry))
                }
            }
            is ToolState.Done -> accountContent(
                data = s.result,
                activatingKey = activating,
                onActivate = { viewModel.activateFromAccount(it) },
                onBuy = { openWeb(context, "/antivirus/products") },
            )
        }

        item {
            OutlinedButton(
                onClick = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.account_logout))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.accountContent(
    data: MeResponse,
    activatingKey: String?,
    onActivate: (String) -> Unit,
    onBuy: () -> Unit,
) {
    val licenses = data.licenses.orEmpty()
    val orders = data.orders.orEmpty()
    val active = licenses.count { it.state == "active" }

    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("$active", "Lisensi aktif", Modifier.weight(1f))
            StatTile("${licenses.sumOf { it.devices.orEmpty().size }}", "Perangkat", Modifier.weight(1f))
            StatTile("${orders.count { it.status == "paid" }}", "Pembelian", Modifier.weight(1f))
        }
    }

    item { SectionHeader("Lisensi Saya") }
    if (licenses.isEmpty()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Belum ada lisensi Premium di akun ini.")
                    Button(onClick = onBuy, modifier = Modifier.padding(top = 8.dp)) { Text("Lihat paket Premium") }
                }
            }
        }
    } else {
        items(licenses, key = { it.licenseKey }) { l ->
            LicenseCard(l, activating = activatingKey == l.licenseKey, onActivate = { onActivate(l.licenseKey) })
        }
    }

    item { SectionHeader("Riwayat Pesanan") }
    if (orders.isEmpty()) {
        item { Text("Belum ada pesanan.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
    } else {
        items(orders, key = { it.orderId }) { OrderRow(it) }
    }
}

@Composable
private fun ProfileCard(name: String?, email: String, isPremium: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = BrandBlack)) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val initial = (name?.firstOrNull() ?: email.firstOrNull() ?: '?').uppercaseChar()
            Box(
                Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$initial", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(name ?: email.substringBefore('@'), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(email, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
            if (isPremium) PremiumBadge()
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun LicenseCard(l: AccountLicenseDto, activating: Boolean, onActivate: () -> Unit) {
    val (label, color) = when (l.state) {
        "active" -> "Aktif" to SafeGreen
        "expired" -> "Kedaluwarsa" to WarnAmber
        else -> "Dicabut" to ThreatRed
    }
    val devices = l.devices.orEmpty()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.licenseKey, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    label, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                "Paket ${l.plan} · ${devices.size}/${l.maxDevices} perangkat · " +
                    (ServerDates.formatDate(l.expiresAt)?.let { "berlaku hingga $it" } ?: "seumur hidup"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp),
            )
            devices.forEach { d ->
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = if (d.thisDevice) SafeGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text(
                        (if (d.thisDevice) "HP ini" else d.device) + " · terakhir aktif " + ServerDates.ago(d.lastSeenAt),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (l.state == "active" && !l.activeOnThisDevice) {
                val full = devices.size >= l.maxDevices
                Button(
                    onClick = onActivate,
                    enabled = !full && !activating,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    if (activating) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (full) "Batas perangkat penuh" else "Aktifkan Premium di HP ini")
                }
            } else if (l.activeOnThisDevice) {
                Text("✓ Aktif di HP ini", color = SafeGreen, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun OrderRow(o: AccountOrderDto) {
    val rupiah = NumberFormat.getNumberInstance(Locale("id", "ID")).format(o.amount)
    val (label, color) = when (o.status) {
        "paid" -> "Lunas" to SafeGreen
        "pending", "processing" -> "Menunggu" to WarnAmber
        else -> o.status to ThreatRed
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.product, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Rp$rupiah · ${ServerDates.formatDateTime(o.createdAt) ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}
