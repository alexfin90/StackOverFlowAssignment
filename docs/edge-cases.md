[← Back to the README](../README.md)

# Edge cases

What happens in the unusual situations, and where it is covered.

## SDK

| Case | Behaviour | Test |
|---|---|---|
| Offline, DNS failure, connect/call timeout | `Failure(SdkError.Network)` (all are `IOException`s) | `QuestionsRepositoryImplTest`: connection failure, unknown host |
| HTTP error (4xx/5xx, e.g. quota exhausted) | `Failure(SdkError.Api(httpCode, message))` | HTTP error test |
| Body that is not valid JSON | `Failure(SdkError.Api(null, ...))`, never an exception | malformed body test |
| API adds or omits fields | Unknown fields ignored; optional ones have defaults (e.g. no `owner`) | success mapping, mapper tests |
| Title with HTML entities (`&#39;`, `&amp;`) | Decoded to plain text | `QuestionMapperTest` |
| Question id that does not exist | `QuestionResult.NotFound` (the API answers with an empty list) | not found test |
| Empty result | `Success` with an empty list | unanswered request test |
| Coroutine cancelled while loading | `CancellationException` is rethrown, never turned into a result | design (see [Coroutine cancellation](sdk-api.md#coroutine-cancellation)) |
| Unexpected bug inside the SDK | Caught and reported as `SdkError.Api`, so the host app does not crash | design |
| Invalid configuration (bad URL, page size, timeouts) | `IllegalArgumentException` at construction, fail fast | `StackOverflowConfigTest` |

## App

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

## Not handled on purpose

- **No offline cache**: without a network the list is not available (it would live in the app, see
  [Roadmap](roadmap-and-limitations.md#roadmap)).
- **No automatic retry or backoff**, and the API `backoff` field is ignored: the user retries by hand.
- **Quota exhaustion is shown as a generic server error**, not with a dedicated message.
- **After process death the list restarts from page 1** (only the filter is restored).
- **No pull-to-refresh**: Retry and the filter toggle are the only ways to reload.
