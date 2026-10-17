package com.example.teamsync.integration

import android.app.Application
import com.example.teamsync.MainDispatcherRule
import com.example.teamsync.data.CalendarSync
import com.example.teamsync.data.EventCalendarSync
import com.example.teamsync.data.model.Group
import com.example.teamsync.data.repository.AuthRepository
import com.example.teamsync.data.repository.GroupRepository
import com.example.teamsync.ui.CreateGroupViewModel
import com.example.teamsync.ui.MyGroupsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsFlowIntegrationTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    // In-memory stand-in for the groups collection; emitting here is what a Firestore listener does.
    private val db = MutableStateFlow(emptyList<Group>())

    private val authRepo = mockk<AuthRepository> {
        every { userId } returns "user1"
        every { displayName } returns "Eduardo"
        every { photoUrl } returns null
    }

    private val groupRepo = mockk<GroupRepository> {
        every { getMyGroups("user1") } returns db
        coEvery { createGroup(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            db.value = db.value + testGroup(
                id = "g${db.value.size + 1}",
                name = arg(0),
                ownerId = arg(2)
            )
        }
        coEvery { joinGroup(any(), any(), any(), any(), any()) } coAnswers {
            db.value = db.value + testGroup(
                id = "joined",
                name = "Joined group",
                ownerId = "someone-else",
                memberIds = listOf("someone-else", arg<String>(1))
            )
            Result.success("joined")
        }
        coEvery { getMyGroupIds(any()) } returns null
    }

    private val calendarSync = mockk<CalendarSync>(relaxed = true)

    private fun myGroupsVm() =
        MyGroupsViewModel(mockk<Application>(relaxed = true), groupRepo, authRepo, calendarSync, mockk<EventCalendarSync>(relaxed = true))

    private fun createGroupVm() = CreateGroupViewModel(groupRepo, authRepo)

    //** Data from the repository reaches the UI state

    @Test
    fun `groups from the repository are exposed to the UI`() {
        db.value = listOf(testGroup(id = "g1", name = "Alpha"), testGroup(id = "g2", name = "Beta"))

        val state = myGroupsVm().uiState.value

        assertFalse(state.isLoading)
        assertEquals(listOf("Alpha", "Beta"), state.groups.map { it.name })
        assertEquals("user1", state.currentUserId)
    }

    @Test
    fun `no groups gives an empty list and stops loading`() {
        val state = myGroupsVm().uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.groups.isEmpty())
    }

    @Test
    fun `user that is not signed in sees no groups and never queries the repository`() {
        every { authRepo.userId } returns null

        val state = myGroupsVm().uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.groups.isEmpty())
        coVerify(exactly = 0) { groupRepo.getMyGroups(any()) }
    }

    @Test
    fun `a failing groups listener stops loading instead of hanging`() {
        every { groupRepo.getMyGroups("user1") } returns flow { throw RuntimeException("offline") }

        val state = myGroupsVm().uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.groups.isEmpty())
    }

    //** Create -> list

    @Test
    fun `creating a group makes it appear in My Groups as owned by the user`() = runTest {
        val groups = myGroupsVm()
        val create = createGroupVm()
        assertTrue(groups.uiState.value.groups.isEmpty())

        create.onNameChange("Capstone Team")
        create.createGroup()

        assertTrue(create.uiState.value.isCreated)
        val state = groups.uiState.value
        assertEquals(listOf("Capstone Team"), state.groups.map { it.name })
        assertTrue(state.groups.single().isOwnedBy(state.currentUserId))
    }

    @Test
    fun `a failed creation leaves My Groups unchanged`() = runTest {
        coEvery {
            groupRepo.createGroup(any(), any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("offline")
        val groups = myGroupsVm()
        val create = createGroupVm()

        create.onNameChange("Capstone Team")
        create.createGroup()

        assertFalse(create.uiState.value.isCreated)
        assertTrue(groups.uiState.value.groups.isEmpty())
    }

    //** Join -> list

    @Test
    fun `joining with a valid code adds the group and flags success`() = runTest {
        val groups = myGroupsVm()

        groups.joinGroup("abc123 ", syncCalendar = false)

        coVerify(exactly = 1) {
            groupRepo.joinGroup("ABC123", "user1", "Eduardo", null, false)
        }
        val state = groups.uiState.value
        assertTrue(state.joinSuccess)
        assertNull(state.joinError)
        assertFalse(state.isJoining)
        assertEquals(listOf("Joined group"), state.groups.map { it.name })
        // Not the owner of the joined group -> shown as a plain member
        assertFalse(state.groups.single().isOwnedBy(state.currentUserId))
    }

    @Test
    fun `joining with calendar permission syncs the joined group right away`() = runTest {
        val groups = myGroupsVm()

        groups.joinGroup("ABC123", syncCalendar = true)

        coVerify(exactly = 1) { calendarSync.sync("joined", "user1", false) }
    }

    @Test
    fun `joining without calendar permission does not sync`() = runTest {
        val groups = myGroupsVm()

        groups.joinGroup("ABC123", syncCalendar = false)

        coVerify(exactly = 0) { calendarSync.sync(any(), any(), any()) }
    }

    @Test
    fun `onJoinSuccess resets the success flag`() = runTest {
        val groups = myGroupsVm()
        groups.joinGroup("ABC123", syncCalendar = false)

        groups.onJoinSuccess()

        assertFalse(groups.uiState.value.joinSuccess)
    }

    //** Join error conditions

    @Test
    fun `a code with the wrong length is rejected without calling the repository`() = runTest {
        val groups = myGroupsVm()

        groups.joinGroup("ABC", syncCalendar = true)

        assertEquals("Enter a valid group code", groups.uiState.value.joinError)
        coVerify(exactly = 0) { groupRepo.joinGroup(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an unknown code shows the not found message`() = runTest {
        coEvery {
            groupRepo.joinGroup(any(), any(), any(), any(), any())
        } returns Result.failure(NoSuchElementException("nope"))
        val groups = myGroupsVm()

        groups.joinGroup("ZZZZZZ", syncCalendar = true)

        val state = groups.uiState.value
        assertEquals("There are not groups with this code", state.joinError)
        assertFalse(state.joinSuccess)
        assertFalse(state.isJoining)
        assertTrue(state.groups.isEmpty())
        coVerify(exactly = 0) { calendarSync.sync(any(), any(), any()) }
    }

    @Test
    fun `any other failure shows the generic message and error can be dismissed`() = runTest {
        coEvery {
            groupRepo.joinGroup(any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("offline"))
        val groups = myGroupsVm()

        groups.joinGroup("ABC123", syncCalendar = false)
        assertEquals("Something goes wrong, try later.", groups.uiState.value.joinError)

        groups.onJoinErrorDismissed()
        assertNull(groups.uiState.value.joinError)
    }
}
