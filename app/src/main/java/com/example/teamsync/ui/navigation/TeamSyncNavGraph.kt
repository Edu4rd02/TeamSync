package com.example.teamsync.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.example.teamsync.ui.screens.MyGroupsScreen
import com.example.teamsync.ui.screens.LoginScreen

@Composable
fun TeamSyncNavGraph(
    navController: NavHostController
){
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
                onTabClick = { /* TODO: Create and Account screens */ }
            )
        }
    }
}
