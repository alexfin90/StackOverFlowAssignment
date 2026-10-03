package com.alexfin90.stackoverflowsdk.data.mapper

import com.alexfin90.stackoverflowsdk.data.remote.OwnerDto
import com.alexfin90.stackoverflowsdk.data.remote.QuestionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuestionMapperTest {

    @Test
    fun `decodes numeric and named html entities`() {
        assertEquals("Don't use \"x\" & <y>", "Don&#39;t use &quot;x&quot; &amp; &lt;y&gt;".decodeHtmlEntities())
    }

    @Test
    fun `decodes hex entities`() {
        assertEquals("it's", "it&#x27;s".decodeHtmlEntities())
    }

    @Test
    fun `does not double decode`() {
        assertEquals("&lt;", "&amp;lt;".decodeHtmlEntities())
    }

    @Test
    fun `maps dto to domain`() {
        val question = QuestionDto(
            questionId = 42,
            title = "What&#39;s new?",
            link = "https://stackoverflow.com/q/42",
            creationDate = 1_700_000_000,
            answerCount = 0,
            score = 3,
            owner = OwnerDto("Jane &amp; John"),
            tags = listOf("kotlin"),
        ).toDomain()

        assertEquals(42L, question.id)
        assertEquals("What's new?", question.title)
        assertEquals("Jane & John", question.ownerName)
        assertEquals(listOf("kotlin"), question.tags)
    }

    @Test
    fun `missing owner maps to null`() {
        val question = QuestionDto(1, "t", "l", 0).toDomain()
        assertNull(question.ownerName)
    }
}
