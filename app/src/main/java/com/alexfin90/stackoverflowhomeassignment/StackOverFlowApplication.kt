package com.alexfin90.stackoverflowhomeassignment

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy.Builder
import android.os.StrictMode.VmPolicy
import com.alexfin90.stackoverflowhomeassignment.logging.plantTimberTree
import com.alexfin90.stackoverflowhomeassignment.core.dispatcher.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@HiltAndroidApp
class StackOverFlowApplication : Application() {

    @Inject
    @field:ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        plantTimberTree()
        setStrictModePolicy()
        initializeSdk(applicationScope)
    }
}

/**
 * Set a thread policy that detects all potential problems on the main thread, such as network
 * and disk access. Also detects potential memory leaks in the application
 *
 * If a problem is found, the offending call will be logged and the screen flashes.
 * Debug builds only.
 * @see <a href="https://medium.com/wizeline-mobile/raising-the-bar-with-android-strictmode-7042d8a9e67b">Android StrictMode best practices</a>
 */
private fun setStrictModePolicy() {
    if (BuildConfig.DEBUG) {
        StrictMode.setThreadPolicy(
            Builder().detectAll().penaltyLog().penaltyFlashScreen().build(),
        )
        StrictMode.setVmPolicy(
            VmPolicy.Builder().detectAll().penaltyLog().build(),
        )
    }
}
