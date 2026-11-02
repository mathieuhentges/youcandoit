# Run "You Can Do It!" locally

## What you need

- **Android Studio Narwhal 3 or newer** (the project uses Android Gradle Plugin 8.13).
  It bundles the JDK, so you don't need to install Java separately.
- **Android 16 (API 36) SDK platform.** Android Studio offers to install it on the first sync.
- **A phone with Android 8.0 or newer**, or an emulator.
- Internet on the first build: Gradle, the libraries, and Robolectric's Android runtime are downloaded once.

The Gradle wrapper (Gradle 8.13) is included, so you don't need Gradle installed either.

## 1. Open the project

1. Unzip `todo-app.zip` (or clone your repo).
2. Android Studio → **File → Open** → select the `todo-app` folder (the one with `settings.gradle.kts`).
3. Click **Trust Project**, then wait for **Gradle sync** to finish. The first sync takes a few minutes.
4. If Studio asks to install a missing SDK platform (Android 16 / API 36) or build tools, click the link to install, then sync again.

## 2. Run the tests

From the terminal at the project root:

```
./gradlew test          # macOS / Linux
gradlew.bat test        # Windows
```

This runs everything: the domain tests (JUnit 5) and the app tests (Room, ViewModels, reminders,
Compose UI) on the JVM, so no phone or emulator is needed. The first run is slower while Robolectric
downloads its Android runtime.

In Android Studio you can also right-click `domain/src/test` or `app/src/test` → **Run Tests**.
Test reports: `domain/build/reports/tests/` and `app/build/reports/tests/`.

## 3. Run the app

### On your phone

1. On the phone: **Settings → About phone** → tap **Build number** 7 times to enable Developer options.
2. **Settings → System → Developer options** → turn on **USB debugging**
   (or **Wireless debugging**, then pair it from Android Studio's device menu).
3. Plug the phone in and accept the "Allow USB debugging?" prompt.
4. In Android Studio, pick your phone in the device dropdown, the run configuration **app**, and click **Run ▶**.

### On an emulator

1. **Tools → Device Manager → +** → **Create Virtual Device** → pick a phone (e.g. Pixel 8)
   → a system image with **API 36** → Finish.
2. Select it in the device dropdown and click **Run ▶**.

### From the command line

```
./gradlew installDebug
```

Installs the debug build on the connected phone or running emulator; then open **Can Do It** from the app drawer.

## 4. Try the main features

- **Welcome**: the very first launch shows "You Can Do It, Jeanne!" with a **Let’s go** button.
  It appears only once; to see it again, clear the app's storage or reinstall.

- **Home**: tap **+**, add a task with a priority and a category, tick it, then **Archive done**.
- **Lists**: create, rename and delete categories; tap one to see only its tasks.
- **History**: archived tasks, done counts per period, restore.
- **Appearance**: moon button on Home → Follow phone / Light / Dark.
- **Reminders**: give a task a deadline 2–3 minutes away and pick **At deadline**.
  Allow notifications when asked. If the note "Reminders may arrive a few minutes late" appears,
  tap **Allow exact timing**. Lock the phone and wait.

## Troubleshooting

| Problem | Fix |
|---|---|
| "This version of the Android Gradle plugin requires a newer Android Studio" | Update Android Studio (Narwhal 3 or newer). |
| Gradle JDK errors | **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**: pick the bundled JetBrains Runtime (17 or newer). |
| "SDK location not found" when using the terminal | Open the project once in Android Studio (it writes `local.properties`), or create `local.properties` with `sdk.dir=/path/to/Android/sdk`. |
| `./gradlew: Permission denied` | `chmod +x gradlew` |
| Install fails with a signature or version conflict | Uninstall the old app from the phone, then run again. |
| The app crashes on launch after updating the code | The database changed shape: uninstall the app (or clear its storage) and reinstall. |
| Reminders never show | Check the app's notification permission, and that battery saver isn't restricting the app. |
| Anything else | Copy the full error from the **Build** window and send it to Claude. |

## Release builds

Building for Google Play needs an upload key: see [RELEASE.md](RELEASE.md).
Without `keystore.properties`, `./gradlew bundleRelease` still works but produces an unsigned bundle.
