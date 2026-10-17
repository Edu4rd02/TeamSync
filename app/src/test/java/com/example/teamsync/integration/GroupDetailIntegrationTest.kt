package com.example.teamsync.integration

import androidx.lifecycle.SavedStateHandle
import com.example.teamsync.MainDispatcherRule
import com.example.teamsync.data.CalendarSync
import com.example.teamsync.data.EventCalendarSync
import com.example.teamsync.data.model.Announcement
import com.example.teamsync.data.model.Event
import com.example.teamsync.data.model.Group
import com.example.teamsync.data.model.Member
import com.example.teamsync.data.model.MemberRole
import com.example.teamsync.data.repository.AnnouncementsRepository
import com.example.teamsync.data.repository.AuthRepository
import com.example.teamsync.data.repository.EventRepository
import com.example.teamsync.data.repository.GroupRepository
import com.example.teamsync.ui.FeedFilter
import com.example.teamsync.ui.FeedItemType
import com.example.teamsync.ui.GroupDetailViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailIntegrationTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val groupFlow = MutableStateFlow<Group?>(testGroup(id = "g1", ownerId = "user1"))
    private val eventsFlow = MutableStateFlow(emptyList<Event>())
    private val announcementsFlow = MutableStateFlow(emptyList<Announcement>())
    private val membersFlow = MutableStateFlow(emptyList<Member>())

    private val currentUser = mockk<AuthRepository> {
        every { userId } returns "user1"
        every { photoUrl } returns null
    }

    private val groupRepo = mockk<GroupRepository> {
        every { getGroup("g1") } returns groupFlow
        every { getMembers("g1") } returns membersFlow
        coEvery { removeMember(any(), any()) } just Runs
        coEvery { deleteGroup(any()) } just Runs
    }
    private val eventRepo = mockk<EventRepository> {
        every { getEvents("g1") } returns eventsFlow
    }
    private val announcementsRepo = mockk<AnnouncementsRepository> {
        every { getAnnouncements("g1") } returns announcementsFlow
        coEvery { createAnnouncement(any(), any(), any()) } coAnswers {
            announcementsFlow.value = announcementsFlow.value + testAnnouncement(
                id = "new${announcementsFlow.value.size}",
                title = arg(1),
                text = arg(2),
                createdAt = Instant.ofEpochSecond(9_000)
            )
        }
    }
    private val calendarSync = mockk<CalendarSync>(relaxed = true)
    private val eventCalendarSync = mockk<EventCalendarSync>(relaxed = true)

    private fun viewModel() = GroupDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("groupId" to "g1")),
        calendarSync = calendarSync,
        groupRepository = groupRepo,
        eventRepository = eventRepo,
        announcementsRepository = announcementsRepo,
        authRepository = currentUser,
        eventCalendarSync = eventCalendarSync
    )

    private fun member(uid: String, name: String, role: MemberRole = MemberRole.MEMBER) =
        Member(uid = uid, role = role, displayName = name, joinedAt = Instant.EPOCH)

    //** Data from the repositories reaches the UI state

    @Test
    fun `group data is shown and the owner is recognised as admin`() {
        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals("Capstone Team", state.group?.name)
        assertTrue(state.isAdmin)
    }

    @Test
    fun `a member that does not own the group is not admin`() {
        every { currentUser.userId } returns "someone-else"

        assertFalse(viewModel().uiState.value.isAdmin)
    }

    @Test
    fun `a group that no longer exists leaves the group empty and stops loading`() {
        groupFlow.value = null

        val state = viewModel().uiState.value

        assertNull(state.group)
        assertFalse(state.isLoading)
    }

    @Test
    fun `entering the group refreshes the calendar for the signed in user`() {
        viewModel()

        coVerify(exactly = 1) { calendarSync.sync("g1", "user1", true) }
    }

    @Test
    fun `no calendar sync happens without a signed in user`() {
        every { currentUser.userId } returns null

        viewModel()

        coVerify(exactly = 0) { calendarSync.sync(any(), any(), any()) }
    }

    //** Feed merges events and announcements

    @Test
    fun `feed merges events and announcements newest first`() {
        eventsFlow.value = listOf(testEvent(id = "e1", createdAt = Instant.ofEpochSecond(1_000)))
        announcementsFlow.value = listOf(
            testAnnouncement(id = "a1", createdAt = Instant.ofEpochSecond(3_000)),
            testAnnouncement(id = "a2", createdAt = Instant.ofEpochSecond(500))
        )

        val feed = viewModel().uiState.value.feed

        assertEquals(listOf("a1", "e1", "a2"), feed.map { it.id })
        assertEquals(
            listOf(FeedItemType.ANNOUNCEMENT, FeedItemType.EVENT, FeedItemType.ANNOUNCEMENT),
            feed.map { it.type }
        )
    }

    @Test
    fun `an event created elsewhere shows up live in the feed`() {
        val vm = viewModel()
        assertTrue(vm.uiState.value.feed.isEmpty())

        eventsFlow.value = listOf(testEvent(id = "e1", title = "Sprint planning"))

        assertEquals(listOf("Sprint planning"), vm.uiState.value.feed.map { it.title })
    }

    @Test
    fun `filters show only the selected kind of item`() {
        eventsFlow.value = listOf(testEvent(id = "e1"))
        announcementsFlow.value = listOf(testAnnouncement(id = "a1"))
        val vm = viewModel()

        assertEquals(2, vm.uiState.value.visibleFeed.size)

        vm.onFilterSelected(FeedFilter.EVENTS)
        assertEquals(listOf("e1"), vm.uiState.value.visibleFeed.map { it.id })

        vm.onFilterSelected(FeedFilter.ANNOUNCEMENTS)
        assertEquals(listOf("a1"), vm.uiState.value.visibleFeed.map { it.id })

        vm.onFilterSelected(FeedFilter.ALL)
        assertEquals(2, vm.uiState.value.visibleFeed.size)
    }

    @Test
    fun `a failing events listener keeps the rest of the screen working`() {
        every { eventRepo.getEvents("g1") } returns flow { throw RuntimeException("offline") }
        announcementsFlow.value = listOf(testAnnouncement(id = "a1"))

        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.events.isEmpty())
        assertEquals("Capstone Team", state.group?.name)
        assertEquals(listOf("a1"), state.feed.map { it.id })
    }

    //** Publish announcement -> feed

    @Test
    fun `publishing an announcement trims it, stores it and shows it in the feed`() = runTest {
        val vm = viewModel()
        var succeeded = false

        vm.publishAnnouncement("  Meeting moved ", "  Now at 3pm  ") { succeeded = true }

        coVerify(exactly = 1) { announcementsRepo.createAnnouncement("g1", "Meeting moved", "Now at 3pm") }
        assertTrue(succeeded)
        assertFalse(vm.uiState.value.isPublishing)
        val item = vm.uiState.value.feed.single()
        assertEquals(FeedItemType.ANNOUNCEMENT, item.type)
        assertEquals("Meeting moved", item.title)
        assertEquals("Now at 3pm", item.text)
    }

    @Test
    fun `blank title or message is not published`() = runTest {
        val vm = viewModel()
        var succeeded = false

        vm.publishAnnouncement("   ", "text") { succeeded = true }
        vm.publishAnnouncement("title", "") { succeeded = true }

        coVerify(exactly = 0) { announcementsRepo.createAnnouncement(any(), any(), any()) }
        assertFalse(succeeded)
        assertTrue(vm.uiState.value.feed.isEmpty())
    }

    @Test
    fun `a failed publish does not call onSuccess nor change the feed`() = runTest {
        coEvery { announcementsRepo.createAnnouncement(any(), any(), any()) } throws RuntimeException("offline")
        val vm = viewModel()
        var succeeded = false

        vm.publishAnnouncement("Title", "Text") { succeeded = true }

        assertFalse(succeeded)
        assertFalse(vm.uiState.value.isPublishing)
        assertTrue(vm.uiState.value.feed.isEmpty())
    }

    //** Members, leaving and deleting

    @Test
    fun `members are listed admin first and then by name`() {
        membersFlow.value = listOf(
            member("u3", "luis"),
            member("user1", "Zoe", MemberRole.ADMIN),
            member("u2", "Ana")
        )

        assertEquals(listOf("user1", "u2", "u3"), viewModel().uiState.value.members.map { it.uid })
    }

    @Test
    fun `a member leaving is removed, their calendar is cleaned and the screen closes`() = runTest {
        every { currentUser.userId } returns "someone-else"
        val vm = viewModel()

        vm.leaveGroup()

        coVerify(exactly = 1) { groupRepo.removeMember("g1", "someone-else") }
        coVerify(exactly = 1) { eventCalendarSync.removeGroup("g1") }
        assertTrue(vm.uiState.value.exited)
        assertFalse(vm.uiState.value.isLeaving)
    }

    @Test
    fun `a failed leave keeps the calendar and the screen as they were`() = runTest {
        every { currentUser.userId } returns "someone-else"
        coEvery { groupRepo.removeMember(any(), any()) } throws RuntimeException("offline")
        val vm = viewModel()

        vm.leaveGroup()

        coVerify(exactly = 0) { eventCalendarSync.removeGroup(any()) }
        assertFalse(vm.uiState.value.exited)
        assertFalse(vm.uiState.value.isLeaving)
    }

    @Test
    fun `the admin deletes the group instead of leaving it`() = runTest {
        val vm = viewModel()

        vm.leaveGroup()
        coVerify(exactly = 0) { groupRepo.removeMember(any(), any()) }

        vm.deleteGroup()
        coVerify(exactly = 1) { groupRepo.deleteGroup("g1") }
        coVerify(exactly = 1) { eventCalendarSync.removeGroup("g1") }
        assertTrue(vm.uiState.value.exited)
    }

    @Test
    fun `a member can not delete the group`() = runTest {
        every { currentUser.userId } returns "someone-else"

        viewModel().deleteGroup()

        coVerify(exactly = 0) { groupRepo.deleteGroup(any()) }
    }

    @Test
    fun `removing a member leaves the admin's own calendar untouched`() = runTest {
        val vm = viewModel()

        vm.removeMember("u2")

        coVerify(exactly = 1) { groupRepo.removeMember("g1", "u2") }
        coVerify(exactly = 0) { eventCalendarSync.removeGroup(any()) }
        assertFalse(vm.uiState.value.exited)
    }

    @Test
    fun `only the admin removes members and never themselves`() = runTest {
        viewModel().removeMember("user1")

        every { currentUser.userId } returns "someone-else"
        viewModel().removeMember("u2")

        coVerify(exactly = 0) { groupRepo.removeMember(any(), any()) }
    }
}
