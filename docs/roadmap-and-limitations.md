[← Back to the README](../README.md)

# Roadmap

Delivered step by step through stacked pull requests.

- [x] Module structure (facade, api, domain, data), explicit API mode
- [x] Domain models, result type, repository contract and data layer with tests
- [x] Facade, config and README
- [x] SDK: `getQuestion(id)`
- [x] App: Hilt, `mock`/`real` flavors, design system, list + filter, detail + deep link
- [x] Unit tests and UI tests (happy and unhappy paths)

## What I would add with more time (SDK)

- Pluggable HTTP layer / relocation to remove the runtime dependency risk described above
- Response caching and retry with backoff for transient failures
- Structured logging with a session id and PII-free diagnostics hook
- Binary-compatibility validation (`binary-compatibility-validator`) and a published changelog
  with semantic versioning and a deprecation policy
- `Flow`-based API for paging/refresh on top of the current `suspend` contract

## What I would add with more time (app)

- Instrumented tests (Hilt end to end, navigation, deep link) and screenshot tests
- Show the question body (needs the API `withbody` filter) and answers
- Offline cache (Room as single source of truth) in the app, keeping the SDK stateless
- Paging 3 instead of the hand-written paging, if the list grows in complexity

# Known limitations

- Manual checks were limited to an emulator: the list with real data and the detail through the
  deep link on `mock`. Not tried on a physical device. The rest of the behaviour is covered by 84
  tests (UI tests run on the JVM, see [Testing](testing.md)).
- "Open in browser" opens the `link` returned by the API. If `stackoverflow.com` blocks your
  network (it can answer 403/503 to some shared IPs, e.g. hotels), the browser shows that error:
  it is not an app issue.
- The `fromdate` window slides between pages, so a question at the edge can appear twice (the app
  de-duplicates by id) or be skipped.
- Anonymous Stack Exchange requests have a small shared quota; when it is exhausted the app shows
  the server error state. An API key is not used.
- kotlinx.serialization and kotlinx.coroutines are still runtime dependencies that could clash with the host's versions (much less likely than an HTTP library), see
  [Dependency conflicts](dependency-conflicts.md).
