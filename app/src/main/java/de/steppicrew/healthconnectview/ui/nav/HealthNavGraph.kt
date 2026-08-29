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
    const val TILE_DETAIL = "tile/{typeName}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    fun typeDetail(typeName: String) = "type/$typeName"
    fun tileDetail(typeName: String) = "tile/$typeName"
}

@Composable
fun HealthNavGraph(
    onRequestPermissions: (Set<String>) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {

        composable(Routes.DASHBOARD) {
            val viewModel: DashboardViewModel = viewModel()
            DashboardScreen(
                viewModel = viewModel,
                onOpenType = { navController.navigate(Routes.tileDetail(it)) },
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
            arguments = listOf(navArgument("typeName") { type = NavType.StringType }),
        ) { entry ->
            val typeName = entry.arguments?.getString("typeName").orEmpty()
            val viewModel: TileDetailViewModel = viewModel()
            LaunchedEffect(typeName) { viewModel.load(typeName) }
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
