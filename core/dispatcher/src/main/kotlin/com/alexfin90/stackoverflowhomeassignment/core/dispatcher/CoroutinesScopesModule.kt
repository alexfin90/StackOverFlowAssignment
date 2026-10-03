package com.alexfin90.stackoverflowhomeassignment.core.dispatcher

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

// https://manuelvivo.dev/coroutinescope-hilt
@InstallIn(SingletonComponent::class)
@Module
object CoroutinesScopesModule {

    /** Lives as long as the process. `SupervisorJob`: a failing child does not cancel its siblings. */
    @Singleton
    @ApplicationScope
    @Provides
    fun providesCoroutineScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
}
