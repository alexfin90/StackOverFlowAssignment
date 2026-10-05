package com.alexfin90.stackoverflowhomeassignment.fake

import com.alexfin90.stackoverflowsdk.domain.Question

private const val NOW = 1_760_000_000L // fixed, so the mock data never changes

/** The 20 questions served by the `mock` flavor; ids 1000 to 1019. */
internal val fakeQuestions: List<Question> = listOf(
    "How do I collect a Flow safely in Jetpack Compose?" to 3,
    "Why does my LazyColumn recompose every item on scroll?" to 0,
    "Hilt cannot provide an interface binding in a library module" to 1,
    "Retrofit suspend function throws HttpException on 404" to 0,
    "Kotlin explicit API mode: how to hide a public class from consumers?" to 2,
    "Gradle: api vs implementation for a transitive dependency" to 4,
    "How to unit test SavedStateHandle without Robolectric?" to 0,
    "OkHttp callTimeout vs readTimeout, what is the difference?" to 1,
    "Type-safe navigation arguments with kotlinx.serialization" to 0,
    "R8 removes my @Serializable classes in release builds" to 2,
    "How to open a URL in the browser from Compose?" to 5,
    "Material 3 dynamic color falls back on API 30 and below" to 0,
    "StateFlow vs SharedFlow for one-off UI events" to 6,
    "Room migration fails with schema export enabled" to 0,
    "What is the difference between remember and rememberSaveable?" to 3,
    "Coroutine cancelled but the network call keeps running" to 0,
    "Why is my deep link opening the wrong activity?" to 1,
    "Dependency version conflict between app and library at runtime" to 0,
    "Compose preview not showing with Hilt ViewModel" to 2,
    "Kotlin sealed interface vs sealed class, when to use which?" to 4,
).mapIndexed { index, (title, answers) ->
    Question(
        id = 1000L + index,
        title = title,
        link = "https://stackoverflow.com/questions/${1000 + index}",
        creationDateEpochSeconds = NOW - index * 3_600L,
        answerCount = answers,
        score = (index * 7) % 13 - 2,
        ownerName = if (index % 5 == 4) null else "user_${index + 1}",
        tags = listOf("kotlin", "android").plus(if (index % 2 == 0) listOf("jetpack-compose") else emptyList()),
    )
}
