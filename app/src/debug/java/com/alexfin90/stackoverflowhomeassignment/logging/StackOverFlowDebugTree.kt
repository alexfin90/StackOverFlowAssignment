package com.alexfin90.stackoverflowhomeassignment.logging

import timber.log.Timber

/** Debug builds: logs everything, with the line number in the tag. */
object StackOverFlowDebugTree : Timber.DebugTree() {

    override fun createStackElementTag(element: StackTraceElement): String {
        return "${super.createStackElementTag(element)}:${element.lineNumber}"
    }
}
