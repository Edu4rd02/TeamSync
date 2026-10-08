package com.example.teamsync.data

internal object FireStoreConstants {
    const val GROUPS_COLLECTION = "groups"
    object GroupFields {
        const val NAME = "name"
        const val DESCRIPTION = "description"
        const val MEMBER_IDS = "memberIds"
        const val OWNER_ID = "ownerId"
        const val INVITATION_CODE = "invitationCode"
        const val WORK_DAYS = "workDays"
        const val WORK_START = "workStart"
        const val WORK_END = "workEnd"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
    }

    const val MEMBERS_COLLECTION = "members"
    object MemberFields {
        const val ROLE = "role"
        const val DISPLAY_NAME = "displayName"
        const val PHOTO_URL = "photoUrl"
        const val JOINED_AT = "joinedAt"
        const val LAST_SYNC_AT = "lastSyncAt"
        const val CALENDAR_STATUS = "calendarStatus"
    }

    const val BUSYBLOCKS_COLLECTION = "busyBlocks"
    object BusyBlocksFields {
        const val USER_ID = "uid"
        const val BLOCK = "blocks"
        const val UPDATED_AT = "updatedAt"
        const val WINDOW_START = "windowStart"
        const val WINDOW_END = "windowEnd"
    }

    object TimeSlotFields {
        const val START = "start"
        const val END = "end"
    }
}

