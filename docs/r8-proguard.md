[← Back to the README](../README.md)

# R8 / ProGuard

What happens when a host app that minifies (R8) uses the SDK, and what the SDK ships to make it work.

**Short answer: the SDK ships no active rules, and the sample app works minified.** The file
`stackoverflowsdk/consumer-rules.keep` is packaged in the AAR (`proguard.txt`) and applied to the host,
but it only documents this decision.

## Why no rules are needed

- **The libraries bring their own.** kotlinx.serialization ships `kotlinx-serialization-common.pro`,
  which R8 reads automatically and which covers the serializers of our `@Serializable` DTOs. The HTTP
  client is the platform's `HttpURLConnection`, so there is nothing to keep for it. (When the SDK used
  Retrofit + OkHttp, their jars also brought their own rules.)
- **Our own code needs none.** The DTOs and the data layer are `internal`: obfuscating them is fine.
  Nothing in the SDK loads a class by name.

## Why the public API is not kept

A blanket `-keep` on the public API is common and tempting (readable names, safe for reflection). It is
not used here because it **stops the host from shrinking** what it does not use, and a small binary is
one of the goals of an SDK. R8 already removes the unused classes (in the test below the unused
public classes were gone from the mapping). Readable crash traces are the host's `mapping.txt` job.

If a wrapper (React Native, Flutter, Cordova) calls the SDK **by reflection**, the wrapper must keep
what it calls in its own consumer rules: it is the one that knows which names it uses.

## How it was checked

> The check below was done when the SDK still used Retrofit + OkHttp. After the switch to
> `HttpURLConnection` it should be repeated with the steps in "How to check it again".

Not assumed: an app was built with R8 enabled and **no rules of our own**, installed on an emulator and used.

| Path in the minified `real` release | Result |
|---|---|
| List from the real API (Retrofit + serialization of the DTOs, at the time of the check) | 14 questions shown |
| "Unanswered" filter | only "0 answers" |
| Open a question (a request with a path parameter, DTO deserialized) | detail shown |
| Deep link `stackoverflowdemo://question/{id}` | detail of a real question |
| Offline at launch, then Retry | error message, then the list |
| Crashes | none |

R8 did rename some public classes that the app uses (for example `Question` and `QuestionsResult`),
which is expected and harmless.

### How to check it again

Temporarily, in `app/build.gradle.kts`, enable R8 on `release` and sign it with the debug key so it can
be installed:

```kotlin
release {
    optimization { enable = true }
    signingConfig = signingConfigs.getByName("debug")
}
```

```bash
./gradlew :app:assembleRealRelease
adb install -r app/build/outputs/apk/real/release/app-real-release.apk
```

To check the deep link on the minified build, fire it from a terminal (the id must be a question that exists; the
other paths are used by hand in the app):

```bash
adb shell am start -a android.intent.action.VIEW -d "stackoverflowdemo://question/80007925" \
  -p com.alexfin90.stackoverflowhomeassignment
```

Use the paths in the table above and watch `adb logcat` for `FATAL EXCEPTION`, `SerializationException`
or `Unable to create converter`. R8 writes `app/build/outputs/mapping/realRelease/mapping.txt` to see what
was renamed. Revert the change afterwards: the sample app's release build keeps R8 disabled.

## When to add a rule

Only for something that breaks in a minified app, for example a class the SDK would load by name. Put the
rule in `consumer-rules.keep` with a comment saying what breaks without it, and add a row to the table above.

## Limits

- Checked with the Android Gradle Plugin 9 default (R8 in full mode) and the versions of this project.
  Another host with another R8 configuration or other versions of kotlinx.serialization
  is not covered (see [dependency conflicts](dependency-conflicts.md)).
- Checked by hand on an emulator, not with an automated test.
