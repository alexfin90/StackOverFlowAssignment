[← Back to the README](../README.md)

# Test tags

Every interesting element of the sample app has a stable **test tag**. The same tags serve two
audiences:

- **Compose UI tests** (`onNodeWithTag(TestTags.UNANSWERED_FILTER)`), already used by the unit tests.
- **End-to-end automation by QA** (Appium / UiAutomator), which cannot read Compose tags unless the
  app exposes them. That needs one extra step, described below.

## How it works

In Compose a tag is added with `Modifier.testTag("name")`. Tags live in the semantics tree, which
only Compose tests read. To let **UiAutomator, and therefore Appium,** see them as the Android
`resource-id` of the element, the root of the UI must opt in. Two pieces, both in this repository:

1. **The tags** are constants, so a test and the screen can never disagree on a name:
   `TestTags` in `:core:designsystem` and `DetailTestTags` in `:feature:detail`.
2. **The switch**: `Modifier.exposeTestTagsAsResourceId()` (in `:core:designsystem`) sets
   `testTagsAsResourceId = true` on the node where it is applied. The app applies it **once**, on the
   root `Scaffold` of `StackOverFlowAppContent`, and **only in debug builds**:

```kotlin
// StackOverFlowApp.kt
Scaffold(
    modifier = modifier
        .fillMaxSize()
        .then(if (BuildConfig.DEBUG) Modifier.exposeTestTagsAsResourceId() else Modifier),
    // ...
)
```

Why debug only: exposing resource-ids reveals the internal structure of the screens to any
automation tool. The tags themselves are attached in every build but are invisible outside Compose
tests unless this flag is on, so a release build exposes nothing.

> The flag can use `BuildConfig.DEBUG` directly because the design system is a module of the same
> app, built with the same variant. A design system published on its own as a `release` library
> could not do that (inside the library `BuildConfig.DEBUG` is always `false`) and would need a
> runtime switch set by the consuming app.

### Verified

Not guessed: on an emulator, with the `mock` debug build, `adb shell uiautomator dump` lists the tags
as `resource-id` (exactly the tag, with no package prefix). Before the switch every `resource-id`
was empty. A unit test checks that the modifier sets the flag on exactly one node, and fails if the
flag is removed.

Appium itself has **not** been run against the app; there is no Appium suite in this repository.

## The tags

| Tag | Element | Screen | Defined in |
|---|---|---|---|
| `unanswered_filter` | the "Unanswered" filter chip | list | `TestTags.UNANSWERED_FILTER` |
| `questions_list` | the scrolling list of questions | list | `TestTags.QUESTIONS_LIST` |
| `question_row_<id>` | one question row, `<id>` is the question id (e.g. `question_row_1000`) | list | `TestTags.questionRow(id)` |
| `loading` | the full-screen spinner | list, detail | `TestTags.LOADING` |
| `error` | the error state (message and button) | list, detail (also "question not found") | `TestTags.ERROR` |
| `error_retry` | the **Retry** button of the error state | list, detail | `TestTags.ERROR_RETRY` |
| `empty` | the "no questions" state | list | `TestTags.EMPTY` |
| `detail_back` | the back arrow in the top bar | detail | `DetailTestTags.BACK` |
| `detail_content` | the scrolling content of the question | detail | `DetailTestTags.CONTENT` |
| `detail_open_in_browser` | the **Open in browser** button | detail | `DetailTestTags.OPEN_IN_BROWSER` |

**Keep this table up to date in the same pull request** that adds, renames or removes a tag.

## Rules

- **`snake_case`, lowercase, stable.** Renaming a tag breaks every test and automation script that
  uses it, so treat tags as a small public contract.
- **Never build a tag from translated text.** The text on screen changes with the language, so a tag
  made from it would differ per locale. Dynamic parts must be a stable value: an id, never a label
  (`question_row_1000`, not `question_row_What is a ViewModel?`).
- **Constants, not string literals,** in screens and tests.
- **No personal data in a tag**, only ids.
- **Apply the tag where the element is,** not on a wrapper far away: the `resource-id` is what QA
  will click.

## Using the tags from QA automation

With the debug build installed, the elements are found by `resource-id`. Because the id is the bare
tag, a locator that does not depend on how a client prefixes ids is the XPath one:

```text
//*[@resource-id='unanswered_filter']
//*[@resource-id='question_row_1000']
//*[@resource-id='detail_open_in_browser']
```

The `mock` flavor makes end-to-end tests **deterministic and independent of the network**: it serves
20 fixed questions (ids `1000` to `1019`, some with no answers) with a short latency. Combined with
the deep link, a test can also skip the list:

```bash
adb shell am start -a android.intent.action.VIEW -d "stackoverflowdemo://question/1000" \
  -p com.alexfin90.stackoverflowhomeassignment.mock
```

(`-p` selects the app when both flavors are installed, see [the sample app](sample-app.md#deep-link).)

To check what is exposed on a device, without Appium:

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb exec-out cat /sdcard/ui.xml | grep -o 'resource-id="[^"]*"'
```

## Not covered

- No Appium or other end-to-end suite is included; the tags and the switch are the groundwork for it.
- Only the elements listed above are tagged. The text fields inside a row (title, author) are not;
  QA locates a row by `question_row_<id>` and reads its text.
