package com.alexfin90.stackoverflowhomeassignment.logging

import android.util.Log
import timber.log.Timber

/**
 * Release builds: only warnings and errors, no debug noise and nothing that could leak data.
 * This is the place to forward errors to a crash reporter (not used in this project).
 */
object StackOverFlowReleaseTree : Timber.Tree() {

    private const val TAG = "StackOverFlow"

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Log.println(priority, tag ?: TAG, message)
    }
}
