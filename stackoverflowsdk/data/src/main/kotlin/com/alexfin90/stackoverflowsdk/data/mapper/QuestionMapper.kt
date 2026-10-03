package com.alexfin90.stackoverflowsdk.data.mapper

import android.text.Html
import com.alexfin90.stackoverflowsdk.data.remote.QuestionDto
import com.alexfin90.stackoverflowsdk.domain.Question

internal fun QuestionDto.toDomain(): Question = Question(
    id = questionId,
    title = title.decodeHtmlEntities(),
    link = link,
    creationDateEpochSeconds = creationDate,
    answerCount = answerCount,
    score = score,
    ownerName = owner?.displayName?.decodeHtmlEntities(),
    tags = tags,
)

/**
 * The API returns titles HTML-encoded (`&#39;`, `&amp;`, ...). Html.fromHtml decodes every entity;
 * it needs the Android framework, which is why this module is an Android library.
 */
internal fun String.decodeHtmlEntities(): String =
    Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString()
