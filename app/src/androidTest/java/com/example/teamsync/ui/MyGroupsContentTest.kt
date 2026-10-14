package com.example.teamsync.ui

import android.Manifest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.test.rule.GrantPermissionRule
import com.example.teamsync.data.model.Group
import com.example.teamsync.ui.components.BottomNavTab
import com.example.teamsync.ui.screens.MyGroupsContent
import com.example.teamsync.ui.theme.TeamSyncTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class MyGroupsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    // With the permission granted, "Join" calls onJoinGroup directly instead of opening a system dialog
    @get:Rule
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private fun group(id: String, name: String, ownerId: String, members: Int) = Group(
        id = id,
        name = name,
        memberIds = List(members) { "m$it" },
        ownerId = ownerId,
        invitationCode = "ABC123",
        workDays = DayOfWeek.entries.take(5),
        workStart = LocalTime.of(7, 0),
        workEnd = LocalTime.of(17, 0),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private val groups = listOf(
        group("1", "Capstone Team", ownerId = "me", members = 5),
        group("2", "Design Club", ownerId = "other", members = 3)
    )

    private fun show(
        state: MyGroupsUiState,
        onGroupClick: (Group) -> Unit = {},
        onJoinGroup: (String, Boolean) -> Unit = { _, _ -> },
        onTabClick: (BottomNavTab) -> Unit = {}
    ) = composeRule.setContent {
        TeamSyncTheme {
            MyGroupsContent(
                uiState = state,
                onGroupClick = onGroupClick,
                onJoinGroup = onJoinGroup,
                onJoinErrorDismissed = {},
                onJoinSuccess = {},
                onTabClick = onTabClick
            )
        }
    }

    @Test
    fun groupsAreShownWithMemberCountAndRole() {
        show(MyGroupsUiState(isLoading = false, groups = groups, currentUserId = "me"))

        composeRule.onNodeWithText("Capstone Team").assertIsDisplayed()
        composeRule.onNodeWithText("5 members").assertIsDisplayed()
        composeRule.onNodeWithText("Design Club").assertIsDisplayed()
        composeRule.onNodeWithText("3 members").assertIsDisplayed()
        // Owner of the first group only
        composeRule.onAllNodesWithText("Admin").assertCountEquals(1)
        composeRule.onAllNodesWithText("Member").assertCountEquals(1)
    }

    @Test
    fun whileLoadingTheGroupListIsHidden() {
        show(MyGroupsUiState(isLoading = true))

        composeRule.onAllNodesWithText("Join with code").assertCountEquals(0)
    }


    @Test
    fun tappingAGroupPassesThatGroupToTheCallback() {
        var clicked: Group? = null
        show(
            MyGroupsUiState(isLoading = false, groups = groups, currentUserId = "me"),
            onGroupClick = { clicked = it }
        )

        composeRule.onNodeWithText("Design Club").performClick()

        assertEquals("2", clicked?.id)
    }

    @Test
    fun tappingATabReportsItToTheCallback() {
        var tab: BottomNavTab? = null
        show(MyGroupsUiState(isLoading = false), onTabClick = { tab = it })

        composeRule.onNodeWithText("Create").performClick()

        assertEquals(BottomNavTab.CREATE, tab)
    }

    //** Join dialog

    @Test
    fun joinButtonIsDisabledUntilACodeIsTyped() {
        show(MyGroupsUiState(isLoading = false))

        composeRule.onNodeWithText("Join with code").performClick()
        composeRule.onNodeWithText("Join").assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextInput("abc123")
        composeRule.onNodeWithText("Join").assertIsEnabled()
    }

    @Test
    fun joiningSendsTheTypedCodeWithCalendarPermission() {
        var joined: Pair<String, Boolean>? = null
        show(
            MyGroupsUiState(isLoading = false),
            onJoinGroup = { code, sync -> joined = code to sync }
        )

        composeRule.onNodeWithText("Join with code").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("abc123")
        composeRule.onNodeWithText("Join").performClick()

        assertEquals("abc123" to true, joined)
    }

    @Test
    fun joinErrorIsShownInsideTheDialog() {
        show(MyGroupsUiState(isLoading = false, joinError = "There are not groups with this code"))

        composeRule.onNodeWithText("Join with code").performClick()

        composeRule.onNodeWithText("There are not groups with this code").assertIsDisplayed()
    }

    @Test
    fun cancelClosesTheDialog() {
        show(MyGroupsUiState(isLoading = false))

        composeRule.onNodeWithText("Join with code").performClick()
        composeRule.onNodeWithText("Join Group").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onAllNodesWithText("Join Group").assertCountEquals(0)
    }
}
