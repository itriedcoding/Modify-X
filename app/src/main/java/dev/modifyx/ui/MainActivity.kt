package dev.modifyx.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.modifyx.ui.components.BusyDialog
import dev.modifyx.ui.screens.AppDetailScreen
import dev.modifyx.ui.screens.AppsScreen
import dev.modifyx.ui.screens.BackupScreen
import dev.modifyx.ui.screens.ComponentsScreen
import dev.modifyx.ui.screens.ConsoleScreen
import dev.modifyx.ui.screens.HistoryScreen
import dev.modifyx.ui.screens.HomeScreen
import dev.modifyx.ui.screens.ProfilesScreen
import dev.modifyx.ui.screens.SettingsScreen
import dev.modifyx.ui.theme.ModifyXTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

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
                AppRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Usage access can be granted in system settings while the app is backgrounded.
        viewModel.refreshUsageAccess()
    }
}

object Routes {
    const val HOME = "home"
    const val APPS = "apps"
    const val PROFILES = "profiles"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val CONSOLE = "console"
    const val BACKUP = "backup"
    const val DETAIL = "detail/{package}"
    const val COMPONENTS = "components/{package}"

    fun detail(pkg: String): String = "detail/${Uri.encode(pkg)}"

    fun components(pkg: String): String = "components/${Uri.encode(pkg)}"
}

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Home", Icons.Default.Home),
    BottomDestination(Routes.APPS, "Apps", Icons.Default.List),
    BottomDestination(Routes.PROFILES, "Presets", Icons.Default.Star),
    BottomDestination(Routes.HISTORY, "History", Icons.Default.DateRange),
    BottomDestination(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
private fun AppRoot(vm: MainViewModel) {
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
                    onOpenApps = { navigateTo(navController, Routes.APPS) },
                    onOpenProfiles = { navigateTo(navController, Routes.PROFILES) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onOpenHistory = { navigateTo(navController, Routes.HISTORY) },
                )
            }
            composable(Routes.APPS) {
                AppsScreen(vm = vm, onOpenDetail = { pkg -> navController.navigate(Routes.detail(pkg)) })
            }
            composable(Routes.PROFILES) { ProfilesScreen(vm) }
            composable(Routes.HISTORY) { HistoryScreen(vm) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    vm = vm,
                    onOpenConsole = { navController.navigate(Routes.CONSOLE) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                )
            }
            composable(Routes.CONSOLE) {
                ConsoleScreen(vm = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.BACKUP) {
                BackupScreen(vm = vm, onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("package") { type = NavType.StringType }),
            ) { entry ->
                val pkg = Uri.decode(entry.arguments?.getString("package").orEmpty())
                AppDetailScreen(
                    vm = vm,
                    packageName = pkg,
                    onBack = { navController.popBackStack() },
                    onOpenComponents = { navController.navigate(Routes.components(pkg)) },
                )
            }
            composable(
                route = Routes.COMPONENTS,
                arguments = listOf(navArgument("package") { type = NavType.StringType }),
            ) { entry ->
                val pkg = Uri.decode(entry.arguments?.getString("package").orEmpty())
                ComponentsScreen(
                    vm = vm,
                    packageName = pkg,
                    onBack = { navController.popBackStack() },
                )
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
