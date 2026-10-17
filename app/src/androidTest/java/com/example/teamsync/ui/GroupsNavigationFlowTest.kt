package com.example.teamsync.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasText
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import com.example.teamsync.data.CalendarSync
import com.example.teamsync.data.model.Group
import com.example.teamsync.data.repository.AuthRepository
import com.example.teamsync.data.repository.GroupRepository
import com.example.teamsync.ui.components.BottomNavTab
import com.example.teamsync.ui.navigation.TeamSyncRoutes
import com.example.teamsync.ui.screens.CreateGroupScreen
import com.example.teamsync.ui.screens.MyGroupsScreen
import com.example.teamsync.ui.theme.TeamSyncTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class GroupsNavigationFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val db = MutableStateFlow(emptyList<Group>())
    private lateinit var navController: TestNavHostController
    private lateinit var groupRepo: GroupRepository
    private lateinit var myGroupsVm: MyGroupsViewModel
    private lateinit var createGroupVm: CreateGroupViewModel

    private fun group(id: String, name: String, ownerId: String) = Group(
        id = id,
        name = name,
        memberIds = listOf(ownerId),
        ownerId = ownerId,
        invitationCode = "ABC123",
        workDays = DayOfWeek.entries.take(5),
        workStart = LocalTime.of(7, 0),
        workEnd = LocalTime.of(17, 0),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    @Before
    fun setUp() {
        val authRepo = mockk<AuthRepository> {
            every { userId } returns "user1"
            every { displayName } returns "Eduardo"
            every { photoUrl } returns null
        }
        groupRepo = mockk {
            every { getMyGroups("user1") } returns db
            coEvery { getMyGroupIds(any()) } returns null
            coEvery { createGroup(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                db.value = db.value + group("new", arg(0), "user1")
            }
            coEvery { joinGroup(any(), any(), any(), any(), any()) } coAnswers {
                db.value = db.value + group("joined", "Joined group", "someone-else")
                Result.success("joined")
            }
        }
        val app = ApplicationProvider.getApplicationContext<Application>()
        myGroupsVm = MyGroupsViewModel(app, groupRepo, authRepo, mockk<CalendarSync>(relaxed = true), mockk(relaxed = true))
        createGroupVm = CreateGroupViewModel(groupRepo, authRepo)
    }

    @Composable
    private fun TestApp() {
        navController = TestNavHostController(LocalContext.current).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
        }
        val onTabClick: (BottomNavTab) -> Unit = { tab ->
            val route = when (tab) {
                BottomNavTab.GROUPS -> TeamSyncRoutes.GROUPS
                BottomNavTab.CREATE -> TeamSyncRoutes.CREATE_GROUP
                BottomNavTab.ACCOUNT -> TeamSyncRoutes.PROFILE
            }
            navController.navigate(route) {
                popUpTo(TeamSyncRoutes.GROUPS)
                launchSingleTop = true
            }
        }
        TeamSyncTheme {
            NavHost(navController, startDestination = TeamSyncRoutes.GROUPS) {
                composable(TeamSyncRoutes.GROUPS) {
                    MyGroupsScreen(onGroupClick = {}, onTabClick = onTabClick, viewModel = myGroupsVm)
                }
                composable(TeamSyncRoutes.CREATE_GROUP) {
                    CreateGroupScreen(
                        onGroupCreated = {
                            navController.popBackStack(TeamSyncRoutes.GROUPS, inclusive = false)
                        },
                        onTabClick = onTabClick,
                        viewModel = createGroupVm
                    )
                }
            }
        }
    }

    private fun currentRoute() = composeRule.runOnIdle { navController.currentDestination?.route }


    @Test
    fun createTabOpensTheFormWithTheButtonDisabledUntilNameIsTyped() {
        composeRule.setContent { TestApp() }

        composeRule.onNodeWithText("Create").performClick()

        assertEquals(TeamSyncRoutes.CREATE_GROUP, currentRoute())
        composeRule.onNodeWithText("New Group").assertIsDisplayed()
        composeRule.onNodeWithText("Create group").assertIsNotEnabled()

        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("Capstone Team")
        composeRule.onNodeWithText("Create group").assertIsEnabled()
    }

    @Test
    fun creatingAGroupReturnsToTheListAndShowsTheNewGroup() {
        composeRule.setContent { TestApp() }

        composeRule.onNodeWithText("Create").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("Capstone Team")
        composeRule.onNodeWithText("Create group").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("My Groups")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(TeamSyncRoutes.GROUPS, currentRoute())
        composeRule.onNodeWithText("Capstone Team").assertIsDisplayed()
        composeRule.onNodeWithText("Admin").assertIsDisplayed()
    }

    @Test
    fun aFailedCreationKeepsTheUserOnTheFormWithoutAddingAGroup() {
        coEvery {
            groupRepo.createGroup(any(), any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("offline")
        composeRule.setContent { TestApp() }

        composeRule.onNodeWithText("Create").performClick()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("Capstone Team")
        composeRule.onNodeWithText("Create group").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { !createGroupVm.uiState.value.isCreating }
        assertEquals(TeamSyncRoutes.CREATE_GROUP, currentRoute())
        composeRule.onNodeWithText("Create group").assertIsEnabled()

        composeRule.onNodeWithText("Groups").performClick()
        composeRule.onAllNodesWithText("Capstone Team").assertCountEquals(0)
    }

    @Test
    fun joiningWithACodeAddsTheGroupAndClosesTheDialog() {
        composeRule.setContent { TestApp() }

        composeRule.onNodeWithText("Join with code").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("abc123")
        composeRule.onNodeWithText("Join").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("Joined group")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Member").assertIsDisplayed()
        composeRule.onAllNodesWithText("Join Group").assertCountEquals(0)
    }

    @Test
    fun joiningWithAnUnknownCodeShowsAnErrorInTheDialog() {
        coEvery {
            groupRepo.joinGroup(any(), any(), any(), any(), any())
        } returns Result.failure(NoSuchElementException("nope"))
        composeRule.setContent { TestApp() }

        composeRule.onNodeWithText("Join with code").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("zzzzzz")
        composeRule.onNodeWithText("Join").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("There are not groups with this code"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Join Group").assertIsDisplayed()
    }
}
