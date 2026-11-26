package dev.suks.midas.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import dev.suks.midas.ui.suite.NavBarItem
import dev.suks.midas.ui.suite.SuiteNavBar
import dev.suks.midas.ui.suite.SuiteTransitions
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.suks.midas.MainActivity
import dev.suks.midas.ui.bills.BillsScreen
import dev.suks.midas.ui.budgets.BudgetsScreen
import dev.suks.midas.ui.home.HomeScreen
import dev.suks.midas.ui.insights.InsightsScreen
import dev.suks.midas.ui.lists.ListsScreen
import dev.suks.midas.ui.settings.CategoriesScreen
import dev.suks.midas.ui.settings.SettingsScreen
import dev.suks.midas.ui.split.SplitScreen
import dev.suks.midas.ui.transactions.EditTransactionScreen
import dev.suks.midas.ui.transactions.TransactionsScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object HomeRoute
@Serializable object ActivityRoute
@Serializable data class ListsRoute(val tab: Int = 0)
@Serializable object BillsRoute
@Serializable object InsightsRoute
@Serializable data class EditTransactionRoute(
    val id: Long = 0,
    val income: Boolean = false,
    /** Text shared from another app, read for an amount and payee. */
    val sharedText: String = "",
)
@Serializable object BudgetsRoute
@Serializable object SettingsRoute
@Serializable object CategoriesRoute
@Serializable object SplitRoute

private data class Tab(val label: String, val route: Any, val routeClass: KClass<*>, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("Home", HomeRoute, HomeRoute::class, Icons.Outlined.Home, Icons.Rounded.Home),
    Tab("Activity", ActivityRoute, ActivityRoute::class, Icons.Outlined.SwapVert, Icons.Rounded.SwapVert),
    Tab("Lists", ListsRoute(), ListsRoute::class, Icons.Outlined.Checklist, Icons.Rounded.Checklist),
    Tab("Bills", BillsRoute, BillsRoute::class, Icons.Outlined.ReceiptLong, Icons.Rounded.ReceiptLong),
    Tab("Insights", InsightsRoute, InsightsRoute::class, Icons.Outlined.Insights, Icons.Rounded.Insights),
)

/**
 * [workspaceKey] identifies the open workspace. When it changes the whole navigation graph is
 * rebuilt, so every screen and ViewModel starts fresh against the new workspace's data.
 */
@Composable
fun MidasApp(
    workspaceKey: String = "", pendingAction: String? = null, onActionHandled: () -> Unit = {},
    sharedText: String? = null, onShareHandled: () -> Unit = {},
) {
    key(workspaceKey) { MidasNav(pendingAction, onActionHandled, sharedText, onShareHandled) }
}

@Composable
private fun MidasNav(pendingAction: String?, onActionHandled: () -> Unit, sharedText: String?, onShareHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onTab = tabs.any { t -> destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }

    LaunchedEffect(pendingAction) {
        when (pendingAction) {
            MainActivity.ACTION_ADD_EXPENSE -> nav.navigate(EditTransactionRoute())
            MainActivity.ACTION_OPEN_GROCERIES -> nav.switchTab(ListsRoute(tab = 1))
            MainActivity.ACTION_OPEN_BILLS -> nav.switchTab(BillsRoute)
            else -> return@LaunchedEffect
        }
        onActionHandled()
    }

    LaunchedEffect(sharedText) {
        if (sharedText == null) return@LaunchedEffect
        nav.navigate(EditTransactionRoute(sharedText = sharedText))
        onShareHandled()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTab) {
                SuiteNavBar(
                    tabs.map { tab ->
                        NavBarItem(
                            label = tab.label,
                            icon = tab.icon,
                            selectedIcon = tab.selectedIcon,
                            selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true,
                            onClick = { nav.switchTab(tab.route) },
                        )
                    }
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { if (isTabSwitch()) SuiteTransitions.tabEnter else SuiteTransitions.pushEnter },
            exitTransition = { if (isTabSwitch()) SuiteTransitions.tabExit else SuiteTransitions.pushExit },
            popEnterTransition = { if (isTabSwitch()) SuiteTransitions.tabEnter else SuiteTransitions.popEnter },
            popExitTransition = { if (isTabSwitch()) SuiteTransitions.tabExit else SuiteTransitions.popExit },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onAdd = { nav.navigate(EditTransactionRoute()) },
                    onOpenTransaction = { nav.navigate(EditTransactionRoute(id = it)) },
                    onSeeAll = { nav.switchTab(ActivityRoute) },
                    onBudgets = { nav.navigate(BudgetsRoute) },
                    onBills = { nav.switchTab(BillsRoute) },
                    onGroceries = { nav.switchTab(ListsRoute(tab = 1)) },
                    onSettings = { nav.navigate(SettingsRoute) },
                    onSplit = { nav.navigate(SplitRoute) },
                )
            }
            composable<ActivityRoute> {
                TransactionsScreen(
                    onAdd = { income -> nav.navigate(EditTransactionRoute(income = income)) },
                    onOpen = { nav.navigate(EditTransactionRoute(id = it)) },
                )
            }
            composable<ListsRoute> { ListsScreen(initialTab = it.toRoute<ListsRoute>().tab) }
            composable<BillsRoute> { BillsScreen() }
            composable<InsightsRoute> { InsightsScreen(onBudgets = { nav.navigate(BudgetsRoute) }) }
            composable<EditTransactionRoute> {
                val r = it.toRoute<EditTransactionRoute>()
                EditTransactionScreen(id = r.id, startAsIncome = r.income, onDone = { nav.popBackStack() }, sharedText = r.sharedText)
            }
            composable<BudgetsRoute> { BudgetsScreen(onBack = { nav.popBackStack() }) }
            composable<SettingsRoute> {
                SettingsScreen(onBack = { nav.popBackStack() }, onCategories = { nav.navigate(CategoriesRoute) }, onSplit = { nav.navigate(SplitRoute) })
            }
            composable<SplitRoute> { SplitScreen(onBack = { nav.popBackStack() }) }
            composable<CategoriesRoute> { CategoriesScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavDestination.isTab(): Boolean = tabs.any { t -> hierarchy.any { it.hasRoute(t.routeClass) } }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.isTab() && targetState.destination.isTab()
