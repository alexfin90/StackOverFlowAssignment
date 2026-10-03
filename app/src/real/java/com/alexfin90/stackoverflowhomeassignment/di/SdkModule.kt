package com.alexfin90.stackoverflowhomeassignment.di

import com.alexfin90.stackoverflowsdk.StackOverflowSdk
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `real` flavor: the only place where the app creates the actual SDK.
 * The `mock` flavor has its own `SdkModule` in `src/mock`, so no network stack is built there.
 */
@Module
@InstallIn(SingletonComponent::class)
object SdkModule {

    @Provides
    @Singleton
    fun provideQuestionsRepository(): QuestionsRepository = StackOverflowSdk.create()
}
