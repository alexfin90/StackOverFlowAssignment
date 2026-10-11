[← Back to the README](../README.md)

# Testing

112 automated tests, all running on the JVM: no device, no emulator, no internet. This page explains
what is tested at each layer, with which tools, how to run it, and how to see the coverage.

- [Strategy](#strategy)
- [Tools, and why](#tools-and-why)
- [What is tested, layer by layer](#what-is-tested-layer-by-layer)
- [Techniques worth knowing](#techniques-worth-knowing)
- [How to run the tests](#how-to-run-the-tests)
- [Coverage](#coverage)
- [What is not tested](#what-is-not-tested)

For the test tags used by the UI tests and by QA automation, see [test-tags.md](test-tags.md).

## Strategy

An SDK has a slow feedback loop with its users (a bank updates it every few months), so the weight
is on **many fast unit tests of the logic** and **few tests that exercise the real HTTP stack**.

```
        few        Wiring: real Retrofit + OkHttp against a local server (3 tests)
       ─────
     some          Remote data source against a local server: what goes on the wire,
    ───────        how each failure becomes a result (11 tests)
   many            Everything else against fakes: repository, decorator, facade, config,
  ─────────        mapper, ViewModels, Compose screens, routes, the top bar and the use cases (93 tests)
```

Rules followed everywhere:

- **Test behaviour, not implementation.** A test should not break when the code is refactored
  without changing what it does.
- **Hand-written fakes instead of a mocking library.** A fake is a small real class
  (`FakeQuestionsRepository`, `FakeRemote`, `FakeDelegate`), easy to read and to script, with no
  extra dependency.
- **Happy path first, then edge cases, then errors**, for every unit.
- **Time and randomness are injected** (see [techniques](#techniques-worth-knowing)), never read
  from the real clock inside the tests.

| Module | Tests | What |
|---|---|---|
| `:stackoverflowsdk:data` | 38 | mapper (5), remote data source (11), repository (9), logging decorator (8), end-to-end wiring (5, including the host logger) |
| `:stackoverflowsdk:api` | 7 | configuration defaults and validation |
| `:stackoverflowsdk` | 14 | `initialize`, `warmUp`, shared instance, concurrency, `create()`, warning to the host logger |
| `:core:designsystem` | 8 | components (Compose UI), title alignment, top bar, test tags exposed as `resource-id` |
| `:feature:home` | 20 | 12 ViewModel + 6 Compose UI (happy and unhappy paths) + 2 stateful route |
| `:feature:detail` | 10 | 4 ViewModel + 4 Compose UI (happy and unhappy paths) + 2 stateful route |
| `:app` | 4 | `MainViewModel`: top bar state per destination |
| `:core:domain` | 11 | use cases: paging with de-duplication, error translation (plain JVM) |
| **Total** | **112** | |

## Tools, and why

| Tool | What it is | Used for |
|---|---|---|
| **JUnit 4** | The test framework | Every test |
| **kotlinx-coroutines-test** | `runTest`, virtual time, `StandardTestDispatcher` | Testing `suspend` code and ViewModels without real waiting |
| **MockWebServer** | A tiny **real HTTP server** that starts inside the test, on `localhost` | Testing Retrofit + OkHttp for real, without the internet |
| **Robolectric** | A test runner that provides the Android framework on the JVM | Code that uses `Html`, `Bundle`, `SavedStateHandle`, Compose |
| **Compose UI test** | `createComposeRule`, `onNodeWithText`, `performClick` | UI tests of the screens |

### MockWebServer

[MockWebServer](https://github.com/square/okhttp/tree/master/mockwebserver) (from the OkHttp
project) opens a real server on a random port of `localhost`. The test queues the responses it
wants, points Retrofit at the server, makes the real call, and can inspect the request that arrived:

```kotlin
server.enqueue(json("""{"items":[...],"has_more":true}"""))          // the answer to give
val result = remote.getLatestQuestions(fromDate = 1_000, page = 2, pageSize = 20, unansweredOnly = false)
val request = server.takeRequest()                                    // what actually arrived
assertEquals("/2.3/questions", request.requestUrl!!.encodedPath)
assertEquals("2", request.requestUrl!!.queryParameter("page"))
```

Why not mock the Retrofit service instead? Because the real stack is what can be wrong: a misspelled
`@Query`, a renamed JSON field, a converter that fails. A mocked service would let all of that pass.
It also simulates failures that are hard to fake otherwise:

| Simulation | How | Result checked |
|---|---|---|
| HTTP error | response with code 400 or 500 | `Failure.Http(code)` |
| Not JSON | body `not json` | `Failure.Malformed` |
| Connection dropped | `SocketPolicy.DISCONNECT_AFTER_REQUEST` | `Failure.Network` |
| Server that never answers | `SocketPolicy.NO_RESPONSE` | used to test cancellation |
| Host that does not exist | `http://nonexistent.invalid/` (a name that can never resolve) | `Failure.Network` |

### Robolectric

Plain JVM unit tests run against `android.jar`, whose framework classes are **stubs**: calling them
fails with `Method ... not mocked`. [Robolectric](https://robolectric.org) is a test runner
(`@RunWith(RobolectricTestRunner::class)`) that replaces those stubs with real implementations of
the Android framework, so code that needs `Context`, `Bundle`, `Html` or Compose runs on the JVM
without an emulator. It is declared with `testImplementation`, so **it is never shipped with the
SDK**.

Where it is needed here:

- `:data`: the mapper uses `Html.fromHtml` to decode HTML entities in titles, so every test that goes
  through the mapper needs it.
- ViewModels that use `SavedStateHandle` (it relies on `Bundle`).
- Every Compose UI test.

The cost: slower test runs, and the first run on a machine downloads the Android jars (so it needs
the network once). If this ever becomes a problem, the alternative is a small decoder written in
plain Kotlin for the few entities the API emits, which would let `:data` go back to plain JUnit.

### Compose UI tests run on the JVM

The UI tests target the **stateless screens** (`HomeScreen`, `DetailScreen`), which take a state and
callbacks. They need no device, so they run in CI like any unit test. The trade-off: they do not
exercise real rendering, navigation or the Hilt graph end to end. That is what instrumented tests
would add (see [What is not tested](#what-is-not-tested)).

## What is tested, layer by layer

### SDK: `:data`

| Test class | Uses | Covers |
|---|---|---|
| `QuestionMapperTest` (5) | Robolectric | HTML entities (numeric, hex, named, no double decoding), DTO to domain, missing author |
| `RetrofitQuestionsRemoteDataSourceTest` (11) | MockWebServer | endpoints and query parameters (`fromdate`, `page`, `pagesize`, `sort`, `site`), `no-answers` for the filter, `/questions/{id}`, unknown JSON fields ignored, HTTP error, malformed body, connection failure, unknown host, **cancellation** |
| `QuestionsRepositoryImplTest` (9) | fake data source + Robolectric | DTO to domain mapping, the 30-day `fromdate` window, parameters passed down, every failure kind mapped to the right public result, `getQuestion` (found, not found, failures) |
| `LoggingQuestionsRepositoryTest` (8) | fake repository, fake log sink | exact line format, levels, **no content in the lines**, a failing sink or a cancellation does not break the request, `getQuestion` lines |
| `QuestionsRepositoryWiringTest` (3) | MockWebServer + Robolectric | the whole chain through the public factory: success, unanswered endpoint, connection failure |

### SDK: configuration and facade

- `StackOverflowConfigTest` (6): defaults, logging off by default, every validation rule.
- `StackOverflowSdkTest` (12): `initialize` is applied once and ignored when repeated or late (with a
  warning when logging is on), use without `initialize`, one shared instance, `warmUp` is idempotent,
  **8 threads hitting the shared instance build exactly one**, `create()` is independent, a failing
  log sink cannot break `initialize`.

### App: ViewModels and screens

- `HomeViewModelTest` (12): initial load, success, empty, network and API errors, retry, filter
  toggle, filter restored from `SavedStateHandle`, **a stale response is ignored when the filter
  changes**, paging with de-duplication, no request when there are no more pages, a failed "load
  more" keeps the list, and changing the filter after loading more replaces the list
  instead of appending.
- `HomeRouteTest` (2) and `DetailRouteTest` (2): the **stateful** route against a fake ViewModel
  contract (no Hilt, no repository), which checks that the state is rendered and that events are
  forwarded. The back arrow is no longer a screen concern: it is in the shared top bar.
- `LoadQuestionsPageUseCaseTest` (7) and `GetQuestionUseCaseTest` (4): the app domain, on plain JVM
  with a fake repository: a page is added to the loaded ones, a question coming back in two pages
  appears once, page 1 starts over, every SDK failure becomes `Network` or `Server`.
- `MainViewModelTest` (4): the shared top bar state per destination (title, back arrow only on the detail).
- `HomeScreenTest` (6) and `DetailScreenTest` (4): the happy path (list, click, filter chip, content,
  open link) and the unhappy path (network error with retry, server error, not found).
- `DetailViewModelTest` (4): id read from the route, not found, error then retry, API error.
- `ComponentsTest` (8): each design system component, the alignment of titles in the list, the
  top bar (title; back arrow only when a handler is given) and the test tag switch.

## Techniques worth knowing

- **Time is injected.** The repository takes `nowEpochSeconds: () -> Long` (so "the last 30 days"
  can be checked exactly) and the log decorator takes `nanoTime` (so the duration in the log line is
  deterministic: `5 ms`).
- **`internal` test hooks.** The facade exposes `resetForTesting()` and `warningSink`, both
  `internal`, so tests can start from a clean state and capture the "initialize ignored" warning
  without Logcat. They are not part of the public API.
- **Testing cancellation.** The test starts a request against a server that never answers, cancels
  it, and asserts that **no value was returned** (if the `CancellationException` had been swallowed
  and turned into a result, a value would exist). `runTest` also fails on any uncaught exception, so
  finishing the test proves the cancellation did not crash. I checked it by removing the `throw e`
  temporarily: the test fails.
- **Fakes with a trailing lambda.** In `FakeDelegate` the parameter that tests pass as a trailing
  lambda is the **last** one, because Kotlin binds a trailing lambda to the last parameter.
- **One `@Before`.** In JUnit 4 the order of several `@Before` methods is not guaranteed, so the
  setup and the reset live in a single method.
- **`StandardTestDispatcher` as `Main`.** ViewModel tests set `Dispatchers.Main` to a test dispatcher
  (`MainDispatcherRule` in `:feature:home`, a `@Before` in `:feature:detail`) and advance time with
  `advanceUntilIdle()`.

## How to run the tests

All the suites:

```bash
./gradlew :core:domain:test :stackoverflowsdk:api:test :stackoverflowsdk:testDebugUnitTest \
  :stackoverflowsdk:data:testDebugUnitTest :core:designsystem:testDebugUnitTest \
  :feature:home:testDebugUnitTest :feature:detail:testDebugUnitTest
```

One module, or one test:

```bash
./gradlew :stackoverflowsdk:data:testDebugUnitTest
./gradlew :stackoverflowsdk:data:testDebugUnitTest --tests "*RetrofitQuestionsRemoteDataSourceTest.cancelling*"
```

Gradle writes an **HTML report** for each module, with the result of every test:
`<module>/build/reports/tests/testDebugUnitTest/index.html`
(for example `stackoverflowsdk/data/build/reports/tests/testDebugUnitTest/index.html`).

In Android Studio, right-click a test class or method and choose **Run**.

## Coverage

In Android Studio: right-click a test class, a package or a module and choose
**Run with Coverage**; the result shows in the editor and in the Coverage panel.

From Gradle, the Android Gradle Plugin can produce a JaCoCo HTML report per module. It is **not
enabled in the build** (it makes the build slower and the repository does not need it); this is how
I measured it, with a temporary change that I did not commit. In the `build.gradle.kts` of each
Android module:

```kotlin
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

android {
    buildTypes {
        debug { enableUnitTestCoverage = true }
    }
    testOptions {
        unitTests.all { test ->
            test.extensions.configure(JacocoTaskExtension::class.java) {
                isIncludeNoLocationClasses = true   // classes loaded by Robolectric's classloader
                excludes = listOf("jdk.internal.*")
            }
        }
    }
}
```

```bash
./gradlew :stackoverflowsdk:data:createDebugUnitTestCoverageReport
# report: stackoverflowsdk/data/build/reports/coverage/test/debug/index.html
```

The `isIncludeNoLocationClasses` part matters: without it, modules whose tests use Robolectric
report **0%**, because Robolectric loads the classes with its own classloader, which JaCoCo does not
instrument by default.

Measured on 2026-10-04 (line and branch coverage of the unit tests):

| Module | Lines | Branches |
|---|---|---|
| `:stackoverflowsdk:data` | 89.8% (167/186) | 83.6% |
| `:stackoverflowsdk` (facade) | 97.4% (38/39) | 75.0% |
| `:core:designsystem` | 99.4% (179/180) | 46.2% |
| `:feature:home` | 87.7% (157/179) | 67.1% |
| `:feature:detail` | 87.9% (124/141) | 52.9% |

`:api`, `:domain` and `:app` were not measured. Coverage says which code the tests run, not that the
assertions are good; the regression checks described above (removing a line to see the test fail)
say more about quality.

## What is not tested

- **No Appium / end-to-end suite.** The groundwork exists (stable [test tags](test-tags.md) exposed
  as `resource-id` in debug builds, and a `mock` flavor with fixed data) but no suite is included.
- **No instrumented tests.** Hilt wiring, navigation between screens and the deep link are not
  exercised end to end by an automated test. They were checked by hand on an emulator.
- **No test against the real Stack Exchange API.** It would be slow, flaky and subject to a shared
  quota. The request shape is verified against the documentation through `MockWebServer`.
- **`LogcatSink` (0%)**: it only wraps `android.util.Log`; the tests use a fake sink instead.
- **Branches in the screens** (design system 46%, detail 53%): several Compose branches (previews,
  alternative styles) are not driven by any test.
- **No physical device.** Only an emulator, and only by hand.
- **No performance, startup time or binary size measurement**, even though an SDK should track them.
