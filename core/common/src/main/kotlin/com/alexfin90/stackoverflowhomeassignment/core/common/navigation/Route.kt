package com.alexfin90.stackoverflowhomeassignment.core.common.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations, shared by the app and the feature modules. */
sealed interface Route {

    @Serializable
    data object QuestionsList : Route

    /** Arguments are read with `SavedStateHandle.toRoute<Route.QuestionDetail>()`. */
    @Serializable
    data class QuestionDetail(val questionId: Long) : Route
}

object DeepLinks {
    /**
     * `stackoverflowdemo://question/{questionId}` opens the detail screen, e.g.
     * `adb shell am start -a android.intent.action.VIEW -d "stackoverflowdemo://question/1000"`
     */
    const val QUESTION_DETAIL_BASE = "stackoverflowdemo://question"
}
