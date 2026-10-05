package com.alexfin90.stackoverflowsdk

import com.alexfin90.stackoverflowsdk.api.StackOverflowConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class StackOverflowSdkTest {

    private val warnings = mutableListOf<String>()

    // One @Before: the order of several @Before methods is not guaranteed in JUnit 4.
    @Before
    fun setUp() {
        StackOverflowSdk.resetForTesting()
        StackOverflowSdk.warningSink = { warnings += it }
    }

    @After
    fun tearDown() = StackOverflowSdk.resetForTesting()

    @Test
    fun `create does no io and returns a repository`() {
        assertNotNull(StackOverflowSdk.create())
    }

    @Test
    fun `create returns independent instances`() {
        assertNotSame(StackOverflowSdk.create(), StackOverflowSdk.create())
    }

    @Test
    fun `repository without initialize uses the defaults and does not throw`() {
        assertNotNull(StackOverflowSdk.questions)
    }

    @Test
    fun `repository is a single shared instance`() {
        assertSame(StackOverflowSdk.questions, StackOverflowSdk.questions)
    }

    @Test
    fun `first initialize is applied`() {
        assertTrue(StackOverflowSdk.initialize(StackOverflowConfig(pageSize = 10)))
    }

    @Test
    fun `second initialize is ignored`() {
        StackOverflowSdk.initialize(StackOverflowConfig(pageSize = 10))

        assertFalse(StackOverflowSdk.initialize(StackOverflowConfig(pageSize = 50)))
    }

    @Test
    fun `initialize after the repository was used is ignored`() {
        StackOverflowSdk.questions

        assertFalse(StackOverflowSdk.initialize(StackOverflowConfig(pageSize = 10)))
    }

    @Test
    fun `ignored initialize logs a warning when logging is enabled`() {
        StackOverflowSdk.initialize(StackOverflowConfig(loggingEnabled = true))

        StackOverflowSdk.initialize(StackOverflowConfig())

        assertEquals(1, warnings.size)
    }

    @Test
    fun `ignored initialize is silent when logging is disabled`() {
        StackOverflowSdk.initialize(StackOverflowConfig(loggingEnabled = false))

        StackOverflowSdk.initialize(StackOverflowConfig())

        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `a failing log sink does not break initialize`() {
        StackOverflowSdk.warningSink = { error("boom") }
        StackOverflowSdk.initialize(StackOverflowConfig(loggingEnabled = true))

        assertFalse(StackOverflowSdk.initialize(StackOverflowConfig()))
    }

    @Test
    fun `warmUp is idempotent and builds the shared instance`() {
        StackOverflowSdk.warmUp()
        val first = StackOverflowSdk.questions
        StackOverflowSdk.warmUp()

        assertSame(first, StackOverflowSdk.questions)
    }

    @Test
    fun `concurrent access builds a single instance`() {
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val futures = (1..8).map {
            executor.submit<Any> {
                start.await()
                StackOverflowSdk.questions
            }
        }
        start.countDown()

        val instances = futures.map { it.get() }.toSet()
        executor.shutdown()

        assertEquals(1, instances.size)
    }
}
