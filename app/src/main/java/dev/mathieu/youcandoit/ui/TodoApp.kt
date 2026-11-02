package dev.mathieu.youcandoit.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import dev.mathieu.youcandoit.DeepLinks
import dev.mathieu.youcandoit.R
import dev.mathieu.youcandoit.ui.about.AboutScreen
import dev.mathieu.youcandoit.ui.common.LocalToday
import dev.mathieu.youcandoit.ui.common.rememberToday
import dev.mathieu.youcandoit.ui.editor.TaskEditorScreen
import dev.mathieu.youcandoit.ui.history.HistoryScreen
import dev.mathieu.youcandoit.ui.lists.ListsScreen
import dev.mathieu.youcandoit.ui.navigation.NavArgs
import dev.mathieu.youcandoit.ui.navigation.Routes
import dev.mathieu.youcandoit.ui.tasks.TaskListScreen

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    LISTS(Routes.LISTS, R.string.nav_lists, Icons.AutoMirrored.Filled.List),
    HISTORY(Routes.HISTORY, R.string.nav_history, Icons.Outlined.Inventory2),
}

@Composable
fun TodoApp(
    onShowWelcome: () -> Unit = {},
    updateReady: Boolean = false,
    onRestartToUpdate: () -> Unit = {},
    onUpdateLater: () -> Unit = {},
) {
    CompositionLocalProvider(LocalToday provides rememberToday()) {
        TodoNavigation(onShowWelcome, updateReady, onRestartToUpdate, onUpdateLater)
    }
}

@Composable
private fun TodoNavigation(
    onShowWelcome: () -> Unit,
    updateReady: Boolean,
    onRestartToUpdate: () -> Unit,
    onUpdateLater: () -> Unit,
) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val currentTab = when (route) {
        Routes.HOME -> Tab.HOME
        Routes.LISTS, Routes.LIST -> Tab.LISTS
        Routes.HISTORY -> Tab.HISTORY
        else -> null
    }

    val snackbar = remember { SnackbarHostState() }
    val updateMessage = stringResource(R.string.update_ready)
    val restartLabel = stringResource(R.string.action_restart)
    LaunchedEffect(updateReady) {
        if (!updateReady) return@LaunchedEffect
        val result = snackbar.showSnackbar(updateMessage, actionLabel = restartLabel, withDismissAction = true, duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) onRestartToUpdate() else onUpdateLater()
    }

    Scaffold(
        // Each screen has its own Scaffold and handles the system bars itself.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) {
                TaskListScreen(
                    onAddTask = { nav.navigate(Routes.newTask(it)) },
                    onEditTask = { nav.navigate(Routes.editTask(it)) },
                    onBack = null,
                    onShowWelcome = onShowWelcome,
                    onOpenAbout = { nav.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.LISTS) {
                ListsScreen(onOpenList = { nav.navigate(Routes.list(it)) })
            }
            composable(
                Routes.LIST,
                arguments = listOf(navArgument(NavArgs.LIST) { type = NavType.StringType }),
            ) {
                TaskListScreen(
                    onAddTask = { nav.navigate(Routes.newTask(it)) },
                    onEditTask = { nav.navigate(Routes.editTask(it)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen()
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { nav.popBackStack() })
            }
            composable(
                Routes.NEW_TASK,
                arguments = listOf(
                    navArgument(NavArgs.LIST) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(NavArgs.PARENT) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                TaskEditorScreen(
                    onClose = { nav.popBackStack() },
                    onAddSubTask = { nav.navigate(Routes.newSubTask(it)) },
                    onEditTask = { nav.navigate(Routes.editTask(it)) },
                )
            }
            composable(
                Routes.EDIT_TASK,
                arguments = listOf(navArgument(NavArgs.TASK_ID) { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.TASK_PATTERN }),
            ) {
                TaskEditorScreen(
                    onClose = { nav.popBackStack() },
                    onAddSubTask = { nav.navigate(Routes.newSubTask(it)) },
                    onEditTask = { nav.navigate(Routes.editTask(it)) },
                )
            }
        }
    }
}
