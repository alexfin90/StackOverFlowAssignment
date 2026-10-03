package com.alexfin90.stackoverflowsdk.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class QuestionsResponseDto(
    val items: List<QuestionDto> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
internal class QuestionDto(
    @SerialName("question_id") val questionId: Long,
    val title: String,
    val link: String,
    @SerialName("creation_date") val creationDate: Long,
    @SerialName("answer_count") val answerCount: Int = 0,
    val score: Int = 0,
    val owner: OwnerDto? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
internal class OwnerDto(
    @SerialName("display_name") val displayName: String? = null,
)
