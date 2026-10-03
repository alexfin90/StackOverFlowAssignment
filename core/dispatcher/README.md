# Coroutine Scope and Dispatchers with Hilt

This module follows the architecture described in this article by Manuel Vivo:
https://manuelvivo.dev/coroutinescope-hilt

It provides a clean and testable way to inject coroutine-related dependencies using Hilt: instead of
hardcoding `Dispatchers.IO` inside a class, the class asks for a dispatcher, so a test can pass a
`TestDispatcher`.

> **Status in this project:** the module is wired into the Hilt graph, but nothing consumes it yet.
> It is introduced with the application foundation and used by the features in later pull requests.
> The SDK does **not** use it: the SDK has no DI framework on purpose (see the root README).

## What is included

### `CoroutinesQualifiers.kt`

Custom `@Qualifier`s to tell the dispatchers and the scope apart:
`@DefaultDispatcher`, `@IoDispatcher`, `@MainDispatcher`, `@MainImmediateDispatcher`,
`@ApplicationScope`.

### `CoroutinesDispatchersModule.kt`

Provides the standard `CoroutineDispatcher`s through Hilt with those qualifiers.

```kotlin
@InstallIn(SingletonComponent::class)
@Module
object CoroutinesDispatchersModule {

    @DefaultDispatcher
    @Provides
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @IoDispatcher
    @Provides
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    // ... Main and Main.immediate follow the same pattern
}
```

### `CoroutinesScopesModule.kt`

Provides a singleton `CoroutineScope` tied to the application lifecycle.

```kotlin
@Singleton
@ApplicationScope
@Provides
fun providesCoroutineScope(
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
```

## Usage example

Inject the dispatchers and the application-wide scope anywhere in the app:

```kotlin
class MyRepository @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    fun loadData() {
        applicationScope.launch(ioDispatcher) {
            // Perform background work here
        }
    }
}
```

## When to use which

- `withContext(ioDispatcher)` for blocking I/O (disk, database). Retrofit `suspend` calls do **not**
  need it: they are already main-safe.
- `@DefaultDispatcher` for CPU-heavy work (mapping or sorting big lists).
- `@ApplicationScope` for work that must **outlive a screen** (e.g. a fire-and-forget sync). For
  work tied to a screen use `viewModelScope`.

## Testing

Replace the qualified dispatcher with a test one in the constructor (or with `@TestInstallIn`):

```kotlin
val testDispatcher = StandardTestDispatcher()
val repository = MyRepository(CoroutineScope(testDispatcher), testDispatcher)
```
