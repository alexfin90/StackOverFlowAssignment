package com.alexfin90.stackoverflowhomeassignment.di

import com.alexfin90.stackoverflowhomeassignment.fake.FakeQuestionsPagerFactory
import com.alexfin90.stackoverflowhomeassignment.fake.FakeQuestionsRepository
import com.alexfin90.stackoverflowsdk.domain.QuestionsPagerFactory
import com.alexfin90.stackoverflowsdk.domain.QuestionsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `mock` flavor: the app runs on in-memory data and never touches the SDK networking.
 * Useful offline, for demos and for UI work.
 */
@Module
@InstallIn(SingletonComponent::class)
object SdkModule {

    @Provides
    @Singleton
    fun provideQuestionsRepository(): QuestionsRepository = FakeQuestionsRepository()

    @Provides
    @Singleton
    fun provideQuestionsPagerFactory(): QuestionsPagerFactory = FakeQuestionsPagerFactory()
}
