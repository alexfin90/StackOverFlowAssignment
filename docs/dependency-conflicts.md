[← Back to the README](../README.md)

# Dependency conflicts: why the SDK has no HTTP library

> [!NOTE]
> **`implementation` hides a dependency at compile time, not at runtime.**
>
> A dependency declared with `implementation` cannot be imported by host apps, and changing it is
> not a breaking change for the public API. **But** at *runtime* the SDK's dependencies end up in
> the host app's dependency graph, and **Gradle resolves each library to a single version, usually
> the highest one requested by anyone**. If the SDK used OkHttp and the host app used another
> version, the SDK would run against *the host's version*, not the one it was built and tested
> with. A binary-incompatible change between the two (a removed method, an extension function
> that moved) crashes **inside the host app**, even if the SDK did not change.

This is a real risk for any SDK that embeds third-party libraries, and it is the main reason an
SDK should keep its dependency set small.

## What this SDK does

The first versions used Retrofit + OkHttp. They were replaced by the platform's
**`HttpURLConnection`** (`UrlConnectionQuestionsRemoteDataSource`): for two GET calls an HTTP library
adds more risk than value.

- **No HTTP library at runtime.** On Android, `HttpURLConnection` is implemented with a private,
  repackaged copy of OkHttp (`com.android.okhttp`) that belongs to the platform, so it can never
  clash with the host's OkHttp. It is the same idea as shading, done by Android.
- **Only one class changed.** The repository depends on the `QuestionsRemoteDataSource` interface,
  so swapping the transport did not touch the repository, the mapper or the public API.
- **What is left at runtime**: kotlinx.serialization (parsing) and kotlinx.coroutines (`Dispatchers.IO`,
  timeouts). Both are `implementation`, from JetBrains, very common in host apps and careful about
  binary compatibility. To reach zero, the parsing could use `org.json` (part of the platform), at
  the cost of manual parsing.
- **OkHttp is still used, but only in tests** (MockWebServer is a test server).

What the platform client does not give for free, and how the SDK handles it:

| Topic | How |
|---|---|
| Total call timeout | `readTimeout` only limits each read, so the whole call runs under `withTimeoutOrNull(callTimeout)` |
| Cancellation | A blocking read ignores coroutine cancellation: the read runs outside the caller's job, a cancelled caller stops waiting at once and the connection is closed in the background |
| Main safety | The SDK moves to `Dispatchers.IO` itself, so callers can call it from the main thread |
| gzip | Android decompresses transparently; the SDK also handles `Content-Encoding: gzip` for a plain JVM |
| Retries | `HttpURLConnection` retries a failed GET once by itself (OkHttp does the same by default) |
| Interceptors, pinning | No interceptors. Pinning would go in a Network Security Config or a custom `TrustManager` |

## Other options

| Option | Idea | Cost |
|---|---|---|
| Relocation (shading) | Repackage OkHttp/Retrofit under the SDK's own package at build time so the host's version can never clash | Build complexity, two copies of OkHttp in the app, not trivial for AARs |
| Pin and document | Publish the exact tested versions and the supported range | Pushes work to integrators |
| Compatibility tests | CI matrix that builds a sample host against several versions of the libraries | CI time |
| Accept the host's client | Let the host pass its own `OkHttpClient` | OkHttp becomes part of the public API |
