package com.alexfin90.stackoverflowsdk.api

import org.junit.Assert.assertEquals
import org.junit.Test

class StackOverflowConfigTest {

    @Test
    fun `defaults match the assignment requirements`() {
        val config = StackOverflowConfig()
        assertEquals("https://api.stackexchange.com/", config.baseUrl)
        assertEquals(30, config.lastDays)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `base url without trailing slash is rejected`() {
        StackOverflowConfig(baseUrl = "https://example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `page size above api limit is rejected`() {
        StackOverflowConfig(pageSize = 101)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non positive window is rejected`() {
        StackOverflowConfig(lastDays = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `call timeout shorter than connect timeout is rejected`() {
        StackOverflowConfig(connectTimeoutSeconds = 20, callTimeoutSeconds = 10)
    }
}
