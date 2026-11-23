package id.whitesecurity.antivirus

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import id.whitesecurity.antivirus.ui.MainViewModel
import id.whitesecurity.antivirus.ui.screens.AccountScreen
import id.whitesecurity.antivirus.ui.screens.DashboardScreen
import id.whitesecurity.antivirus.ui.screens.PremiumScreen
import id.whitesecurity.antivirus.ui.screens.ScamShieldScreen
import id.whitesecurity.antivirus.ui.screens.ScanHistoryScreen
import id.whitesecurity.antivirus.ui.screens.SecurityCheckupScreen
import id.whitesecurity.antivirus.ui.theme.WhiteSecurityTheme
import id.whitesecurity.antivirus.update.UpdateState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            WhiteSecurityTheme {
                WhiteSecurityNavHost(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        UpdateState.appInForeground = true
        viewModel.checkForUpdate()
    }

    override fun onPause() {
        UpdateState.appInForeground = false
        super.onPause()
    }
}

@Composable
private fun WhiteSecurityNavHost(viewModel: MainViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                onOpenPremium = { navController.navigate("premium") },
                onOpenHistory = { navController.navigate("history") },
                onOpenScamShield = { navController.navigate("scamshield") },
                onOpenCheckup = { navController.navigate("checkup") },
                onOpenAccount = { navController.navigate("account") },
            )
        }
        composable("account") {
            AccountScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("scamshield") {
            ScamShieldScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("checkup") {
            SecurityCheckupScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("premium") {
            PremiumScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable("history") {
            ScanHistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
