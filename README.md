# StackOverflow Home Assignment

An Android **SDK** that retrieves the latest StackOverflow questions, built for the Callsign
**[Mobile Engineer] Home Assignment**, plus a sample app that consumes it.

The assignment asks for an app listing the latest StackOverflow questions, with an "unanswered"
filter and a detail view, where **the retrieval logic is extracted into a library module** that any
app can reuse. This project puts the weight on that library: a small, stable, hard-to-misuse SDK.

> **Status.** The SDK (`:stackoverflowsdk` + `:domain` + `:data`) is implemented and tested. The
> sample app screens are in progress, see [Roadmap](#roadmap).

---

## Highlights

- **SDK first**: the app only depends on one artifact, `:stackoverflowsdk`.
- **Minimal public surface**: one entry point, one config class, one repository interface, one
  model and one result type. Everything else is `internal`.
- **Never throws across the SDK boundary**: every failure is a `QuestionsResult` case.
- **Explicit API mode** (`explicitApi()`): the compiler forces every public declaration to be
  deliberate.
- **Multi-module Gradle project**, no convention plugins (kept simple on purpose for a small project).
- **Unit tests** with `MockWebServer` for the networking layer.

---

## Dependency conflicts: the main trade-off

> [!WARNING]
> **Retrofit/OkHttp are hidden at compile time, not at runtime.**
>
> In `:stackoverflowsdk:data`, Retrofit, OkHttp and kotlinx.serialization are declared with
> `implementation`, so host apps **cannot import or accidentally use them**, and changing them is
> not a breaking change for the public API.
>
> **But** that only affects the *compile classpath*. At *runtime* the SDK's transitive dependencies
> end up in the host app's dependency graph, and **Gradle resolves each library to a single
> version, the highest one requested by anyone**. If the host app uses a different OkHttp or
> Retrofit version, the SDK runs against *the host's resolved version*, not the one it was built
> and tested with. A binary-incompatible change between the two can crash **inside the host app**.

This is a real risk for any SDK that embeds third-party libraries, and it is the main reason an
SDK should keep its dependency set small.

**Why I still use Retrofit here.** It is the de-facto standard, the assignment allows third-party
libraries, and the risk is contained:

- Retrofit/OkHttp never appear in a public signature, so the exposure is limited to the runtime
  version resolution described above.
- All network access sits behind a single internal service (`StackExchangeService`) and the
  `QuestionsRepository` contract, so the HTTP stack can be replaced without touching consumers.
- The facade module declares **no unnecessary dependencies** (no AppCompat, Material, ...).

**What I would do in production** (not done here to keep the scope reasonable):

| Option | Idea | Cost |
|---|---|---|
| No third-party HTTP stack | `HttpURLConnection` + `org.json` / minimal parser, only platform APIs | More code to own and test |
| Relocation (shading) | Repackage OkHttp/Retrofit under the SDK's own package at build time so the host's version can never clash | Build complexity, larger binary, not trivial for AARs |
| Pin and document | Publish the exact tested versions and the supported range | Pushes work to integrators |
| Compatibility tests | CI matrix that builds a sample host against several OkHttp/Retrofit versions | CI time |

---

## Architecture

```
┌──────────────────────────── Host app ────────────────────────────┐
│  :app  (sample consumer)                                         │
└───────────────────────────────┬──────────────────────────────────┘
                                │ implementation
                                ▼
┌──────────────────────── :stackoverflowsdk ───────────────────────┐
│  Facade: StackOverflowSdk, StackOverflowConfig                   │
│  api → :domain          implementation → :data                   │
└───────────────┬──────────────────────────────────┬───────────────┘
                │ api                              │ implementation
                ▼                                  ▼
┌──────────── :domain ────────────┐   ┌──────────── :data ─────────────┐
│ Question, QuestionsResult,      │◄──│ Retrofit service, DTOs, mapper,│
│ QuestionsRepository             │   │ QuestionsRepositoryImpl        │
│ (pure Kotlin/JVM, no deps)      │   │ (pure Kotlin/JVM)              │
└─────────────────────────────────┘   └────────────────────────────────┘
```

- `:domain` is pure Kotlin/JVM with **zero third-party dependencies**. The repository contract only
  uses `suspend`, so it does not even expose coroutines.
- `:data` hides networking. Its only public symbol is `createQuestionsRepository()`.
- `:stackoverflowsdk` is the only module apps declare. It re-exports `:domain` with `api(...)`, so
  integrators get the models without declaring `:domain` themselves, and keeps `:data` as
  `implementation`, so integrators cannot see it.
- Rule: no `:data` type may appear in a public signature of the facade, otherwise `implementation`
  would not be enough.

### Why `@InternalStackOverflowApi`?

Kotlin's `internal` is scoped to a **Gradle module**, so the facade cannot see `internal`
declarations of `:data`. The one symbol it needs, `createQuestionsRepository()`, must therefore be
`public`. It is annotated with `@InternalStackOverflowApi` (`@RequiresOptIn`, level `ERROR`): the
facade opts in explicitly, while a host app calling it directly gets a **compile error**. It is
public for technical reasons only and is not part of the supported API.

---

## Public API

```kotlin
// Add to your app
implementation(project(":stackoverflowsdk"))

val questions: QuestionsRepository = StackOverflowSdk.create()   // no I/O, no Context

when (val result = questions.getLatestQuestions(unansweredOnly = true, page = 1)) {
    is QuestionsResult.Success      -> show(result.questions, canLoadMore = result.hasMore)
    is QuestionsResult.NetworkError -> showOffline(result.message)
    is QuestionsResult.ApiError     -> showError(result.httpCode, result.message)
}
```

| Type | Role |
|---|---|
| `StackOverflowSdk.create(config)` | Single entry point. Cheap, does no I/O. |
| `StackOverflowConfig` | `baseUrl`, `pageSize` (1..100), `lastDays` (default 30). Validated at construction. |
| `QuestionsRepository` | `suspend fun getLatestQuestions(unansweredOnly, page): QuestionsResult` |
| `QuestionsResult` | Sealed: `Success(questions, hasMore)` / `NetworkError` / `ApiError(httpCode?, message)` |
| `Question` | Plain domain model, independent from the wire format. Title is already HTML-decoded. |

### Design decisions

- **Errors are values, not exceptions.** An exception thrown by the SDK would show up as a crash of
  the *host* app. The repository catches everything (including the unexpected) and maps it to a
  `QuestionsResult`. The only exception rethrown is `CancellationException`, to respect structured
  concurrency.
- **No `Context`, no initialization step.** Nothing blocks the host's startup or leaks an
  `Activity`.
- **Config validated eagerly.** An invalid configuration is a programming error in the host, so it
  fails fast with a clear message instead of failing at the first request.
- **Unanswered filter uses the dedicated endpoint** (`/questions/no-answers`) so the filtering is
  done by the server and pagination stays correct.
- **No DI framework inside the SDK.** Hilt/Dagger would be imposed on the host (annotation
  processing, application class, version conflicts). Testability comes from plain constructor
  injection (`QuestionsRepositoryImpl` receives its service). Hilt is a good fit for the *sample
  app*, where it will be used.
- **`minSdk` 24**, to keep the SDK integrable by apps with a low minimum.
- **INTERNET permission** is declared in the SDK manifest and merged into the host app.

---

## Module Structure

```
StackOverFlow Home Assignment
├── app                       # Sample consumer app (Compose)
└── stackoverflowsdk          # Facade (Android library): StackOverflowSdk, StackOverflowConfig
    ├── domain                # Question, QuestionsResult, QuestionsRepository (pure JVM)
    └── data                  # Retrofit service, DTOs, mapper, repository impl (pure JVM)
```

---

## Tech Stack

- **Language**: Kotlin `2.2.10`, JDK `17` (Gradle toolchain)
- **Build**: AGP `9.3.3`, Gradle version catalog (no convention plugins)
- **Networking**: Retrofit `3.0.0` (brings OkHttp `4.12.0`), kotlinx.serialization `1.10.0`
- **Async**: Kotlin Coroutines `1.10.2`
- **Testing**: JUnit 4, `MockWebServer`, `kotlinx-coroutines-test`
- **Android**: `minSdk` 24, `compileSdk` 37

---

## Running the Project

### Requirements

- Android Studio (recent stable)
- JDK 17+ to run Gradle. The build compiles with a JDK 17 toolchain and downloads it automatically
  (foojay resolver) if it is not installed.

### Build and test

```bash
./gradlew :app:assembleDebug                 # build the sample app (and the SDK)
./gradlew :stackoverflowsdk:data:test        # networking layer tests (MockWebServer)
./gradlew :stackoverflowsdk:testDebugUnitTest # facade/config tests
```

---

## Testing

- **Mapper**: HTML entity decoding (numeric, hex, named, no double decoding), DTO to domain mapping.
- **Repository** (against `MockWebServer`): success mapping, correct endpoint and query parameters
  (30-day window, paging, `no-answers` for the filter), HTTP error, malformed body, connection
  failure.
- **Config**: defaults and validation.

---

## Roadmap

Work is delivered step by step through pull requests.

- [x] Module structure (facade, domain, data), explicit API mode
- [x] Domain models, result type and repository contract
- [x] Data layer: Retrofit service, DTOs, mapper, repository, tests
- [x] Facade (`StackOverflowSdk`, `StackOverflowConfig`) and README
- [ ] Sample app: list screen with "unanswered" filter and loading state, detail screen
      (Compose, ViewModel, Hilt)
- [ ] App tests: ViewModel unit tests, one happy-path and one unhappy-path UI test
- [ ] Split the app into `feature:home` and `feature:detail` modules

### What I would add with more time (SDK)

- Pluggable HTTP layer / relocation to remove the runtime dependency risk described above
- Response caching and retry with backoff for transient failures
- Structured logging with a session id and PII-free diagnostics hook
- Binary-compatibility validation (`binary-compatibility-validator`) and a published changelog
  with semantic versioning and a deprecation policy
- `Flow`-based API for paging/refresh on top of the current `suspend` contract

---

## Notes

This repository is private, as required by the assignment.
