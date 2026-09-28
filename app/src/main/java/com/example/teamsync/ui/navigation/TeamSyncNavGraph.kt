package com.example.teamsync.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.example.teamsync.ui.components.BottomNavTab
import com.example.teamsync.ui.screens.MyGroupsScreen
import com.example.teamsync.ui.screens.LoginScreen
import com.example.teamsync.ui.screens.ProfileScreen

@Composable
fun TeamSyncNavGraph(
    navController: NavHostController
){
    val onTabClick: (BottomNavTab) -> Unit = { tab ->
        val route = when (tab) {
            BottomNavTab.GROUPS -> TeamSyncRoutes.GROUPS
            BottomNavTab.ACCOUNT -> TeamSyncRoutes.PROFILE
            BottomNavTab.CREATE -> null // TODO: Create screen
        }
        route?.let {
            navController.navigate(it) {
                // Keep Groups as the only entry below the current tab.
                popUpTo(TeamSyncRoutes.GROUPS)
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = TeamSyncRoutes.LOGIN
    ){
        composable(TeamSyncRoutes.LOGIN){
            LoginScreen(
                onSignedIn = {
                    navController.navigate(TeamSyncRoutes.GROUPS) {
                        popUpTo(TeamSyncRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(TeamSyncRoutes.GROUPS){
            MyGroupsScreen(
                onGroupClick = { /* TODO: navigate to group detail */ },
                onJoinWithCodeClick = { /* TODO: join with code */ },
                onTabClick = onTabClick
            )
        }
        composable(TeamSyncRoutes.PROFILE){
            ProfileScreen(
                onSignedOut = {
                    navController.navigate(TeamSyncRoutes.LOGIN) {
                        // Clear the whole back stack so Back can't return to signed-in screens.
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onTabClick = onTabClick
            )
        }
    }
}
