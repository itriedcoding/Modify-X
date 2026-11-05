package dev.modifyx.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.modifyx.ui.components.BusyDialog
import dev.modifyx.ui.screens.DuplicatesScreen
import dev.modifyx.ui.screens.ExploreScreen
import dev.modifyx.ui.screens.HistoryScreen
import dev.modifyx.ui.screens.HomeScreen
import dev.modifyx.ui.screens.JunkScreen
import dev.modifyx.ui.screens.OnboardingScreen
import dev.modifyx.ui.screens.SettingsScreen
import dev.modifyx.ui.theme.ModifyXTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        // Returning to the app is a real trigger: auto-scan runs here when the result is stale.
        viewModel.autoScanIfDue()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            ModifyXTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                themeId = settings.themeId,
                customThemes = settings.customThemes,
                amoled = settings.amoled,
            ) {
                Surface(Modifier, color = MaterialTheme.colorScheme.background) {
                    Root(viewModel)
                }
            }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val JUNK = "junk"
    const val DUPLICATES = "duplicates"
    const val EXPLORE = "explore"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Home", Icons.Default.Home),
    BottomDestination(Routes.JUNK, "Junk", Icons.Default.Delete),
    BottomDestination(Routes.DUPLICATES, "Duplicates", Icons.Default.List),
    BottomDestination(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
private fun Root(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::addRoot)
    }

    LaunchedEffect(settings.onboardingDone, vm.roots.size) {
        vm.autoScanIfDue()
    }

    if (!settings.onboardingDone) {
        OnboardingScreen(
            roots = vm.roots,
            onAddFolder = { runCatching { picker.launch(null) } },
            onRemoveRoot = vm::removeRoot,
            onFinish = { vm.finishOnboarding(scanNow = vm.roots.isNotEmpty()) },
        )
        return
    }

    AppScaffold(vm, onAddFolder = { runCatching { picker.launch(null) } })
}

@Composable
private fun AppScaffold(vm: MainViewModel, onAddFolder: () -> Unit) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = vm.notice

    LaunchedEffect(notice) {
        if (notice != null) {
            snackbarHostState.showSnackbar(notice)
            vm.consumeNotice()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (bottomDestinations.any { it.route == route }) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = { navigateTo(navController, destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm = vm,
                    onOpenJunk = { navigateTo(navController, Routes.JUNK) },
                    onOpenDuplicates = { navigateTo(navController, Routes.DUPLICATES) },
                    onOpenExplore = { navController.navigate(Routes.EXPLORE) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenSettings = { navigateTo(navController, Routes.SETTINGS) },
                    onAddFolder = onAddFolder,
                )
            }
            composable(Routes.JUNK) { JunkScreen(vm, onAddFolder = onAddFolder) }
            composable(Routes.DUPLICATES) { DuplicatesScreen(vm, onAddFolder = onAddFolder) }
            composable(Routes.EXPLORE) {
                ExploreScreen(vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(vm, onAddFolder = onAddFolder)
            }
        }
    }

    vm.busy?.let { BusyDialog(it.title, it.done, it.total, it.current) }
}

private fun navigateTo(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
