// SPDX-License-Identifier: GPL-3.0-or-later

package net.ankiweb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.impl.TestBindingLoggerFactory

/**
 * AnkiDroid uses com.arcao:slf4j-timber which SLF4J ignores
 *
 * If this test fails after a dependency bump: keep slf4j-api on 1.7.x.
 */
class Slf4jBindingTest {
    @Test
    fun `slf4j API uses v1 Style Bindings`() {
        assertEquals(
            TestBindingLoggerFactory::class.java,
            LoggerFactory.getILoggerFactory().javaClass,
            "slf4j-api no longer binds via StaticLoggerBinder (bumped to 2.x?). " +
                "This silently breaks slf4j-timber",
        )
    }
}
