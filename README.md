# StackOverflow Home Assignment

An Android **SDK** that retrieves the latest StackOverflow questions, built for the Callsign
**[Mobile Engineer] Home Assignment**, plus a sample app that consumes it.

The assignment asks for an app listing the latest StackOverflow questions, with an "unanswered"
filter and a detail view, where **the retrieval logic is extracted into a library module** that any
app can reuse. This project puts the weight on that library: a small, stable, hard-to-misuse SDK.

> **Status.** The SDK (`:stackoverflowsdk` + `:api` + `:domain` + `:data`) and the sample app
> (list with "unanswered" filter, detail screen, deep link, `mock`/`real` flavors) are implemented,
> with 123 unit/UI tests. It was also checked by hand on an Android emulator (list on the `real`
> flavor, detail through the deep link on `mock`), but **not on a physical device**, see
> [known limitations](docs/roadmap-and-limitations.md#known-limitations).

---

## Highlights

- **SDK first**: the app only depends on one artifact, `:stackoverflowsdk`.
- **Minimal public surface**: one entry point, one config class, one repository interface, one
  model and one result type. Everything else is `internal`.
- **Never throws across the SDK boundary**: every failure is a `Failure` with one `SdkError` type, shared by every result.
- **Explicit API mode** (`explicitApi()`): the compiler forces every public declaration to be
  deliberate.
- **Multi-module Gradle project**, no convention plugins (kept simple on purpose for a small project).
- **Configure once**: `StackOverflowSdk.initialize(config)` in `Application`, cheap and thread-safe,
  with a `warmUp()` the host can run off the main thread. Using the SDK without it is safe.
- **Optional logging**: off by default, one switch (`loggingEnabled`) that writes to Logcat; the
  log lines never contain content.
- **Paged list inside the SDK**: `QuestionsPager` exposes a `StateFlow`, joins pages, fixes the date window
  (snapshot) so nothing shifts while scrolling, serves one request at a time and keeps the list on failure.
- **Explicit timeouts** and **coroutine-cancellation aware**: slow or missing networks fail fast
  as `SdkError.Network`, cancelled requests stop cleanly.
- **Unit tests**: `MockWebServer` + Robolectric for the networking layer, plain JUnit elsewhere.
- **QA-ready**: stable test tags on the key elements, exposed to UiAutomator/Appium as `resource-id`
  in debug builds, plus a `mock` flavor with fixed data for deterministic end-to-end tests
  ([docs/test-tags.md](docs/test-tags.md)).
- **Sample app** with the same modular layout as a production app (`feature:*`, `core:*`), Hilt,
  immutable UI state, `mock`/`real` flavors, a deep link and Compose UI tests that run on the JVM.

---

> [!WARNING]
> **Retrofit/OkHttp are hidden at compile time, not at runtime.**
>
> `:stackoverflowsdk:data` declares Retrofit, OkHttp and kotlinx.serialization with
> `implementation`, so host apps cannot import them. But at *runtime* Gradle resolves each library
> to a **single version, the highest one requested by anyone**: if the host app uses another OkHttp
> or Retrofit version, the SDK runs against the host's, and a binary-incompatible change can crash
> **inside the host app**.
>
> Read the trade-off and what I would do in production: [docs/dependency-conflicts.md](docs/dependency-conflicts.md).

---

## Documentation

| Document | What is in it |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Modules and dependencies, request flow, error mapping, design patterns |
| [docs/sdk-api.md](docs/sdk-api.md) | Public API, initialization rules, logging, coroutine cancellation, design decisions |
| [docs/dependency-conflicts.md](docs/dependency-conflicts.md) | The main trade-off of using Retrofit, and the alternatives |
| [docs/stackexchange-api.md](docs/stackexchange-api.md) | The Stack Exchange endpoints used and the quirks of the API |
| [docs/sample-app.md](docs/sample-app.md) | The sample app: features, modules, use cases and how paging works, flavors, deep link, Compose practices |
| [docs/testing.md](docs/testing.md) | **Test strategy, tools, how to run, coverage** |
| [docs/test-tags.md](docs/test-tags.md) | Test tags for Compose tests and Appium/UiAutomator, how they are exposed, the full list |
| [docs/r8-proguard.md](docs/r8-proguard.md) | R8 / ProGuard: why the SDK ships no rules, how it was checked on a minified app |
| [docs/edge-cases.md](docs/edge-cases.md) | What happens in unusual situations, and the test that covers each |
| [docs/roadmap-and-limitations.md](docs/roadmap-and-limitations.md) | What is done, what I would add, known limitations |
| [docs/how-this-was-built.md](docs/how-this-was-built.md) | The step-by-step, AI-assisted approach and the decisions along the way |

---

## Quick start

**Requirements**

- Android Studio (recent stable)
- JDK 17+ to run Gradle. The build compiles with a JDK 17 toolchain and downloads it automatically
  (foojay resolver) if it is not installed.

**Build, install and test**

```bash
./gradlew :app:installMockDebug     # in-memory data, works offline
./gradlew :app:installRealDebug     # real Stack Exchange API

./gradlew :stackoverflowsdk:data:testDebugUnitTest # networking layer (MockWebServer + Robolectric)
./gradlew :stackoverflowsdk:api:test :stackoverflowsdk:testDebugUnitTest
./gradlew :core:designsystem:testDebugUnitTest :feature:home:testDebugUnitTest :feature:detail:testDebugUnitTest
```

See [docs/testing.md](docs/testing.md) for what each test suite covers and how to see the coverage.

**Try the deep link** from a terminal (with the app installed and an emulator or device connected):

```bash
# mock flavor: ids 1000..1019 exist, works offline
adb shell am start -a android.intent.action.VIEW -d "stackoverflowdemo://question/1000" \
  -p com.alexfin90.stackoverflowhomeassignment.mock

# real flavor: the id of an existing question (80007925 exists at the time of writing)
adb shell am start -a android.intent.action.VIEW -d "stackoverflowdemo://question/80007925" \
  -p com.alexfin90.stackoverflowhomeassignment
```

`-p` picks the flavor, because both register the same link. More in [docs/sample-app.md](docs/sample-app.md#deep-link).

---

## Project structure

```
StackOverFlow Home Assignment
├── app                       # Sample app: MainActivity, NavHost, Hilt, mock/real flavors
├── core
│   ├── common                # Type-safe routes and deep link base (pure JVM)
│   ├── domain                # Use cases over the SDK contract (pure JVM)
│   └── designsystem          # Theme, QuestionRow, filter chip, loading/error/empty states
├── feature
│   ├── home                  # Questions list, filter, paging (ViewModel + screen + tests)
│   └── detail                # Question detail, deep link target (ViewModel + screen + tests)
└── stackoverflowsdk          # Facade (Android library): StackOverflowSdk
    ├── api                   # Public configuration: StackOverflowConfig (pure JVM)
    ├── domain                # Question, QuestionsResult, QuestionsRepository (pure JVM)
    └── data                  # Retrofit service, DTOs, mapper, repository impl (Android library)
```

---

## Tech Stack

- **Language**: Kotlin `2.2.10`, JDK `17` (Gradle toolchain)
- **Build**: AGP `9.3.3`, Gradle version catalog (no convention plugins)
- **Networking**: Retrofit `3.0.0`, OkHttp `4.12.0` (explicit timeouts), kotlinx.serialization `1.10.0`
- **Async**: Kotlin Coroutines `1.10.2`
- **UI**: Jetpack Compose (BOM `2026.02.01`), Material 3, Navigation Compose `2.9.3` (type-safe routes),
  kotlinx-collections-immutable `0.4.0`
- **DI** (app only): Hilt `2.60.1` + KSP `2.3.12`, Hilt Navigation Compose `1.3.0`
- **Testing**: JUnit 4, Robolectric `4.14.1`, `MockWebServer`, `kotlinx-coroutines-test`
- **Android**: `minSdk` 24, `compileSdk` 37

---

## Notes

This repository is private, as required by the assignment.
