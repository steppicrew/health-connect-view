package de.steppicrew.healthconnectview.ui.nav

import android.net.Uri
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.ui.body.BodyCompositionScreen
import de.steppicrew.healthconnectview.ui.compare.CompareScreen
import de.steppicrew.healthconnectview.ui.insights.InsightsScreen
import de.steppicrew.healthconnectview.ui.insights.InsightsViewModel
import de.steppicrew.healthconnectview.ui.compare.CompareViewModel
import de.steppicrew.healthconnectview.ui.body.BodyCompositionViewModel
import de.steppicrew.healthconnectview.ui.session.SessionScreen
import de.steppicrew.healthconnectview.ui.session.SessionViewModel
import de.steppicrew.healthconnectview.ui.session.WorkoutsScreen
import de.steppicrew.healthconnectview.ui.session.WorkoutsViewModel
import de.steppicrew.healthconnectview.health.WorkoutFamily
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
import de.steppicrew.healthconnectview.ui.cycle.CycleScreen
import de.steppicrew.healthconnectview.ui.cycle.CycleViewModel
import de.steppicrew.healthconnectview.ui.dashboard.DashboardScreen
import de.steppicrew.healthconnectview.ui.dashboard.DashboardViewModel
import de.steppicrew.healthconnectview.ui.dashboard.TileDetailScreen
import de.steppicrew.healthconnectview.ui.dashboard.TileDetailViewModel
import de.steppicrew.healthconnectview.ui.permissions.PermissionsScreen
import de.steppicrew.healthconnectview.ui.permissions.PermissionsViewModel
import de.steppicrew.healthconnectview.ui.privacy.PrivacyScreen
import de.steppicrew.healthconnectview.ui.settings.SettingsScreen
import de.steppicrew.healthconnectview.ui.settings.SettingsViewModel
import java.time.LocalDate

object Routes {
    const val DASHBOARD = "dashboard"
    const val CATALOG = "catalog"
    const val PERMISSIONS = "permissions"
    const val TILE_DETAIL = "tile/{typeName}?date={date}&span={span}&session={session}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    /** `fixture` draws synthetic cycles; only the debug build has any to draw. */
    const val CYCLE = "cycle?fixture={fixture}"
    /** One session by its record's id; `kind` says which record type to read it from. */
    const val SESSION = "session/{kind}/{id}"

    /** The past year's workouts; `family` opens on one kind, by its enum name. */
    const val WORKOUTS = "workouts?family={family}"

    fun workouts() = "workouts"

    const val BODY = "body"

    const val INSIGHTS = "insights?date={date}"

    fun insights(date: String = "") = "insights?date=$date"

    const val COMPARE = "compare/{first}/{second}?span={span}&date={date}"

    fun compare(first: String, second: String, span: Span, date: String) = "compare/$first/$second?span=${span.name}&date=$date"

    fun session(session: Session) = "session/${session.kind.name}/${Uri.encode(session.recordId)}"

    fun cycle() = "cycle"

