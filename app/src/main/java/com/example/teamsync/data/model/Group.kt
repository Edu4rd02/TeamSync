package com.example.teamsync.data.model

enum class GroupRole { ADMIN, MEMBER }

data class Group(
    val id: String,
    val name: String,
    val role: GroupRole,
    val memberCount: Int,
    val nextEvent: String?
)
