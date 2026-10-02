package com.alexfin90.stackoverflowsdk.data.mapper

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

private val numericEntity = Regex("&#(x?)([0-9a-fA-F]+);")

/**
 * The API returns titles HTML-encoded (`&#39;`, `&amp;`, ...). Decoding here keeps
 * the SDK free from android.text.Html and the domain model plain text.
 */
internal fun String.decodeHtmlEntities(): String =
    numericEntity.replace(this) { match ->
        val radix = if (match.groupValues[1].isEmpty()) 10 else 16
        match.groupValues[2].toIntOrNull(radix)
            ?.takeIf { it in 0..0x10FFFF }
            ?.let { String(Character.toChars(it)) }
            ?: match.value
    }
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&") // last, so "&amp;lt;" becomes "&lt;" and not "<"
