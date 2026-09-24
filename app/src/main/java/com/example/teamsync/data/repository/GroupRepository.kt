package com.example.teamsync.data.repository

import com.example.teamsync.data.model.Group
import com.example.teamsync.data.model.GroupRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface GroupRepository {
    fun getMyGroups(): Flow<List<Group>>
}

// In-memory data until the backend is wired up.
class FakeGroupRepository : GroupRepository {
    override fun getMyGroups(): Flow<List<Group>> = flowOf(
        listOf(
            Group("1", "Capstone Team", GroupRole.MEMBER, 5, "Thu Sep 17, 4:00 PM"),
            Group("2", "Design Club", GroupRole.MEMBER, 5, "Thu Sep 17, 4:00 PM"),
            Group("3", "Study Group", GroupRole.MEMBER, 5, "Thu Sep 17, 4:00 PM")
        )
    )
}
