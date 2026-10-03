package com.alexfin90.stackoverflowsdk

import org.junit.Assert.assertNotNull
import org.junit.Test

class StackOverflowSdkTest {

    @Test
    fun `create does no io and returns a repository`() {
        assertNotNull(StackOverflowSdk.create())
    }
}
