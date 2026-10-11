[← Back to the README](../README.md)

# Public API

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
    is QuestionsResult.Success -> show(result.questions, canLoadMore = result.hasMore)
    is QuestionsResult.Failure -> when (val error = result.error) {
        is SdkError.Network -> showOffline(error.message)
        is SdkError.Api     -> showError(error.httpCode, error.message)
    }
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
| `StackOverflowConfig` | `baseUrl`, `pageSize` (1..100), `lastDays` (30), `connectTimeoutSeconds` (10), `callTimeoutSeconds` (30), `loggingEnabled` (false), `logger` (null). Validated at construction. |
| `QuestionsRepository` | `suspend fun getLatestQuestions(unansweredOnly, page): QuestionsResult` and `suspend fun getQuestion(id): QuestionResult` |
| `QuestionsResult` | Sealed: `Success(questions, hasMore)` / `Failure(error)` |
| `QuestionResult` | Same idea for one question: `Success(question)` / `NotFound` / `Failure(error)` |
| `SdkError` | Why a call failed, **one hierarchy for every result**: `Network(message)` / `Api(httpCode?, message)`. A new kind of error is added in one place. |
| `Question` | Plain domain model, independent from the wire format. Title is already HTML-decoded. |

### Why one error type

The errors used to be repeated in each result (`QuestionsResult.NetworkError`, `QuestionResult.NetworkError`, ...),
and the app repeated them again in each screen. Adding one kind of error (for example throttling) meant
touching six types. Now the SDK has one `SdkError`, wrapped by each result's `Failure`, and the app
translates it in one function (`SdkError.toLoadFailure()` in `:core:domain`), and the screens use
`LoadFailure` directly. Done after the submission, before any public release: changing the shape of
public results is a breaking change once hosts depend on it.

### Adding a new kind of error

For example throttling (`error_id` 502 in the body of an HTTP 400). Four places, and the compiler
points to every other one, because each `when` over a sealed type stops compiling until the new
case is handled:

1. **SDK contract**: a new case in `SdkError` (`:stackoverflowsdk:domain`), for example
   `RateLimited(retryAfterSeconds: Int?)`.
2. **SDK detection**: the data source recognises it and returns a new `RemoteResult.Failure` case;
   `QuestionsRepositoryImpl.toSdkError()` maps it. Then the compiler asks for the log level and the
   description in `LoggingQuestionsRepository`.
3. **App translation**: a new case in `LoadFailure` and one line in `SdkError.toLoadFailure()`
   (`:core:domain`).
4. **App messages**: the compiler asks for the message in `HomeScreen` and `DetailScreen`.

Plus the tests: a MockWebServer response for the detection, one mapping test per layer.

## Initialization rules

1. **Only the first `initialize` takes effect**, and only if the shared repository was not used yet.
   Any other call returns `false` and, if logging is enabled, logs a warning. The configuration of a
   running SDK never changes.
2. **Using the SDK without `initialize` is safe**: the defaults apply, nothing throws. The first
   access then freezes that default configuration.
3. **`initialize` does not block**: it validates and stores the configuration. The expensive part
   (OkHttp client, Retrofit) is built on first use or in `warmUp()`, on the caller's thread.

## Logging

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
- **The host's logger**: Logcat on the user's phone cannot be read in production, so the host can
  pass its own `StackOverflowLogger` and send the lines where it already collects logs (Timber, a
  crash reporter). Setting it turns logging on and replaces Logcat. The SDK does not depend on
  Timber: it exposes a one-method `fun interface`. A logger that throws cannot break a request
  (same `safeLog`), and it runs on the caller's thread, so it must be fast. The "initialize
  ignored" warning goes to it too.

  ```kotlin
  StackOverflowConfig(logger = { level, message ->
      Timber.tag("StackOverflowSdk").log(level.toPriority(), message)
  })
  ```

  The sample app (`real` flavor) does this: the planted Timber tree decides what is shown, all
  lines in debug, warnings and errors in release. It was first left out to keep the API small and
  brought back for the production case. Compatibility note: before the first release adding the
  parameter is free; after a release a new constructor parameter is a binary-incompatible change
  and a new `Level` value breaks exhaustive `when` in hosts.
- Implemented as a decorator (`LoggingQuestionsRepository`) that is only created when logging is
  enabled, so the default path pays nothing and the repository stays free of logging code.

## Coroutine cancellation

`getLatestQuestions` is cancellable and rethrows `CancellationException` instead of converting it
to a result: swallowing it would leave a cancelled coroutine running. It can happen when:

- the user leaves the screen while the request is in flight and `viewModelScope` is cancelled;
- the user changes the filter and the previous job is cancelled (`collectLatest`, `Job.cancel()`);
- a `withTimeout` expires (`TimeoutCancellationException` is a subclass);
- a `LaunchedEffect` in Compose leaves the composition or its key changes.

It never crashes the host: the coroutines runtime handles it. It is the only exception that can
leave the SDK.

## Design decisions

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
  becomes `SdkError.Network`, same as offline or DNS failure (`UnknownHostException`).
- **`:data` is an Android library, `:domain` is pure JVM.** Only the contract needs to be
  framework-free. `:data` uses `Html.fromHtml` to decode HTML entities in titles; its tests run
  with Robolectric.
- **No offline cache.** Out of scope: the SDK stays stateless (a database would add dependencies
  and conflict risk). A cache would live in the app (e.g. Room as source of truth with a fallback
  on `SdkError.Network`) or as an optional OkHttp HTTP cache.
- **`minSdk` 24**, to keep the SDK integrable by apps with a low minimum.
- **INTERNET permission** is declared in the SDK manifest and merged into the host app.
