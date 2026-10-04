package com.example.teamsync.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.example.teamsync.ui.components.BottomNavTab
import com.example.teamsync.ui.screens.CreateGroupScreen
import com.example.teamsync.ui.screens.GroupDetailScreen
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
            BottomNavTab.CREATE -> TeamSyncRoutes.CREATE_GROUP
        }
        navController.navigate(route) {
            // Keep Groups as the only entry below the current tab.
            popUpTo(TeamSyncRoutes.GROUPS)
            launchSingleTop = true
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
                onGroupClick = { group ->
                    navController.navigate(TeamSyncRoutes.groupDetail(group.id))
                },
                onTabClick = onTabClick
            )
        }

        composable(
            route = TeamSyncRoutes.GROUP_DETAIL,
            arguments = listOf(navArgument("groupId") {type = NavType.StringType})
        ){
            GroupDetailScreen(
                onBack = {
                    navController.popBackStack()
                },
                onTabClick = onTabClick
            )
        }

        composable(TeamSyncRoutes.CREATE_GROUP){
            CreateGroupScreen(
                onGroupCreated = {
                    navController.popBackStack(TeamSyncRoutes.GROUPS, inclusive = false)
                },
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
