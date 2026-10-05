package com.alexfin90.stackoverflowhomeassignment.feature.home

import com.alexfin90.stackoverflowsdk.domain.Question

internal fun Question.toItem(): QuestionItem = QuestionItem(
    id = id,
    title = title,
    ownerName = ownerName,
    score = score,
    answerCount = answerCount,
    createdAtEpochSeconds = creationDateEpochSeconds,
)
