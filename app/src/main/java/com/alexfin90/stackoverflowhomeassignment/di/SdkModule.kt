package com.alexfin90.stackoverflowhomeassignment.di

import com.alexfin90.stackoverflowsdk.StackOverflowSdk
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Single place where the app creates the SDK; tests replace this module with a fake. */
@Module
@InstallIn(SingletonComponent::class)
object SdkModule {

    @Provides
    @Singleton
    fun provideQuestionsRepository(): QuestionsRepository = StackOverflowSdk.questionsRepository
}
