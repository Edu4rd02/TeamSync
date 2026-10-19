# TeamSync

TeamSync is an Android app for coordinating teams and work groups. Each user signs in with Google, creates or joins a group using an invitation code, and syncs their device calendar. With that information the app knows the members' availability, so the group admin can create events, members confirm them, and everyone receives push notifications.

## Features

- Google sign-in.
- Group creation with work schedule (work days and hours) and an invitation code to join.
- Device calendar sync: the user's events are stored as the group's busy blocks.
- Event creation by the admin, with attendance confirmation from members.
- Group events are written to the device calendar.
- Announcements within the group.
- Push notifications (new event, user removed from a group, etc.).
- User profile.

## Tech stack

- Kotlin + Jetpack Compose (Material 3) and Navigation Compose
- Credential Manager (Google Sign-In)
- Firebase: Authentication, Cloud Firestore and Cloud Messaging
- Firebase Cloud Functions (TypeScript, `functions/` folder) to send push notifications
- Coil for profile pictures
- Testing with JUnit, MockK and Compose UI Test

## ⚠️ Firebase requirements

**TeamSync does not work without Firebase.** To run it you need your own Firebase project with these services enabled:

| Service | Used for |
|---|---|
| **Authentication** (Google provider enabled) | Sign-in |
| **Cloud Firestore** | Groups, members, events, announcements and busy blocks |
| **Cloud Messaging (FCM)** | Push notifications |

If you want to run the project, **paste your Firebase project configuration into [`app/google-services-example.json`](app/google-services-example.json)** (the contents of the `google-services.json` file that Firebase gives you) and follow the steps below.

You must also paste your project's **`WEB_CLIENT_ID`** into `local.properties` (project root), otherwise Google sign-in will not work:

```properties
WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
```

## How to run it

### Prerequisites

- Android Studio (recent version) and JDK 11 or higher
- A device or emulator running Android 9 (API 28) or higher with Google Play Services
- A Google account to create the Firebase project
- (Optional, only for push notifications) Node.js 24 and the [Firebase CLI](https://firebase.google.com/docs/cli)

### Steps

1. **Clone the repository** and open it in Android Studio.

   ```bash
   git clone <repository-url>
   ```

2. **Create a project in the [Firebase console](https://console.firebase.google.com/)** and register an Android app with the package name `com.example.teamsync`. Add the SHA-1 fingerprint of your debug keystore (required for Google Sign-In):

   ```bash
   ./gradlew signingReport
   ```

3. **Enable the services** in the Firebase console:
   - *Authentication* → *Sign-in method* → enable **Google**.
   - *Firestore Database* → create the database.
   - *Cloud Messaging* (enabled by default).

4. **Paste your Firebase configuration** into `app/google-services-example.json` (download it from *Project settings* → *Your apps* → `google-services.json`).

5. **Copy that file as `app/google-services.json`.** The Google Services plugin only reads that name, and it is in `.gitignore` so your credentials are not pushed to the repository.

   ```bash
   cp app/google-services-example.json app/google-services.json
   ```

6. **Add the Web Client ID** to `local.properties` (in the project root). You can find it under *Authentication* → *Sign-in method* → *Google* → *Web SDK configuration*, or in Google Cloud Console as an OAuth client of type "Web":

   ```properties
   WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
   ```

7. **(Optional) Deploy the Cloud Functions** so push notifications are delivered (requires the Firebase Blaze plan):

   ```bash
   firebase use <your-project-id>
   cd functions
   npm install
   npm run deploy
   ```

   If you use a different project, also update `.firebaserc`.

8. **Sync Gradle and run the app** on a device or emulator. Accept the calendar and notification permissions when the app asks for them.

## Tests

```bash
./gradlew test                  # unit tests
./gradlew connectedAndroidTest  # instrumented tests (requires a device/emulator)
```

## Project structure

```
app/src/main/java/com/example/teamsync/
├── data/          # models, repositories (Firestore/Auth) and calendar sync
├── ui/            # screens, ViewModels, navigation and Compose components
├── MainActivity.kt
└── MyFirebaseMessagingService.kt   # receives push notifications
functions/         # Cloud Functions (TypeScript) that send the notifications
```
