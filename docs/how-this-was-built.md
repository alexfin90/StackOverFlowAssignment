[← Back to the README](../README.md)

# How this was built

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
| #11 | README split into linked documents under `docs/`, plus a testing guide |

**Decisions that came out of the reviews** (the assistant proposed, I decided or changed course):

- Multi-module layout copied from my previous project (StocksTracker), without convention plugins.
- A separate `:api` module for the public configuration, and `:data` as `implementation` with a
  warning in the build file and in this README.
- Dropping a `@RequiresOptIn` marker, then dropping a pluggable logger, to keep the public API small.
- A remote data source between the repository and Retrofit, so the repository does not know Retrofit.
  It paid off later: Retrofit + OkHttp were replaced by `HttpURLConnection` by changing one class.
- Naming what the host app sees (`StackOverflowSdk.questions`) while keeping repository naming inside.
- `initialize` + `warmUp` instead of work at startup on the main thread.
- Only labelling as "design patterns" what really is one.
- The README sections on dependency conflicts, edge cases and known limitations.

A few things were found by running the app on an emulator and fixed afterwards (for example titles
not aligned in the list). I read, understand and can explain and change every part of the code in
this repository.
