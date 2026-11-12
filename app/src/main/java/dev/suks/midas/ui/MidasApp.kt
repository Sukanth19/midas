package dev.suks.midas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import dev.suks.midas.ui.transactions.EditTransactionScreen
import dev.suks.midas.ui.transactions.TransactionsScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object HomeRoute
@Serializable object ActivityRoute
@Serializable data class ListsRoute(val tab: Int = 0)
@Serializable object BillsRoute
@Serializable object InsightsRoute
@Serializable data class EditTransactionRoute(val id: Long = 0, val income: Boolean = false)
@Serializable object BudgetsRoute
@Serializable object SettingsRoute
@Serializable object CategoriesRoute

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
fun MidasApp(workspaceKey: String = "", pendingAction: String? = null, onActionHandled: () -> Unit = {}) {
    key(workspaceKey) { MidasNav(pendingAction, onActionHandled) }
}

@Composable
private fun MidasNav(pendingAction: String?, onActionHandled: () -> Unit) {
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

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(onTab, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
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
                EditTransactionScreen(id = r.id, startAsIncome = r.income, onDone = { nav.popBackStack() })
            }
            composable<BudgetsRoute> { BudgetsScreen(onBack = { nav.popBackStack() }) }
            composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }, onCategories = { nav.navigate(CategoriesRoute) }) }
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
