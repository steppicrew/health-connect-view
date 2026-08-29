package de.steppicrew.healthconnectview.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import de.steppicrew.healthconnectview.ui.catalog.CatalogScreen
import de.steppicrew.healthconnectview.ui.catalog.CatalogViewModel
import de.steppicrew.healthconnectview.ui.dashboard.DashboardScreen
import de.steppicrew.healthconnectview.ui.dashboard.DashboardViewModel
import de.steppicrew.healthconnectview.ui.dashboard.TileDetailScreen
import de.steppicrew.healthconnectview.ui.dashboard.TileDetailViewModel
import de.steppicrew.healthconnectview.ui.detail.TypeDetailScreen
import de.steppicrew.healthconnectview.ui.detail.TypeDetailViewModel
import de.steppicrew.healthconnectview.ui.permissions.PermissionsScreen
import de.steppicrew.healthconnectview.ui.permissions.PermissionsViewModel
import de.steppicrew.healthconnectview.ui.privacy.PrivacyScreen
import de.steppicrew.healthconnectview.ui.settings.SettingsScreen
import de.steppicrew.healthconnectview.ui.settings.SettingsViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val CATALOG = "catalog"
    const val PERMISSIONS = "permissions"
    const val TYPE_DETAIL = "type/{typeName}"
    const val TILE_DETAIL = "tile/{typeName}?date={date}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    fun typeDetail(typeName: String) = "type/$typeName"
    fun tileDetail(typeName: String, date: String) = "tile/$typeName?date=$date"
}

@Composable
fun HealthNavGraph(
    onRequestPermissions: (Set<String>) -> Unit,
    navController: NavHostController = rememberNavController(),
    /**
     * Screen to open directly, from the debug launch intent. Null in release builds, where
     * DebugNav has no implementation that can produce one.
     */
    startRoute: String? = null,
) {
    // Navigated to rather than used as the start destination, so Back still reaches the
    // dashboard: landing with an empty back stack would trap the screen with no way out, and
    // the point of the backdoor is to inspect the app, not to replace it.
    LaunchedEffect(startRoute) {
        startRoute?.let(navController::navigate)
    }

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {

        composable(Routes.DASHBOARD) {
            val viewModel: DashboardViewModel = viewModel()
            DashboardScreen(
                viewModel = viewModel,
                onOpenType = { type, date ->
                    navController.navigate(Routes.tileDetail(type, date))
                },
                onOpenCatalog = { navController.navigate(Routes.CATALOG) },
                onOpenPermissions = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.CATALOG) {
            val viewModel: CatalogViewModel = viewModel()
            CatalogScreen(
                viewModel = viewModel,
                onOpenType = { navController.navigate(Routes.typeDetail(it)) },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PERMISSIONS) {
            val viewModel: PermissionsViewModel = viewModel()
            PermissionsScreen(
                viewModel = viewModel,
                onRequestPermissions = onRequestPermissions,
                onContinue = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }

        composable(Routes.PRIVACY) {
            PrivacyScreen()
        }

        composable(
            route = Routes.TILE_DETAIL,
            arguments = listOf(
                navArgument("typeName") { type = NavType.StringType },
                navArgument("date") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val typeName = entry.arguments?.getString("typeName").orEmpty()
            val date = entry.arguments?.getString("date").orEmpty()
            val viewModel: TileDetailViewModel = viewModel()
            LaunchedEffect(typeName, date) { viewModel.load(typeName, date) }
            TileDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.TYPE_DETAIL,
            arguments = listOf(navArgument("typeName") { type = NavType.StringType }),
        ) { entry ->
            val typeName = entry.arguments?.getString("typeName").orEmpty()
            val viewModel: TypeDetailViewModel = viewModel()
            LaunchedEffect(typeName) { viewModel.load(typeName) }
            TypeDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenRecord = { },
            )
        }
    }
}
