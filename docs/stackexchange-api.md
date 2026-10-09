[← Back to the README](../README.md)

# Stack Exchange API used

Base URL `https://api.stackexchange.com/` (v2.3). Both calls are `GET`, anonymous, and return JSON.

| Use case | Endpoint | Docs |
|---|---|---|
| Latest questions | `/2.3/questions` | [docs](https://api.stackexchange.com/docs/questions) |
| Unanswered questions (0 answers) | `/2.3/questions/no-answers` | [docs](https://api.stackexchange.com/docs/no-answer-questions) |
| One question by id (detail, deep link) | `/2.3/questions/{id}` | [docs](https://api.stackexchange.com/docs/questions-by-ids) |

| Query parameter | Value | Why |
|---|---|---|
| `site` | `stackoverflow` | Fixed, defines the target site |
| `sort` / `order` | `creation` / `desc` | Fixed, "latest" means newest first |
| `fromdate` | now minus `lastDays`, Unix time in **seconds** | Requirement: questions of the last 30 days. 1 day = 86,400 s |
| `todate` | the snapshot instant, **pager only** | Fixes the end of the window so new questions cannot shift the pages |
| `page` / `pagesize` | from the caller / `StackOverflowConfig.pageSize` | Paging (`has_more` tells if another page exists) |

Fields read from each item: `question_id`, `title` (HTML-encoded, decoded by the SDK), `link`,
`creation_date`, `answer_count`, `score`, `owner.display_name`, `tags`. Unknown fields are ignored,
so API additions do not break the SDK.

Things to know about the API: anonymous requests are rate limited (the quota is small and shared
per IP), and an exhausted quota or a bad request comes back as an HTTP error with a JSON body,
which the SDK reports as `SdkError.Api`. On the plain repository path the `fromdate` window is
recomputed on each call, so it slides slightly between pages; the `QuestionsPager` avoids it by sending the same
`fromdate` and `todate` for every page of a snapshot (`todate` was checked against the real API: only questions up
to that instant come back).
