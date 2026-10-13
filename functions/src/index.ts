import {setGlobalOptions} from "firebase-functions";
import {onDocumentCreated} from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";
import {initializeApp} from "firebase-admin/app";
import {FieldValue, getFirestore} from "firebase-admin/firestore";
import {getMessaging} from "firebase-admin/messaging";

setGlobalOptions({maxInstances: 10});
initializeApp();

const GROUPS = "groups";
const USERS = "users";
const FCM_TOKENS = "fcmTokens";
const MEMBER_IDS = "memberIds";
const OWNER_ID = "ownerId";
const GROUP_NAME = "name";
const EVENT_TITLE = "title";

// sendEachForMulticast accepts at most 500 tokens per call.
const FCM_BATCH_SIZE = 500;
const DEAD_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

/**
 * Notifies every member of a group, except its admin (owner), when an
 * event is created under groups/{groupId}/events/{eventId}.
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
    const {groupId} = event.params;

    // Contains the info about the new event
    const eventData = event.data?.data();
    if (!eventData) return;

    // Fetch the group document
    const db = getFirestore();
    const groupSnap = await db.collection(GROUPS).doc(groupId).get();
    if (!groupSnap.exists) return;

    // Get the owner and members ids
    const ownerId = groupSnap.get(OWNER_ID) as string | undefined;
    const memberIds = (groupSnap.get(MEMBER_IDS) as string[] | undefined) ?? [];
    const recipients = memberIds.filter((uid) => uid !== ownerId);

    // Checks if the only member is not the admin
    if (recipients.length === 0) return;

    const userSnaps = await db.getAll(
      ...recipients.map((uid) => db.collection(USERS).doc(uid))
    );

    // token -> owner uid, so dead tokens can be removed from the right user.
    const tokenOwner = new Map<string, string>();
    for (const snap of userSnaps) {
      const tokens = (snap.get(FCM_TOKENS) as string[] | undefined) ?? [];
      tokens.forEach((token) => tokenOwner.set(token, snap.id));
    }
    const tokens = [...tokenOwner.keys()];
    if (tokens.length === 0) return;

    const groupName = (groupSnap.get(GROUP_NAME) as string | undefined) ??
      "Your group";
    const eventTitle = (eventData[EVENT_TITLE] as string | undefined) ?? "";

    // Data-only: the app's onMessageReceived builds the notification.
    // All values must be strings.
    const data = {
      title: `New event in ${groupName}`,
      body: eventTitle,
      groupId,
    };

    const deadTokens: string[] = [];
    for (let i = 0; i < tokens.length; i += FCM_BATCH_SIZE) {
      const batch = tokens.slice(i, i + FCM_BATCH_SIZE);
      const res = await getMessaging().sendEachForMulticast({
        tokens: batch,
        data,
        android: {priority: "high"},
      });
      res.responses.forEach((r, idx) => {
        if (r.error && DEAD_TOKEN_CODES.has(r.error.code)) {
          deadTokens.push(batch[idx]);
        } else if (r.error) {
          logger.warn("FCM send failed", {code: r.error.code});
        }
      });
    }

    // Remove tokens FCM says no longer exist.
    await Promise.all(deadTokens.map((token) =>
      db.collection(USERS).doc(tokenOwner.get(token) as string)
        .update({[FCM_TOKENS]: FieldValue.arrayRemove(token)})
    ));
  }
);
