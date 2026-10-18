package com.example.teamsync.integration

import androidx.lifecycle.SavedStateHandle
import com.example.teamsync.MainDispatcherRule
import com.example.teamsync.data.CalendarSync
import com.example.teamsync.data.EventCalendarSync
import com.example.teamsync.data.FreeBlocksResult
import com.example.teamsync.data.MeetingOption
import com.example.teamsync.data.model.Event
import com.example.teamsync.data.model.TimeSlot
import com.example.teamsync.data.repository.AnnouncementsRepository
import com.example.teamsync.data.repository.AuthRepository
import com.example.teamsync.data.repository.EventRepository
import com.example.teamsync.data.repository.GroupRepository
import com.example.teamsync.ui.CreateEventViewModel
import com.example.teamsync.ui.FeedItemType
import com.example.teamsync.ui.GroupDetailViewModel
import com.google.firebase.Timestamp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CreateEventIntegrationTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val zone = ZoneId.systemDefault()
    private val day: LocalDate = LocalDate.now().plusDays(1)

    private fun millis(hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val availability = FreeBlocksResult(
        options = listOf(
            MeetingOption(
                slot = TimeSlot(millis(9), millis(10)),
                attendees = listOf("u1", "u2"),
                missing = emptyList()
            )
        ),
        syncedCount = 2,
        unsyncedCount = 1,
        names = mapOf("u1" to "Ana", "u2" to "Luis")
    )

    private val eventsDb = MutableStateFlow(emptyList<Event>())

    private val authRepo = mockk<AuthRepository> {
        every { userId } returns "user1"
        every { photoUrl } returns null
    }

    private val groupRepo = mockk<GroupRepository> {
        coEvery { getGroupOnce("g1") } returns testGroup(id = "g1")
        coEvery { freeBlocks(any(), any(), any(), any(), any(), any()) } returns availability
        every { getGroup("g1") } returns MutableStateFlow(testGroup(id = "g1"))
        every { getMembers("g1") } returns MutableStateFlow(emptyList())
    }

    private val eventRepo = mockk<EventRepository> {
        every { getEvents("g1") } returns eventsDb
        coEvery { getEventsOnce("g1") } coAnswers { eventsDb.value }
        coEvery { createEvent(any(), any(), any(), any(), any()) } coAnswers {
            val start = Instant.ofEpochSecond(arg<Timestamp>(3).seconds)
            eventsDb.value = eventsDb.value + testEvent(
                id = "e${eventsDb.value.size + 1}",
                title = arg(1),
                createdAt = start
            )
        }
    }

    private fun viewModel() = CreateEventViewModel(
        savedStateHandle = SavedStateHandle(mapOf("groupId" to "g1")),
        authRepository = authRepo,
        groupRepository = groupRepo,
        eventRepository = eventRepo
    )

    //** Availability from the repository reaches the UI state

    @Test
    fun `availability is loaded and grouped by day with member names`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertEquals(LocalTime.of(7, 0), state.workStart)
        assertEquals(LocalTime.of(17, 0), state.workEnd)
        assertEquals(1, state.unsyncedCount)

        val availableDay = state.days.single()
        assertEquals(day, availableDay.date)
        assertTrue(availableDay.isEveryoneFree)
        assertEquals(LocalTime.of(9, 0), availableDay.blocks.single().start)
        assertEquals(LocalTime.of(10, 0), availableDay.blocks.single().end)
        assertEquals(listOf("Ana", "Luis"), availableDay.blocks.single().attendees)
    }

    @Test
    fun `a failing availability search is reported and leaves no days`() = runTest {
        coEvery { groupRepo.freeBlocks(any(), any(), any(), any(), any(), any()) } throws RuntimeException("offline")
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.loadFailed)
        assertFalse(state.isLoading)
        assertTrue(state.days.isEmpty())
    }

    @Test
    fun `changing the duration drops the selection and searches again with it`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val block = vm.uiState.value.days.single().blocks.single()
        vm.onBlockSelected(day, block)
        assertNotNull(vm.uiState.value.selection)

        vm.onDurationChange(Duration.ofMinutes(30))
        advanceUntilIdle()

        assertNull(vm.uiState.value.selection)
        coVerify { groupRepo.freeBlocks("g1", any(), Duration.ofMinutes(30), any(), any(), any()) }
    }

    //** Save -> repository

    @Test
    fun `confirming stores the event with the chosen start and duration`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTitleChange("Sprint planning")
        vm.onBlockSelected(day, vm.uiState.value.days.single().blocks.single())
        var succeeded = false

        vm.onConfirm { succeeded = true }
        advanceUntilIdle()

        val expectedStart = day.atTime(9, 0).atZone(zone).toInstant()
        coVerify(exactly = 1) {
            eventRepo.createEvent(
                groupId = "g1",
                title = "Sprint planning",
                link = "",
                startAt = match { it.seconds == expectedStart.epochSecond },
                endAt = match { it.seconds == expectedStart.plus(Duration.ofHours(1)).epochSecond }
            )
        }
        assertTrue(succeeded)
        assertFalse(vm.uiState.value.isSaving)
        assertFalse(vm.uiState.value.saveFailed)
    }

    @Test
    fun `a start time later than the block allows is clamped`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onBlockSelected(day, vm.uiState.value.days.single().blocks.single())

        vm.onStartTimeChange(LocalTime.of(15, 0))

        // Block is 9:00-10:00 and the event lasts 1h, so the only valid start is 9:00
        assertEquals(LocalTime.of(9, 0), vm.uiState.value.selection?.startTime)
        assertTrue(vm.uiState.value.canConfirm)
    }

    //** Invalid input / error conditions

    @Test
    fun `confirming without a title shows errors and stores nothing`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onBlockSelected(day, vm.uiState.value.days.single().blocks.single())
        var succeeded = false

        vm.onConfirm { succeeded = true }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showErrors)
        assertFalse(vm.uiState.value.isTitleValid)
        assertFalse(succeeded)
        coVerify(exactly = 0) { eventRepo.createEvent(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `confirming without choosing a block stores nothing`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTitleChange("Sprint planning")

        vm.onConfirm()
        advanceUntilIdle()

        coVerify(exactly = 0) { eventRepo.createEvent(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failed save is reported and does not call onSuccess`() = runTest {
        coEvery { eventRepo.createEvent(any(), any(), any(), any(), any()) } throws RuntimeException("offline")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onTitleChange("Sprint planning")
        vm.onBlockSelected(day, vm.uiState.value.days.single().blocks.single())
        var succeeded = false

        vm.onConfirm { succeeded = true }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saveFailed)
        assertFalse(vm.uiState.value.isSaving)
        assertFalse(succeeded)
        assertTrue(eventsDb.value.isEmpty())
    }

    //** Create event -> group detail

    @Test
    fun `an event created from the proposal screen appears in the group detail feed`() = runTest {
        val detail = GroupDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("groupId" to "g1")),
            calendarSync = mockk<CalendarSync>(relaxed = true),
            groupRepository = groupRepo,
            eventRepository = eventRepo,
            announcementsRepository = mockk<AnnouncementsRepository> {
                every { getAnnouncements("g1") } returns MutableStateFlow(emptyList())
            },
            authRepository = authRepo,
            eventCalendarSync = mockk<EventCalendarSync>(relaxed = true)
        )
        assertTrue(detail.uiState.value.feed.isEmpty())

        val create = viewModel()
        advanceUntilIdle()
        create.onTitleChange("Sprint planning")
        create.onBlockSelected(day, create.uiState.value.days.single().blocks.single())
        create.onConfirm()
        advanceUntilIdle()

        val item = detail.uiState.value.feed.single()
        assertEquals("Sprint planning", item.title)
        assertEquals(FeedItemType.EVENT, item.type)
    }
}
