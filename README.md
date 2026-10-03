# StackOverflow Home Assignment

An Android **SDK** that retrieves the latest StackOverflow questions, built for the Callsign
**[Mobile Engineer] Home Assignment**, plus a sample app that consumes it.

The assignment asks for an app listing the latest StackOverflow questions, with an "unanswered"
filter and a detail view, where **the retrieval logic is extracted into a library module** that any
app can reuse. This project puts the weight on that library: a small, stable, hard-to-misuse SDK.

> **Status.** The SDK (`:stackoverflowsdk` + `:api` + `:domain` + `:data`) is implemented and tested. The
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
- **Explicit timeouts** and **coroutine-cancellation aware**: slow or missing networks fail fast
  as `NetworkError`, cancelled requests stop cleanly.
- **Unit tests**: `MockWebServer` + Robolectric for the networking layer, plain JUnit elsewhere.

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
    participant Http as Retrofit / OkHttp
    participant So as Stack Exchange API

    App->>Sdk: create(config)
    Sdk-->>App: QuestionsRepository (no I/O)
    App->>Repo: getLatestQuestions(unansweredOnly, page)
    Note over Repo: fromdate = now - lastDays (seconds)
    Repo->>Http: GET /2.3/questions or /questions/no-answers
    Http->>So: HTTP request (connect + call timeouts)
    alt 2xx and valid JSON
        So-->>Http: items, has_more
        Http-->>Repo: DTOs
        Repo-->>App: Success(questions, hasMore)
    else HTTP 4xx/5xx or unreadable body
        Repo-->>App: ApiError(httpCode?, message)
    else offline, DNS failure, timeout
        Repo-->>App: NetworkError(message)
    end
```

### Error mapping

```mermaid
flowchart LR
    ex{{"exception in the request"}}
    ex -->|CancellationException| rethrow["rethrown<br/>(coroutine cancelled)"]
    ex -->|HttpException| api1["ApiError(httpCode)"]
    ex -->|"IOException<br/>(UnknownHost, Connect, SocketTimeout)"| net["NetworkError"]
    ex -->|SerializationException| api2["ApiError(null)"]
    ex -->|"any other Exception"| api3["ApiError(null)"]
```

Only the first case leaves the SDK as an exception, and it is handled by the coroutines runtime.

- `:api` holds the public configuration (`StackOverflowConfig`), pure Kotlin/JVM.
- `:domain` is pure Kotlin/JVM with **zero third-party dependencies**. The repository contract only
  uses `suspend`, so it does not even expose coroutines.
- `:data` hides networking. Its only public symbol is `createQuestionsRepository()`.
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

### Patterns: Facade and Singleton

**Facade.** The SDK has several moving parts (Retrofit service, DTOs, mappers, HTTP client,
repository). The *facade* pattern hides them behind one simple, stable entry point:
`StackOverflowSdk.create(config)`, which returns a `QuestionsRepository`. The host app does not
know what is behind it, so internals can change (even the whole HTTP stack) without breaking
integrators. For an SDK this matters twice: it keeps the public surface minimal, and it gives one
place to enforce rules (validated config, no exceptions across the boundary).

**Singleton.** `StackOverflowSdk` is a Kotlin `object`, i.e. a singleton used as the entry point:
one well-known, always available access point that needs no instance and no `Context`. A common
SDK practice is also to build the expensive objects (the HTTP client and its connection pool) once
and reuse them. Two conscious choices here:

- The `object` is **stateless**: it only creates repositories. Global mutable state in a singleton
  hurts testability and makes it impossible to have two configurations in the same process.
- The *repository* is **not** forced to be a singleton: `create()` returns a new instance and the
  caller keeps it (typically one per app, provided through DI in the sample app). This keeps tests
  isolated and the SDK free of hidden global state.

---

## Public API

```kotlin
// Add to your app
implementation(project(":stackoverflowsdk"))

// import com.alexfin90.stackoverflowsdk.StackOverflowSdk
// import com.alexfin90.stackoverflowsdk.api.StackOverflowConfig   (optional)
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
| `StackOverflowConfig` | `baseUrl`, `pageSize` (1..100), `lastDays` (default 30), `connectTimeoutSeconds` (10), `callTimeoutSeconds` (30). Validated at construction. |
| `QuestionsRepository` | `suspend fun getLatestQuestions(unansweredOnly, page): QuestionsResult` |
| `QuestionsResult` | Sealed: `Success(questions, hasMore)` / `NetworkError` / `ApiError(httpCode?, message)` |
| `Question` | Plain domain model, independent from the wire format. Title is already HTML-decoded. |

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

## Module Structure

```
StackOverFlow Home Assignment
├── app                       # Sample consumer app (Compose)
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
./gradlew :app:assembleDebug                 # build the sample app (and the SDK)
./gradlew :stackoverflowsdk:data:testDebugUnitTest # networking layer tests (MockWebServer + Robolectric)
./gradlew :stackoverflowsdk:api:test               # config tests
./gradlew :stackoverflowsdk:testDebugUnitTest      # facade tests
```

---

## Testing

- **Mapper**: HTML entity decoding (numeric, hex, named, no double decoding), DTO to domain mapping.
- **Repository** (against `MockWebServer`): success mapping, correct endpoint and query parameters
  (30-day window, paging, `no-answers` for the filter), HTTP error, malformed body, connection
  failure, unknown host.
- **Config** (`:api`): defaults and validation.
- **Facade**: `create()` does no I/O.

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
