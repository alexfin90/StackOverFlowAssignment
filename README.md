# StackOverflow Home Assignment

An Android **SDK** that retrieves the latest StackOverflow questions, built for the Callsign
**[Mobile Engineer] Home Assignment**, plus a sample app that consumes it.

The assignment asks for an app listing the latest StackOverflow questions, with an "unanswered"
filter and a detail view, where **the retrieval logic is extracted into a library module** that any
app can reuse. This project puts the weight on that library: a small, stable, hard-to-misuse SDK.

> **Status.** The SDK (`:stackoverflowsdk` + `:api` + `:domain` + `:data`) and the sample app
> (list with "unanswered" filter, detail screen, deep link, `mock`/`real` flavors) are implemented,
> with 94 unit/UI tests. It was also checked by hand on an Android emulator (list on the `real`
> flavor, detail through the deep link on `mock`), but **not on a physical device**, see
> [Known limitations](#known-limitations).

---

## Highlights

- **SDK first**: the app only depends on one artifact, `:stackoverflowsdk`.
- **Minimal public surface**: one entry point, one config class, one repository interface, one
  model and one result type. Everything else is `internal`.
- **Never throws across the SDK boundary**: every failure is a `QuestionsResult` case.
- **Explicit API mode** (`explicitApi()`): the compiler forces every public declaration to be
  deliberate.
- **Multi-module Gradle project**, no convention plugins (kept simple on purpose for a small project).
- **Configure once**: `StackOverflowSdk.initialize(config)` in `Application`, cheap and thread-safe,
  with a `warmUp()` the host can run off the main thread. Using the SDK without it is safe.
- **Optional logging**: off by default, one switch (`loggingEnabled`) that writes to Logcat; the
  log lines never contain content.
- **Explicit timeouts** and **coroutine-cancellation aware**: slow or missing networks fail fast
  as `NetworkError`, cancelled requests stop cleanly.
- **Unit tests**: `MockWebServer` + Robolectric for the networking layer, plain JUnit elsewhere.
- **Sample app** with the same modular layout as a production app (`feature:*`, `core:*`), Hilt,
  immutable UI state, `mock`/`real` flavors, a deep link and Compose UI tests that run on the JVM.

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

### Module dependencies

```mermaid
flowchart TD
    app[":app<br/>sample consumer"]
    sdk[":stackoverflowsdk<br/>facade: StackOverflowSdk"]
    api[":api<br/>StackOverflowConfig<br/>pure Kotlin/JVM"]
    domain[":domain<br/>Question, QuestionsResult,<br/>QuestionsRepository<br/>pure Kotlin/JVM, no deps"]
    data[":data<br/>Retrofit, DTOs, mapper,<br/>NetworkModule, repository impl<br/>Android library"]

    app -->|implementation| sdk
    sdk -->|api| api
    sdk -->|api| domain
    sdk ==>|"implementation (must NOT be api)"| data
    data -->|api| domain

    classDef hidden stroke-width:3px,stroke-dasharray: 5 3
    class data hidden
```

`api` edges are visible to the host app, `implementation` edges are not. The thick edge is the one
the IMPORTANT note below is about.

### Request flow

```mermaid
sequenceDiagram
    participant App as Host app
    participant Sdk as StackOverflowSdk
    participant Repo as QuestionsRepositoryImpl
    participant Remote as RetrofitQuestionsRemoteDataSource
    participant Http as Retrofit / OkHttp
    participant So as Stack Exchange API

    App->>Sdk: initialize(config) once, warmUp() off the main thread
    App->>Sdk: questions
    Sdk-->>App: QuestionsRepository (shared, lazy)
    App->>Repo: getLatestQuestions(unansweredOnly, page)
    Note over Repo: fromdate = now - lastDays (seconds)
    Repo->>Remote: getLatestQuestions(fromDate, page, pageSize, unansweredOnly)
    Remote->>Http: GET /2.3/questions or /questions/no-answers
    Http->>So: HTTP request (connect + call timeouts)
    alt 2xx and valid JSON
        So-->>Http: items, has_more
        Http-->>Remote: DTOs
        Remote-->>Repo: RemoteResult.Success
        Repo-->>App: Success(questions, hasMore)
    else HTTP 4xx/5xx or unreadable body
        Remote-->>Repo: Failure.Http / Failure.Malformed
        Repo-->>App: ApiError(httpCode?, message)
    else offline, DNS failure, timeout
        Remote-->>Repo: Failure.Network
        Repo-->>App: NetworkError(message)
    end
```

### Error mapping

Two steps, each with one responsibility. The data source is the only place that knows Retrofit's
exceptions; the repository only knows `RemoteResult`.

```mermaid
flowchart LR
    ex{{"exception in the request"}}
    ex -->|CancellationException| rethrow["rethrown<br/>(coroutine cancelled)"]
    ex -->|HttpException| h["Failure.Http"]
    ex -->|"IOException<br/>(UnknownHost, Connect, SocketTimeout)"| n["Failure.Network"]
    ex -->|SerializationException| m["Failure.Malformed"]
    ex -->|"any other Exception"| u["Failure.Unexpected"]
    h --> api1["ApiError(httpCode)"]
    n --> net["NetworkError"]
    m --> api2["ApiError(null)"]
    u --> api3["ApiError(null)"]
```

Only the first case leaves the SDK as an exception, and it is handled by the coroutines runtime.

- `:api` holds the public configuration (`StackOverflowConfig`), pure Kotlin/JVM.
- `:domain` is pure Kotlin/JVM with **zero third-party dependencies**. The repository contract only
  uses `suspend`, so it does not even expose coroutines.
- `:data` hides networking, in three small layers: `QuestionsRepositoryImpl` (public results) →
  `QuestionsRemoteDataSource` (an interface; transport failures as `RemoteResult`) → the Retrofit
  service. The repository does not import Retrofit, so the HTTP stack can be swapped or faked
  without touching it. Its only public symbol is `createQuestionsRepository()`.
- `:stackoverflowsdk` is the only module apps declare. It re-exports `:domain` with `api(...)`, so
  integrators get the models without declaring `:domain` themselves, and keeps `:data` as
  `implementation`, so integrators cannot see it.
- Rule: no `:data` type may appear in a public signature of the facade, otherwise `implementation`
  would not be enough.

> [!IMPORTANT]
> **`:data` must stay `implementation` in `:stackoverflowsdk/build.gradle.kts`, never `api`.**
> That single keyword is what keeps Retrofit, OkHttp, the DTOs and `createQuestionsRepository()`
> invisible to host apps. There is no opt-in marker or other safeguard behind it: if it were
> changed to `api(...)`, they would silently become part of the public API (and a breaking change
> every time they change). A comment in the build file warns about it, and a code review should
> reject any change to this line.

### Why is `createQuestionsRepository()` public?

Kotlin's `internal` is scoped to a **Gradle module**, so the facade cannot see `internal`
declarations of `:data`. The one symbol it needs, `createQuestionsRepository()`, must therefore be
`public`. A host app still cannot reach it: `:stackoverflowsdk` declares `:data` with
`implementation`, so the module is not on the app's compile classpath. The KDoc states that it is
not part of the supported API.

> A `@RequiresOptIn` marker would add a second layer of protection (useful if `:data` were ever
> exposed with `api(...)` or published as its own artifact). It was left out on purpose to keep the
> public surface and the amount of concepts small.

### Design patterns

Where each pattern is used in the SDK, and why. Only things that exist in the code are listed, and
they are labelled for what they are.

**Design patterns (GoF)**

| Pattern | Where | Why |
|---|---|---|
| **Facade** | `StackOverflowSdk` | One simple, stable entry point in front of Retrofit, DTOs, mappers and the HTTP client |
| **Singleton** (with limits) | `StackOverflowSdk` (`object`) | Configure once at startup (`initialize`) and share one client |
| **Decorator** | `LoggingQuestionsRepository` | Add logging around the repository without touching it, and only when enabled |

**Architectural patterns**

| Pattern | Where | Why |
|---|---|---|
| **Repository** + remote **data source** | `QuestionsRepositoryImpl` → `QuestionsRemoteDataSource` | The repository maps outcomes to public results; the data source owns the transport |
| **Dependency injection** by constructor, with a **composition root** | `createQuestionsRepository`, `NetworkModule` | The one place where the object graph is assembled, with no DI framework in the SDK |

**Idioms, not patterns:** sealed result types (`QuestionsResult`, `RemoteResult`) make errors values
the caller must handle instead of exceptions; mapping functions (`QuestionDto.toDomain()`) keep
the wire format out of the public model.

**Facade.** The SDK has several moving parts (Retrofit service, DTOs, mappers, HTTP client,
repository). The *facade* pattern hides them behind one simple, stable entry point,
`StackOverflowSdk`, which hands out a `QuestionsRepository`. The host app does not know what is
behind it, so internals can change (even the whole HTTP stack) without breaking integrators. For an
SDK this matters twice: it keeps the public surface minimal, and it gives one place to enforce
rules (validated config, no exceptions across the boundary).

**Singleton, with a conscious trade-off.** `StackOverflowSdk` is a Kotlin `object`: one well-known
access point that needs no instance and no `Context`, which is how most SDKs are configured once at
startup. Since `initialize` and the shared `questions` property exist, **it now holds state**, and that has
a price: global mutable state is harder to test and allows one configuration per process. How it is
contained:

- The shared state is tiny (the configuration and one repository) and guarded by a lock.
- `initialize` is **once-only**: later or late calls are ignored (and return `false`), so a running
  SDK never changes underneath its callers.
- Using `StackOverflowSdk.questions` without `initialize` falls back to the defaults, it never throws.
- `create(config)` still returns an **independent instance** ignoring the shared state: use it in
  tests, for several configurations, or with your own DI container (the sample app does this
  through Hilt).
- Tests reset the shared state through an `internal` function that is not part of the public API.

**Decorator.** `LoggingQuestionsRepository` is not a subclass of the real repository: it
**wraps** it. Both implement `QuestionsRepository`; the decorator holds the real one as `delegate`,
calls it, writes one log line and returns the same result. The caller cannot tell the difference.

```mermaid
classDiagram
    class QuestionsRepository {
        <<interface>>
        +getLatestQuestions(unansweredOnly, page) QuestionsResult
    }
    class QuestionsRepositoryImpl
    class LoggingQuestionsRepository
    class QuestionsRemoteDataSource {
        <<interface>>
    }

    QuestionsRepository <|.. QuestionsRepositoryImpl
    QuestionsRepository <|.. LoggingQuestionsRepository
    LoggingQuestionsRepository o-- QuestionsRepository : delegate
    QuestionsRepositoryImpl --> QuestionsRemoteDataSource
```

The factory builds it only when `loggingEnabled` is `true`:
`LoggingQuestionsRepository(QuestionsRepositoryImpl(...), sink)`. Why a decorator and not logging
inside the repository or a subclass:

- **One responsibility per class**: the repository maps results and knows nothing about logging.
- **Zero cost when off**: with logging disabled the decorator is never created.
- **Testable alone**: its tests use a fake repository, no network.
- **No inheritance**: a subclass would depend on the base class internals and force it to be `open`.

The price: every new method of `QuestionsRepository` must also be added to the decorator. The
compiler enforces it, so it cannot be forgotten.

---

## Public API

```kotlin
// Add to your app
implementation(project(":stackoverflowsdk"))

// import com.alexfin90.stackoverflowsdk.StackOverflowSdk
// import com.alexfin90.stackoverflowsdk.api.StackOverflowConfig
// 1. Application.onCreate: configure once. Cheap, no I/O, no extra thread.
StackOverflowSdk.initialize(StackOverflowConfig(callTimeoutSeconds = 20, loggingEnabled = BuildConfig.DEBUG))
// 2. Optional: build the HTTP stack off the main thread so the first request is not slower.
applicationScope.launch { StackOverflowSdk.warmUp() }

// 3. Anywhere:
when (val result = StackOverflowSdk.questions.getLatestQuestions(unansweredOnly = true, page = 1)) {
    is QuestionsResult.Success      -> show(result.questions, canLoadMore = result.hasMore)
    is QuestionsResult.NetworkError -> showOffline(result.message)
    is QuestionsResult.ApiError     -> showError(result.httpCode, result.message)
}
```

`initialize` and `warmUp` are optional: `StackOverflowSdk.questions` works with the defaults. If you
prefer to own the instance (tests, DI container, several configurations), use
`StackOverflowSdk.create(config)` instead.

| Type | Role |
|---|---|
| `StackOverflowSdk.initialize(config)` | Sets the configuration once. Cheap. Returns `true` if applied, `false` if ignored (already initialized or already in use). |
| `StackOverflowSdk.questions` | Shared `QuestionsRepository`, built lazily and thread-safe. Uses the defaults if `initialize` was never called. |
| `StackOverflowSdk.warmUp()` | Builds the shared repository now. Idempotent. Call it **off the main thread**: the SDK creates no threads of its own. |
| `StackOverflowSdk.create(config)` | Independent repository, ignores `initialize`. Keep and reuse the instance. |
| `StackOverflowConfig` | `baseUrl`, `pageSize` (1..100), `lastDays` (30), `connectTimeoutSeconds` (10), `callTimeoutSeconds` (30), `loggingEnabled` (false). Validated at construction. |
| `QuestionsRepository` | `suspend fun getLatestQuestions(unansweredOnly, page): QuestionsResult` and `suspend fun getQuestion(id): QuestionResult` |
| `QuestionsResult` | Sealed: `Success(questions, hasMore)` / `NetworkError` / `ApiError(httpCode?, message)` |
| `QuestionResult` | Same idea for one question: `Success(question)` / `NotFound` / `NetworkError` / `ApiError` |
| `Question` | Plain domain model, independent from the wire format. Title is already HTML-decoded. |

### Initialization rules

1. **Only the first `initialize` takes effect**, and only if the shared repository was not used yet.
   Any other call returns `false` and, if logging is enabled, logs a warning. The configuration of a
   running SDK never changes.
2. **Using the SDK without `initialize` is safe**: the defaults apply, nothing throws. The first
   access then freezes that default configuration.
3. **`initialize` does not block**: it validates and stores the configuration. The expensive part
   (OkHttp client, Retrofit) is built on first use or in `warmUp()`, on the caller's thread.

### Logging

Off by default: a library should be silent unless the host asks.

```kotlin
// Logcat, tag "StackOverflowSdk"
StackOverflowConfig(loggingEnabled = BuildConfig.DEBUG)
```

One line per request, for example
`getLatestQuestions(unansweredOnly=true, page=2) -> Success(items=20, hasMore=true) in 312 ms`.
`DEBUG` for success, `WARN` for no connection, `ERROR` for an API error.

- **What is logged**: the operation, its parameters, the kind of outcome, counts and duration.
- **What is never logged**: response bodies, titles, authors, or exception messages (they can echo
  server content). This is covered by a test.
- **Logging cannot break the SDK**: a failure while writing the line (e.g. Logcat unavailable) is
  swallowed.
- **No custom logger on purpose**: a switch is enough for the need, and a pluggable logger would be
  one more public type to keep stable. If a host needs to route the lines elsewhere (Timber, a file),
  an optional `logger` parameter can be added later without breaking anyone.
- Implemented as a decorator (`LoggingQuestionsRepository`) that is only created when logging is
  enabled, so the default path pays nothing and the repository stays free of logging code.

### Coroutine cancellation

`getLatestQuestions` is cancellable and rethrows `CancellationException` instead of converting it
to a result: swallowing it would leave a cancelled coroutine running. It can happen when:

- the user leaves the screen while the request is in flight and `viewModelScope` is cancelled;
- the user changes the filter and the previous job is cancelled (`collectLatest`, `Job.cancel()`);
- a `withTimeout` expires (`TimeoutCancellationException` is a subclass);
- a `LaunchedEffect` in Compose leaves the composition or its key changes.

It never crashes the host: the coroutines runtime handles it. It is the only exception that can
leave the SDK.

### Design decisions

- **Errors are values, not exceptions.** An exception thrown by the SDK would show up as a crash of
  the *host* app. The data source catches everything (including the unexpected) and turns it into
  a `RemoteResult`; the repository maps that to a `QuestionsResult`. The only exception rethrown is
  `CancellationException`, to respect structured concurrency.
- **No `Context`; initialization is optional and non-blocking.** `initialize` only stores a
  validated configuration, so nothing blocks the host's startup or leaks an `Activity`.
- **Config validated eagerly.** An invalid configuration is a programming error in the host, so it
  fails fast with a clear message instead of failing at the first request.
- **Unanswered filter uses the dedicated endpoint** (`/questions/no-answers`) so the filtering is
  done by the server and pagination stays correct.
- **No DI framework inside the SDK.** Hilt/Dagger would be imposed on the host (annotation
  processing, application class, version conflicts). Testability comes from plain constructor
  injection (`QuestionsRepositoryImpl` receives its data source, the data source receives the service). Hilt is a good fit for the *sample
  app*, where it will be used.
- **Timeouts are explicit.** Connect timeout (default 10 s) makes the SDK fail fast on a dead
  network; call timeout (default 30 s) caps the whole request. A timeout is an `IOException`, so it
  becomes `QuestionsResult.NetworkError`, same as offline or DNS failure (`UnknownHostException`).
- **`:data` is an Android library, `:domain` is pure JVM.** Only the contract needs to be
  framework-free. `:data` uses `Html.fromHtml` to decode HTML entities in titles; its tests run
  with Robolectric.
- **No offline cache.** Out of scope: the SDK stays stateless (a database would add dependencies
  and conflict risk). A cache would live in the app (e.g. Room as source of truth with a fallback
  on `NetworkError`) or as an optional OkHttp HTTP cache.
- **`minSdk` 24**, to keep the SDK integrable by apps with a low minimum.
- **INTERNET permission** is declared in the SDK manifest and merged into the host app.

---

## Stack Exchange API used

Base URL `https://api.stackexchange.com/` (v2.3). Both calls are `GET`, anonymous, and return JSON.

| Use case | Endpoint | Docs |
|---|---|---|
| Latest questions | `/2.3/questions` | [docs](https://api.stackexchange.com/docs/questions) |
| Unanswered questions (0 answers) | `/2.3/questions/no-answers` | [docs](https://api.stackexchange.com/docs/no-answer-questions) |
| One question by id (detail, deep link) | `/2.3/questions/{id}` | [docs](https://api.stackexchange.com/docs/questions-by-ids) |

| Query parameter | Value | Why |
|---|---|---|
| `site` | `stackoverflow` | Fixed, defines the target site |
| `sort` / `order` | `creation` / `desc` | Fixed, "latest" means newest first |
| `fromdate` | now minus `lastDays`, Unix time in **seconds** | Requirement: questions of the last 30 days. 1 day = 86,400 s |
| `page` / `pagesize` | from the caller / `StackOverflowConfig.pageSize` | Paging (`has_more` tells if another page exists) |

Fields read from each item: `question_id`, `title` (HTML-encoded, decoded by the SDK), `link`,
`creation_date`, `answer_count`, `score`, `owner.display_name`, `tags`. Unknown fields are ignored,
so API additions do not break the SDK.

Things to know about the API: anonymous requests are rate limited (the quota is small and shared
per IP), and an exhausted quota or a bad request comes back as an HTTP error with a JSON body,
which the SDK reports as `QuestionsResult.ApiError`. The `fromdate` window is recomputed on each
call, so it slides slightly between pages.

---

## Sample App

The app consumes the SDK like any third party would: through `StackOverflowSdk.initialize(...)` / `StackOverflowSdk.questions` and the
public contract only.

| Feature | Notes |
|---|---|
| Latest questions | Last 30 days, newest first, paginated (next page when scrolling near the end) |
| "Unanswered" filter | Chip; calls the dedicated `no-answers` endpoint, selection survives process death |
| Loading / empty / error | Spinner, empty message, network vs server error with **Retry** |
| Detail screen | Title, author, score, answers, tags; **Open in browser** button; loaded by id |
| Deep link | `stackoverflowdemo://question/{questionId}` opens the detail directly |

### Modules

```mermaid
flowchart TD
    app[":app<br/>MainActivity, NavHost, Hilt, flavors"]
    home[":feature:home<br/>list, filter, paging"]
    detail[":feature:detail<br/>detail, deep link"]
    ds[":core:designsystem<br/>theme, components"]
    common[":core:common<br/>type-safe routes"]
    sdk[":stackoverflowsdk<br/>the SDK"]

    app --> home
    app --> detail
    app --> common
    app --> sdk
    home --> ds
    home --> sdk
    detail --> ds
    detail --> common
    detail --> sdk
```

Feature modules never depend on each other: navigation between them lives in `:app`, using the
routes from `:core:common`.

### Product flavors: `mock` and `real`

The flavor is chosen at build time; each one has its own Hilt `SdkModule` in `src/mock` or
`src/real`, so there is no runtime branching.

| Flavor | `SdkModule` binds | Use it when |
|---|---|---|
| `real` | `StackOverflowSdk.questions` (SDK initialized and warmed up at startup, logging in debug) | you want the real Stack Exchange API |
| `mock` | `FakeQuestionsRepository` (in-memory data, 800 ms latency) | offline, demos, UI work. **The SDK networking stack is never built.** |

`mock` has the application id suffix `.mock`, so both can be installed side by side.
Each flavor also has its own `SdkInitializer`: `real` configures the SDK and warms it up off the main
thread, `mock` does nothing, so no SDK code runs at all.

### Deep link

`stackoverflowdemo://question/{questionId}` opens the detail screen directly. It is declared twice,
as usual: a manifest `intent-filter` (`VIEW`, `BROWSABLE`) and a type-safe
`navDeepLink<Route.QuestionDetail>`. It works because the detail loads **by id**
(`getQuestion(id)` in the SDK): a link carries an id, not a question.

**Try it (emulator or device with USB debugging):**

1. Install the app: `./gradlew :app:installMockDebug` (or `:app:installRealDebug`).
2. Fire the link with `adb`. On macOS `adb` is in `~/Library/Android/sdk/platform-tools/` if it is
   not on your `PATH`.

```bash
# mock flavor: ids 1000..1019 exist, works offline
adb shell am start -a android.intent.action.VIEW \
  -d "stackoverflowdemo://question/1000" \
  -p com.alexfin90.stackoverflowhomeassignment.mock

# real flavor: use any StackOverflow question id, e.g. the number in a question URL
adb shell am start -a android.intent.action.VIEW \
  -d "stackoverflowdemo://question/80007692" \
  -p com.alexfin90.stackoverflowhomeassignment
```

- `-p <package>` selects the app. **Both flavors register the same link**, so if both are installed
  and you omit `-p`, Android shows an app chooser. That is expected.
- Expected result: the detail screen of that question (title, author, answers, tags, "Open in
  browser"). An id that does not exist shows the "does not exist" state, a missing network shows
  the error state with **Retry**.
- To check that the link is registered: `adb shell pm query-activities --brief -a
  android.intent.action.VIEW -d "stackoverflowdemo://question/1000"` lists the matching apps.
- Pressing Back after opening the link directly leaves the app (the graph is flat, see
  [Known limitations](#known-limitations)).

---

### Compose and architecture practices

- **Immutable UI state**: one `StateFlow` per screen of an `@Immutable` state (`sealed interface`
  for loading / error / empty / content), `ImmutableList` instead of `List`.
- **`LazyColumn` with stable `key` and `contentType`**, so items are reused and recomposition stays
  local.
- **ViewModel contract (`@Stable`)**: each `*Route` depends on an interface
  (`HomeViewModelContract`, `DetailViewModelContract`), with `hiltViewModel<...>()` as default. A
  concrete ViewModel is reported as *unstable* by the Compose tooling (it has mutable properties) and
  a screen taking it cannot be skipped; the `@Stable` interface can, and a fake can be passed in
  previews and tests without Hilt. Checked with the Compose compiler report: `HomeRoute` is
  `restartable skippable`.
- **One `Scaffold` for the whole app**, as `Theme { App }` -> `AppContent` -> `NavHost`:
  `StackOverFlowApp` (stateful: owns the `NavHostController`, reads a `MainViewModelContract`) ->
  `StackOverFlowAppContent` (stateless: the **only** `Scaffold`, with the top bar) -> the `NavHost`
  in its content. The screens have no `Scaffold` or top bar of their own. The `MainViewModel` owns
  the state of the part common to every destination (title and back arrow of the top bar), driven
  by the current destination.
- **State hoisting**: a stateful `*Route` (Hilt ViewModel, `collectAsStateWithLifecycle`) wraps a
  stateless `*Screen` that only takes state and callbacks. That makes screens testable and
  previewable.
- **No `Context` in ViewModels**: formatting with string resources and plurals happens in the UI
  layer.
- **Stale responses cannot win**: changing the filter cancels the in-flight request.
- **Design system without strings or SDK types**: components receive ready-to-render models.
- **Feature modules depend on the SDK contract only**, never on `:data`.

---

## Module Structure

```
StackOverFlow Home Assignment
├── app                       # Sample app: MainActivity, NavHost, Hilt, mock/real flavors
├── core
│   ├── common                # Type-safe routes and deep link base (pure JVM)
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

## Running the Project

### Requirements

- Android Studio (recent stable)
- JDK 17+ to run Gradle. The build compiles with a JDK 17 toolchain and downloads it automatically
  (foojay resolver) if it is not installed.

### Build and test

```bash
./gradlew :app:installMockDebug     # in-memory data, works offline
./gradlew :app:installRealDebug     # real Stack Exchange API

./gradlew :stackoverflowsdk:data:testDebugUnitTest # networking layer (MockWebServer + Robolectric)
./gradlew :stackoverflowsdk:api:test :stackoverflowsdk:testDebugUnitTest
./gradlew :core:designsystem:testDebugUnitTest :feature:home:testDebugUnitTest :feature:detail:testDebugUnitTest
```

---

## Testing

- **Mapper**: HTML entity decoding (numeric, hex, named, no double decoding), DTO to domain mapping.
- **Remote data source** (against `MockWebServer`): endpoint and query parameters (30-day window,
  paging, `no-answers` for the filter), unknown fields ignored, HTTP error, malformed body,
  connection failure, unknown host.
- **Repository** (against a fake data source, no HTTP): DTO to domain mapping, the `fromdate` window
  and the parameters passed down, every failure kind mapped to the right result.
- **Wiring**: three end-to-end cases through the public factory.
- **Logging decorator**: exact line format, levels, no content in the lines, a failing sink and a
  cancellation do not break the request.
- **Config** (`:api`): defaults, logging off by default, validation.
- **Facade**: `initialize` applied once, ignored when repeated or late (with a warning), use without
  `initialize`, shared instance, `warmUp` idempotent, concurrent access builds one instance,
  `create()` independent.
- **App** (ViewModels and Compose UI): see below.

| Module | Tests | What |
|---|---|---|
| `:stackoverflowsdk:data` | 35 | mapper, remote data source, client (fake data source), logging decorator, end-to-end wiring, `getQuestion` |
| `:stackoverflowsdk:api` | 6 | config defaults and validation |
| `:stackoverflowsdk` | 12 | `initialize`, `warmUp`, shared instance, concurrency, `create()` |
| `:core:designsystem` | 7 | components (Compose UI, JVM), title alignment, top bar |
| `:feature:home` | 19 | 11 ViewModel + 6 Compose UI (happy and unhappy paths) + 2 stateful route |
| `:feature:detail` | 10 | 4 ViewModel + 4 Compose UI (happy and unhappy paths) + 2 stateful route |
| `:app` | 4 | `MainViewModel` (top bar state per destination) |

**UI tests run on the JVM** (Compose UI test + Robolectric), against the stateless screens. They
need no device or emulator, so they run in CI like unit tests. The trade-off: they do not exercise
real rendering, navigation or the Hilt graph end to end; instrumented tests would cover that.

### Why Robolectric?

Plain JVM unit tests run against `android.jar`, whose framework classes are **stubs**: calling them
fails with `Method ... not mocked`. [Robolectric](https://robolectric.org) is a test runner
(`@RunWith(RobolectricTestRunner::class)`) that replaces those stubs with real implementations of
the Android framework, so code that needs `Context`, `Bundle`, `Html`, ... runs on the JVM without
an emulator. It is declared with `testImplementation`, so **it is never shipped with the SDK**.

It is a common need in apps too (for example a ViewModel using `SavedStateHandle`, which relies on
`Bundle`). Here it is needed because the mapper uses `Html.fromHtml` to decode HTML entities in
titles, and the repository tests go through the mapper.

> [!NOTE]
> **Trade-off:** `Html.fromHtml` is more complete than a hand-written decoder, but it makes every
> test that touches the mapper run under Robolectric. Cost: slower test runs (about 37 s for the
> first run on my machine, as Robolectric downloads the Android jars) and network access on the
> very first run on a new machine. If this becomes a problem, the alternative is a small
> hand-written decoder in pure Kotlin (only the few entities the API emits), which lets `:data`
> go back to being a plain JVM module with plain JUnit tests.

---

## Roadmap

Delivered step by step through stacked pull requests.

- [x] Module structure (facade, api, domain, data), explicit API mode
- [x] Domain models, result type, repository contract and data layer with tests
- [x] Facade, config and README
- [x] SDK: `getQuestion(id)`
- [x] App: Hilt, `mock`/`real` flavors, design system, list + filter, detail + deep link
- [x] Unit tests and UI tests (happy and unhappy paths)

### What I would add with more time (SDK)

- Pluggable HTTP layer / relocation to remove the runtime dependency risk described above
- Response caching and retry with backoff for transient failures
- Structured logging with a session id and PII-free diagnostics hook
- Binary-compatibility validation (`binary-compatibility-validator`) and a published changelog
  with semantic versioning and a deprecation policy
- `Flow`-based API for paging/refresh on top of the current `suspend` contract

### What I would add with more time (app)

- Instrumented tests (Hilt end to end, navigation, deep link) and screenshot tests
- Synthetic back stack so that, after opening the deep link, Back goes to the list
- Show the question body (needs the API `withbody` filter) and answers
- Offline cache (Room as single source of truth) in the app, keeping the SDK stateless
- Paging 3 instead of the hand-written paging, if the list grows in complexity

---

## Edge cases

What happens in the unusual situations, and where it is covered.

### SDK

| Case | Behaviour | Test |
|---|---|---|
| Offline, DNS failure, connect/call timeout | `QuestionsResult.NetworkError` (all are `IOException`s) | `QuestionsRepositoryImplTest`: connection failure, unknown host |
| HTTP error (4xx/5xx, e.g. quota exhausted) | `ApiError(httpCode, message)` | HTTP error test |
| Body that is not valid JSON | `ApiError(null, ...)`, never an exception | malformed body test |
| API adds or omits fields | Unknown fields ignored; optional ones have defaults (e.g. no `owner`) | success mapping, mapper tests |
| Title with HTML entities (`&#39;`, `&amp;`) | Decoded to plain text | `QuestionMapperTest` |
| Question id that does not exist | `QuestionResult.NotFound` (the API answers with an empty list) | not found test |
| Empty result | `Success` with an empty list | unanswered request test |
| Coroutine cancelled while loading | `CancellationException` is rethrown, never turned into a result | design (see [Coroutine cancellation](#coroutine-cancellation)) |
| Unexpected bug inside the SDK | Caught and reported as `ApiError`, so the host app does not crash | design |
| Invalid configuration (bad URL, page size, timeouts) | `IllegalArgumentException` at construction, fail fast | `StackOverflowConfigTest` |

### App

| Case | Behaviour | Test |
|---|---|---|
| No internet at launch | Error state "No connection..." with **Retry**; the filter stays usable. Checked by hand on an emulator with airplane mode | `HomeViewModelTest`, `HomeScreenTest` |
| Retry after an error | Back to loading, then content | network error then retry tests |
| Server/API error | Separate message ("not responding properly") with Retry | API error tests |
| No questions (e.g. unanswered filter on a quiet day) | Empty state | empty result tests |
| Filter changed while a request is in flight | The old request is cancelled, a slow stale response cannot overwrite the new list | `stale response is ignored...` |
| Scrolling to the end | Next page loads once; nothing is requested when there are no more pages | load more tests |
| Page 2 fails | The list already shown is kept; scrolling to the end tries again | `failed load more keeps the list` |
| Same question in two pages (the 30-day window slides) | De-duplicated by id | load more test |
| Question without author | Shown as "unknown" | UI formatting |
| Process death | Filter restored from `SavedStateHandle`; detail id restored from the route | `filter survives recreation...`, detail tests |
| Deep link to an id that does not exist | Detail "does not exist" state | `DetailViewModelTest` not found |
| Detail with no network | Error state with Retry | detail error tests |

### Not handled on purpose

- **No offline cache**: without a network the list is not available (it would live in the app, see
  [Roadmap](#roadmap)).
- **No automatic retry or backoff**, and the API `backoff` field is ignored: the user retries by hand.
- **Quota exhaustion is shown as a generic server error**, not with a dedicated message.
- **After process death the list restarts from page 1** (only the filter is restored).
- **No pull-to-refresh**: Retry and the filter toggle are the only ways to reload.

---

## Known limitations

- Manual checks were limited to an emulator: the list with real data and the detail through the
  deep link on `mock`. Not tried on a physical device. The rest of the behaviour is covered by 94
  tests (UI tests run on the JVM, see [Testing](#testing)).
- "Open in browser" opens the `link` returned by the API. If `stackoverflow.com` blocks your
  network (it can answer 403/503 to some shared IPs, e.g. hotels), the browser shows that error:
  it is not an app issue.
- The `fromdate` window slides between pages, so a question at the edge can appear twice (the app
  de-duplicates by id) or be skipped.
- Anonymous Stack Exchange requests have a small shared quota; when it is exhausted the app shows
  the server error state. An API key is not used.
- Retrofit/OkHttp can still clash with the host's versions at runtime, see
  [Dependency conflicts](#dependency-conflicts-the-main-trade-off).

---

## How this was built

The assignment allows AI coding tools as long as I know and own all the code. I used an AI coding
assistant (Claude Code) **to go faster, but deliberately one small step at a time**: every step
became a pull request that I reviewed before the next one started, and I steered the design along
the way. The history is in the pull requests of this repository, in order:

| PR | Step |
|---|---|
| #1 | SDK data layer: Retrofit service, DTOs, mapper, repository, tests |
| #2 | SDK facade, `:api` module, README |
| #3 | App foundation: Hilt, dispatcher module, Timber, StrictMode |
| #10 | SDK: `initialize`/`warmUp`, optional logging, remote data source |
| #4 | SDK: `getQuestion(id)` for the detail screen and the deep link |
| #5 | `mock` and `real` flavors |
| #6 | Design system and shared routes |
| #7 | Questions list with the "unanswered" filter |
| #8 | Detail screen and deep link |
| #9 | README, edge cases, how to try the deep link |

**Decisions that came out of the reviews** (the assistant proposed, I decided or changed course):

- Multi-module layout copied from my previous project (StocksTracker), without convention plugins.
- A separate `:api` module for the public configuration, and `:data` as `implementation` with a
  warning in the build file and in this README.
- Dropping a `@RequiresOptIn` marker, then dropping a pluggable logger, to keep the public API small.
- A remote data source between the repository and Retrofit, so the repository does not know Retrofit.
- Naming what the host app sees (`StackOverflowSdk.questions`) while keeping repository naming inside.
- `initialize` + `warmUp` instead of work at startup on the main thread.
- Only labelling as "design patterns" what really is one.
- The README sections on dependency conflicts, edge cases and known limitations.

A few things were found by running the app on an emulator and fixed afterwards (for example titles
not aligned in the list). I read, understand and can explain and change every part of the code in
this repository.

---

## Notes

This repository is private, as required by the assignment.
