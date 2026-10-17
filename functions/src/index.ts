import { setGlobalOptions } from "firebase-functions";
import { onDocumentCreated, onDocumentDeleted } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";
import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

setGlobalOptions({ maxInstances: 10 });
initializeApp();

const GROUPS = "groups";
const USERS = "users";
const FCM_TOKENS = "fcmTokens";
const MEMBER_IDS = "memberIds";
const OWNER_ID = "ownerId";
const GROUP_NAME = "name";
const EVENT_TITLE = "title";
const ANNOUNCEMENT_TITLE = "title";
const MESSAGE_GROUP_REMOVED = "group_removed";

// sendEachForMulticast accepts at most 500 tokens per call.
const FCM_BATCH_SIZE = 500;
const DEAD_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

/**
 * Sends a push to every member of a group except its admin (owner).
 * `groupId` travels in the data payload so the app can open that group
 * when the notification is tapped.
 */
async function notifyGroupMembers(
  groupId: string,
  buildMessage: (groupName: string) => { title: string; body: string }
) {
  // Fetch the group document
  const db = getFirestore();
  const groupSnap = await db.collection(GROUPS).doc(groupId).get();
  if (!groupSnap.exists) return;

  // Get the owner and members ids
  const ownerId = groupSnap.get(OWNER_ID) as string | undefined;
  const memberIds = (groupSnap.get(MEMBER_IDS) as string[] | undefined) ?? [];
  const recipients = memberIds.filter((uid) => uid !== ownerId);

  const groupName = (groupSnap.get(GROUP_NAME) as string | undefined) ??
    "Your group";

  // Data-only: the app's onMessageReceived builds the notification.
  await sendToUsers(recipients, { ...buildMessage(groupName), groupId });
}

/**
 * Sends a data-only message to every device of the given users and
 * removes the tokens FCM reports as dead.
 */
async function sendToUsers(uids: string[], data: Record<string, string>) {
  // If the only recipient is the owner, don't send a notification.
  if (uids.length == 0) return;
  const db = getFirestore();
  const userSnaps = await db.getAll(
    ...uids.map((uid) => db.collection(USERS).doc(uid))
  );

  // token -> owner uid, so dead tokens can be removed from the right user.
  const tokenOwner = new Map<string, string>();
  for (const snap of userSnaps) {
    const tokens = (snap.get(FCM_TOKENS) as string[] | undefined) ?? [];
    tokens.forEach((token) => tokenOwner.set(token, snap.id));
  }
  const tokens = [...tokenOwner.keys()];
  if (tokens.length === 0) return;


  const deadTokens: string[] = [];
  for (let i = 0; i < tokens.length; i += FCM_BATCH_SIZE) {
    const batch = tokens.slice(i, i + FCM_BATCH_SIZE);
    const res = await getMessaging().sendEachForMulticast({
      tokens: batch,
      data,
      android: { priority: "high" },
    });
    res.responses.forEach((r, idx) => {
      if (r.error && DEAD_TOKEN_CODES.has(r.error.code)) {
        deadTokens.push(batch[idx]);
      } else if (r.error) {
        logger.warn("FCM send failed", { code: r.error.code });
      }
    });
  }

  // Remove tokens FCM says no longer exist.
  await Promise.all(deadTokens.map((token) =>
    db.collection(USERS).doc(tokenOwner.get(token) as string)
      .update({ [FCM_TOKENS]: FieldValue.arrayRemove(token) })
  ));
}

/**
 * Notifies group members when an event is created under
 * groups/{groupId}/events/{eventId}.
 */
export const onEventCreated = onDocumentCreated(
  // Document path to see
  // The region must be the same as the Firestore region
  {
    document: "groups/{groupId}/events/{eventId}",
    region: "us-west2",
  },
  async (event) => {
    // Contains path parameters (groupId, eventId)
    const { groupId } = event.params;

    // Contains the info about the new event
    const eventData = event.data?.data();
    if (!eventData) return;

    const eventTitle = (eventData[EVENT_TITLE] as string | undefined) ?? "";
    await notifyGroupMembers(groupId, (groupName) => ({
      title: `New event in ${groupName}`,
      body: eventTitle,
    }));
  }
);

/**                                                                                                                                                                                                                                                                          
   * Tells a user's devices to remove the group's upcoming events from their
   * calendar once they are no longer part of groups/{groupId}.
   */
export const onMemberDeleted = onDocumentDeleted(
  {
    document: "groups/{groupId}/members/{uid}",
    region: "us-west2",
  },
  async (event) => {
    const { groupId, uid } = event.params;
    // No title: the app handles it silently, without a notification
    await sendToUsers([uid], { type: MESSAGE_GROUP_REMOVED, groupId, uid });
  }
);

/**
 * Deleting a document leaves its subcollections behind, so this removes
 * events (with their confirmations), members, busyBlocks and announcements.
 * Each members/{uid} deletion fires onMemberDeleted.
 */
export const onGroupDeleted = onDocumentDeleted(
  {
    document: "groups/{groupId}",
    region: "us-west2",
  },
  async (event) => {
    const { groupId } = event.params;
    await getFirestore().recursiveDelete(
      getFirestore().collection(GROUPS).doc(groupId)
    );
  }
);

/**
 * Notifies group members when an announcement is created under
 * groups/{groupId}/announcements/{announcementId}.
 */
export const onAnnouncementCreated = onDocumentCreated(
  {
    document: "groups/{groupId}/announcements/{announcementId}",
    region: "us-west2",
  },
  async (event) => {
    const { groupId } = event.params;

    const announcementData = event.data?.data();
    if (!announcementData) return;

    const announcementTitle =
      (announcementData[ANNOUNCEMENT_TITLE] as string | undefined) ?? "";
    await notifyGroupMembers(groupId, (groupName) => ({
      title: `New announcement in ${groupName}`,
      body: announcementTitle,
    }));
  }
);
