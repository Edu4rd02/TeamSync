package com.example.teamsync.ui.navigation

object TeamSyncRoutes {
    const val LOGIN = "login"
    const val GROUPS = "groups"
    const val GROUP_DETAIL = "group_detail/{groupId}"
    // eventId is optional: with it, the screen edits that event instead of proposing a new one
    const val CREATE_EVENT = "create_event/{groupId}?eventId={eventId}"
    const val EVENT_DETAIL = "event_detail/{groupId}/{eventId}"
    fun groupDetail(groupId: String) = "group_detail/$groupId"
    fun createEvent(groupId: String, eventId: String? = null) =
        if (eventId == null) "create_event/$groupId" else "create_event/$groupId?eventId=$eventId"
    fun eventDetail(groupId: String, eventId: String) = "event_detail/$groupId/$eventId"
    const val CREATE_GROUP = "create_group"
    const val PROFILE = "profile"
}