    fun tileDetail(typeName: String, date: String, span: Span = Span.DAY) = "tile/$typeName?date=$date&span=${span.name}"
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
                onOpenType = { type, date, span ->
                    navController.navigate(Routes.tileDetail(type, date, span))
                },
                onOpenCatalog = { navController.navigate(Routes.CATALOG) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenInsights = { navController.navigate(Routes.insights()) },
                onGrantAccess = { navController.navigate(Routes.PERMISSIONS) },
                onOpenBody = { navController.navigate(Routes.BODY) },
                onOpenSession = { navController.navigate(Routes.session(it)) },
            )
        }

        composable(Routes.CATALOG) {
            val viewModel: CatalogViewModel = viewModel()
            CatalogScreen(
                viewModel = viewModel,
                // The same screen a tile opens, on a week as the catalog's own screen did: one
                // place to export, compare and choose sources rather than a lesser twin.
                onOpenType = { navController.navigate(Routes.tileDetail(it, "", Span.WEEK)) },
                onOpenCycles = { navController.navigate(Routes.cycle()) },
                onOpenInsights = { navController.navigate(Routes.insights()) },
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

        composable(
            route = Routes.CYCLE,
            arguments = listOf(
                navArgument("fixture") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val viewModel: CycleViewModel = viewModel()
            CycleScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                fixture = entry.arguments?.getBoolean("fixture") ?: false,
            )
        }

        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.TILE_DETAIL,
            arguments = listOf(
                navArgument("typeName") { type = NavType.StringType },
                navArgument("date") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                // Opening straight onto a week or year view, so a multi-day rendering can be
                // checked with one command rather than a tap the test phone cannot accept.
                // Empty means the screen's own default, which is what navigation itself uses.
                navArgument("span") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                // An instant (ISO-8601): the session running then opens once loaded; or "route":
                // the first one with a route. For the same reason as span -- a session sheet
                // otherwise needs a tap, and a route one needs finding first.
                navArgument("session") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val typeName = entry.arguments?.getString("typeName").orEmpty()
            val date = entry.arguments?.getString("date").orEmpty()
            val span = entry.arguments?.getString("span").orEmpty()
            val viewModel: TileDetailViewModel = viewModel()
            LaunchedEffect(typeName, date, span) { viewModel.load(typeName, date, span) }
            TileDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                openSession = entry.arguments?.getString("session").orEmpty(),
                onOpenSession = { navController.navigate(Routes.session(it)) },
                onOpenWorkouts = { navController.navigate(Routes.workouts()) },
                onOpenBody = { navController.navigate(Routes.BODY) },
                onCompare = { first, second, shownSpan, shownDate ->
                    navController.navigate(Routes.compare(first, second, shownSpan, shownDate))
                },
            )
        }

        composable(
            route = Routes.SESSION,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
            ),
        ) { entry ->
            val kind = entry.arguments?.getString("kind")
                ?.let { name -> Session.Kind.entries.firstOrNull { it.name == name } }
                ?: Session.Kind.EXERCISE
            val id = entry.arguments?.getString("id").orEmpty()
            val viewModel: SessionViewModel = viewModel()
            LaunchedEffect(kind, id) { viewModel.load(kind, id) }
            SessionScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.COMPARE,
            arguments = listOf(
                navArgument("first") { type = NavType.StringType },
                navArgument("second") { type = NavType.StringType },
                navArgument("span") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("date") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val args = entry.arguments
            val viewModel: CompareViewModel = viewModel()
            LaunchedEffect(viewModel) {
                viewModel.start(
                    args?.getString("first").orEmpty(),
                    args?.getString("second").orEmpty(),
                    args?.getString("span").orEmpty(),
                    args?.getString("date").orEmpty(),
                )
            }
            CompareScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }

        composable(
            route = Routes.INSIGHTS,
            arguments = listOf(
                navArgument("date") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val date = entry.arguments?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val viewModel: InsightsViewModel = viewModel()
            LaunchedEffect(viewModel) { viewModel.start(date) }
            InsightsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                // The four weeks, not today: they are what the card compares, and today may hold
                // nothing yet just after midnight.
                onOpenType = { type -> navController.navigate(Routes.tileDetail(type, (date ?: LocalDate.now()).toString(), Span.MONTH)) },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }

        composable(Routes.BODY) {
            val viewModel: BodyCompositionViewModel = viewModel()
            BodyCompositionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }

        composable(
            route = Routes.WORKOUTS,
            arguments = listOf(
                navArgument("family") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val family = entry.arguments?.getString("family")
                ?.let { name -> WorkoutFamily.entries.firstOrNull { it.name == name } }
            val viewModel: WorkoutsViewModel = viewModel()
            LaunchedEffect(viewModel) { viewModel.start(family) }
            WorkoutsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenSession = { navController.navigate(Routes.session(it)) },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }
    }
}
