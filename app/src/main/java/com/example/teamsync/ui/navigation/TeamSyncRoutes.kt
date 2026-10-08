package com.example.teamsync.ui.navigation

object TeamSyncRoutes {
    const val LOGIN = "login"
    const val GROUPS = "groups"
    const val GROUP_DETAIL = "group_detail/{groupId}"
    const val CREATE_EVENT = "create_event/{groupId}"
    fun groupDetail(groupId: String) = "group_detail/$groupId"
    fun createEvent(groupId: String) = "create_event/$groupId"
    const val CREATE_GROUP = "create_group"
    const val PROFILE = "profile"
}