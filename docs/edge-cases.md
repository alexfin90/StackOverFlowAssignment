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
| `loadMore` called many times at once (every scroll event) | One request; the extra calls return immediately | `concurrent loadMore calls make a single request...` |
| Request cancelled (the screen goes away) | The state is restored and the lock released; nothing is thrown to the host | `cancelling a request restores the state...` |
| Empty result vs. nothing loaded yet | `isLoaded` tells them apart | `an empty result is told apart from nothing loaded yet` |
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
| Filter changed while a request is in flight | One pager per filter: `flatMapLatest` cancels the old one with everything started for it, a late answer cannot reach the screen | `the old pager can no longer reach the screen after a filter change`, `a filter change cancels the request still running for the old pager` |
| Scrolling to the end | Next page loads once; extra calls return at once; nothing is requested when there are no more pages | `concurrent loadMore calls...`, `loadMore does nothing without a next page` (pager) |
| Page 2 fails | The list already shown is kept; the same page is tried again on the next call | `a failed loadMore keeps the list and the same page is tried again` (pager) |
| A question is posted while the user scrolls | The window is fixed at refresh (`fromdate`/`todate` snapshot), so the pages do not shift; de-duplication by id is a safety net | `every page of a snapshot uses the same window...`, `a repeated question is kept once` |
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
