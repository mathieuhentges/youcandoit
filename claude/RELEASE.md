# Releasing "You Can Do It!" to the Play internal testing track

Package name (application ID): `mathieu.dev.youcandoit`, as registered in Play Console (permanent once uploaded).
The code keeps the `dev.mathieu.youcandoit` namespace; only the ID that Play and the phone see differs.
Requirements: a recent Android Studio (the project uses AGP 9.5); the Gradle 9.6 wrapper is included.

## 1. One-time: create your upload key

Run from the project root (keytool ships with Android Studio's JDK):

```
keytool -genkeypair -v -keystore upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Then copy `keystore.properties.example` to `keystore.properties` and fill in the passwords.
Both files are git-ignored. **Back them up** (password manager + a copy outside the repo).

With Play App Signing (the default), Google holds the key that signs what users install;
yours is only the *upload* key. If you lose it, Play support can reset it — but it takes days.

## 2. Build the release bundle

```
./gradlew test bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`.
Before every new upload, bump `versionCode` (+1) in `app/build.gradle.kts`, and `versionName` if you like.

Tip: install the release build on your phone once before uploading, since R8 (code shrinking)
only runs in release. In Android Studio: Build Variants → `release`, then Run.

## 2b. One-time: publish the privacy policy

Play Console needs a public URL. The page is ready at `docs/privacy-policy.html`.

1. Push the project to GitHub (the repo can be public, or private on a plan that allows Pages).
2. Repo → **Settings → Pages** → Source: **Deploy from a branch** → Branch: `main`, folder: `/docs` → **Save**.
3. After a minute or two the page is live at
   `https://<your-github-username>.github.io/<repo-name>/privacy-policy.html`.
   Open it in a browser to check, then paste that URL into Play Console.

Only the `docs/` folder is published as a website; the rest of the code isn't served
(though anyone can read it if the repo itself is public).

## 3. One-time: create the app in Play Console

1. Developer account: https://play.google.com/console (US$25 one-time, identity verification).
2. **Create app** → name "You Can Do It!", default language, App, Free.
3. **Test and release → Testing → Internal testing → Testers**: create an email list with your
   friends' Google account addresses (up to 100). Save.

## 4. Upload and roll out

1. **Internal testing → Create new release**.
2. Accept **Play App Signing** if asked.
3. Upload `app-release.aab`, add release notes (e.g. "First test version"), **Save → Review → Roll out**.
4. Play Console may first ask for a few app declarations. Answers are prepared in
   `claude/store/listing.md` (privacy policy, data safety, ads, content rating, target audience).
5. Copy the **opt-in link** from the Testers tab and send it to your friends.

## 5. What your friends do

1. Open the opt-in link with the Google account you added, tap **Accept invite**.
2. Tap the Play Store link on that page and install. Updates arrive through Play automatically.
   (It can take a little while after rollout before the app shows up for them.)

## Updating later

Change code → bump `versionCode` → `./gradlew test bundleRelease` → Internal testing →
Create new release → upload → roll out.

## Going public later (not planned yet)

New personal accounts need a **closed test with at least 12 testers opted in for 14 days in a row**
before production access. Internal testing doesn't count toward it — switch to the closed track then.
