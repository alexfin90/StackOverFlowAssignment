package com.alexfin90.stackoverflowsdk

import com.alexfin90.stackoverflowsdk.api.StackOverflowConfig
import com.alexfin90.stackoverflowsdk.domain.LogLevel
import com.alexfin90.stackoverflowsdk.domain.StackOverflowLogger
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

    @Before
    @After
    fun reset() = StackOverflowSdk.resetForTesting()

    private class RecordingLogger : StackOverflowLogger {
        val lines = mutableListOf<Pair<LogLevel, String>>()
        override fun log(level: LogLevel, message: String) {
            lines += level to message
        }
    }

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
    fun `ignored initialize logs a warning to the active logger`() {
        val logger = RecordingLogger()
        StackOverflowSdk.initialize(StackOverflowConfig(loggingEnabled = true, logger = logger))

        StackOverflowSdk.initialize(StackOverflowConfig())

        assertEquals(1, logger.lines.size)
        assertEquals(LogLevel.WARN, logger.lines.single().first)
    }

    @Test
    fun `ignored initialize is silent when logging is disabled`() {
        val logger = RecordingLogger()
        StackOverflowSdk.initialize(StackOverflowConfig(loggingEnabled = false, logger = logger))

        StackOverflowSdk.initialize(StackOverflowConfig())

        assertTrue(logger.lines.isEmpty())
    }

    @Test
    fun `a throwing logger does not break initialize`() {
        StackOverflowSdk.initialize(
            StackOverflowConfig(loggingEnabled = true, logger = { _, _ -> error("boom") }),
        )

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
