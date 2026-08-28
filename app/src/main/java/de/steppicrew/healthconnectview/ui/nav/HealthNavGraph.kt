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
import de.steppicrew.healthconnectview.ui.detail.TypeDetailScreen
import de.steppicrew.healthconnectview.ui.detail.TypeDetailViewModel
import de.steppicrew.healthconnectview.ui.permissions.PermissionsScreen
import de.steppicrew.healthconnectview.ui.permissions.PermissionsViewModel

object Routes {
    const val CATALOG = "catalog"
    const val PERMISSIONS = "permissions"
    const val TYPE_DETAIL = "type/{typeName}"

    fun typeDetail(typeName: String) = "type/$typeName"
}

@Composable
fun HealthNavGraph(
    onRequestPermissions: (Set<String>) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.CATALOG) {

        composable(Routes.CATALOG) {
            val viewModel: CatalogViewModel = viewModel()
            CatalogScreen(
                viewModel = viewModel,
                onOpenType = { navController.navigate(Routes.typeDetail(it)) },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }

        composable(Routes.PERMISSIONS) {
            val viewModel: PermissionsViewModel = viewModel()
            PermissionsScreen(
                viewModel = viewModel,
                onRequestPermissions = onRequestPermissions,
                onContinue = { navController.popBackStack() },
                onOpenPrivacy = { },
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
