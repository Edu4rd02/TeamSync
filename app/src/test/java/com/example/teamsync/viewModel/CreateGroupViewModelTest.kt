package com.example.teamsync.viewModel

import com.example.teamsync.MainDispatcherRule
import com.example.teamsync.data.repository.AuthRepository
import com.example.teamsync.data.repository.GroupRepository
import com.example.teamsync.ui.CreateGroupViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class CreateGroupViewModelTest {

    // Modify dispatcher
    @get:Rule
    val mainRule = MainDispatcherRule()

    // Mock repositories
    private val groupRepo = mockk<GroupRepository>()

    // Define attributes simulating a real user
    private val authRepo = mockk<AuthRepository> {
        every { userId } returns "user1"
        every { displayName } returns "Eduardo"
        every { photoUrl } returns "http://photo"
    }

    // Create a viewModel using mocked repositories
    private fun viewModel() = CreateGroupViewModel(groupRepo, authRepo)


    private fun stubCreateGroup() =
        coEvery { groupRepo.createGroup(any(), any(), any(), any(), any(), any(), any(), any()) }

    //** Initial state & field changes

    @Test
    fun `initial state uses the photo from auth`() {
        assertEquals("http://photo", viewModel().uiState.value.photoUrl)
    }

    @Test
    fun `onNameChange updates name`() {
        val vm = viewModel()
        vm.onNameChange("Team")
        assertEquals("Team", vm.uiState.value.name)
    }

    @Test
    fun `onDescriptionChange updates description`() {
        val vm = viewModel()
        vm.onDescriptionChange("Desc")
        assertEquals("Desc", vm.uiState.value.description)
    }

    @Test
    fun `onWorkDayToggle removes a selected day and adds it back`() {
        val vm = viewModel()
        assertTrue(DayOfWeek.MONDAY in vm.uiState.value.workDays)

        vm.onWorkDayToggle(DayOfWeek.MONDAY)
        assertFalse(DayOfWeek.MONDAY in vm.uiState.value.workDays)

        vm.onWorkDayToggle(DayOfWeek.MONDAY)
        assertTrue(DayOfWeek.MONDAY in vm.uiState.value.workDays)
    }

    @Test
    fun `onWorkDayToggle adds a day that was not selected`() {
        val vm = viewModel()
        vm.onWorkDayToggle(DayOfWeek.SATURDAY)
        assertTrue(DayOfWeek.SATURDAY in vm.uiState.value.workDays)
    }

    @Test
    fun `onWorkStartChange and onWorkEndChange update times`() {
        val vm = viewModel()
        vm.onWorkStartChange(LocalTime.of(8, 30))
        vm.onWorkEndChange(LocalTime.of(18, 0))
        assertEquals(LocalTime.of(8, 30), vm.uiState.value.workStart)
        assertEquals(LocalTime.of(18, 0), vm.uiState.value.workEnd)
    }

    @Test
    fun `onErrorShown clears the error`() = runTest {
        stubCreateGroup() throws RuntimeException("boom")
        val vm = viewModel()
        vm.onNameChange("Team")
        vm.createGroup()
        assertNotNull(vm.uiState.value.error)

        vm.onErrorShown()

        assertNull(vm.uiState.value.error)
    }

    //** Can create (based on validations of the viewModel)

    @Test
    fun `canCreate is false with blank name`() {
        val vm = viewModel()
        vm.onNameChange("   ")
        assertFalse(vm.uiState.value.canCreate)
    }

    @Test
    fun `canCreate is true with a name and default schedule`() {
        val vm = viewModel()
        vm.onNameChange("Team")
        assertTrue(vm.uiState.value.canCreate)
    }

    @Test
    fun `canCreate is false without work days`() {
        val vm = viewModel()
        vm.onNameChange("Team")
        vm.uiState.value.workDays.toList().forEach { vm.onWorkDayToggle(it) }
        assertTrue(vm.uiState.value.workDays.isEmpty())
        assertFalse(vm.uiState.value.canCreate)
    }

    @Test
    fun `canCreate is false when start is not before end`() {
        val vm = viewModel()
        vm.onNameChange("Team")
        vm.onWorkStartChange(LocalTime.of(17, 0))
        vm.onWorkEndChange(LocalTime.of(17, 0))
        assertFalse(vm.uiState.value.canCreate)

        vm.onWorkStartChange(LocalTime.of(18, 0))
        assertFalse(vm.uiState.value.canCreate)
    }

    //** Communication between the fun createGroup() in repository
    @Test
    fun `createGroup success sets isCreated`() = runTest {
        stubCreateGroup() returns Unit
        val vm = viewModel()
        vm.onNameChange("Team")

        vm.createGroup()

        assertTrue(vm.uiState.value.isCreated)
        assertFalse(vm.uiState.value.isCreating)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `createGroup sends trimmed data and null for blank description`() = runTest {
        stubCreateGroup() returns Unit
        val vm = viewModel()
        vm.onNameChange("  Team  ")
        vm.onDescriptionChange("   ")

        vm.createGroup()

        coVerify(exactly = 1) {
            groupRepo.createGroup(
                name = "Team",
                description = null,
                ownerId = "user1",
                ownerDisplayName = "Eduardo",
                ownerPhotoUrl = "http://photo",
                workDays = DayOfWeek.entries.take(5),
                workStart = LocalTime.of(7, 0),
                workEnd = LocalTime.of(17, 0)
            )
        }
    }

    @Test
    fun `createGroup failure shows error`() = runTest {
        stubCreateGroup() throws RuntimeException("boom")
        val vm = viewModel()
        vm.onNameChange("Team")

        vm.createGroup()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isCreating)
        assertFalse(vm.uiState.value.isCreated)
    }

    @Test
    fun `createGroup timeout shows error`() = runTest {
        stubCreateGroup() coAnswers { delay(20_000) }
        val vm = viewModel()
        vm.onNameChange("Team")

        vm.createGroup()
        assertTrue(vm.uiState.value.isCreating)
        advanceTimeBy(10_001)

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isCreating)
        assertFalse(vm.uiState.value.isCreated)
    }

    @Test
    fun `createGroup without userId does not call the repository`() = runTest {
        every { authRepo.userId } returns null
        val vm = viewModel()
        vm.onNameChange("Team")

        vm.createGroup()

        coVerify(exactly = 0) {
            groupRepo.createGroup(any(), any(), any(), any(), any(), any(), any(), any())
        }
        assertFalse(vm.uiState.value.isCreating)
    }

    @Test
    fun `createGroup with invalid form does not call the repository`() = runTest {
        val vm = viewModel() // blank name

        vm.createGroup()

        coVerify(exactly = 0) {
            groupRepo.createGroup(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }
}
