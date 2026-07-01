// SPDX-License-Identifier: GPL-3.0-or-later
package net.ankiweb

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendFactory.getBackend
import net.ankiweb.rsdroid.testing.RustBackendLoader.ensureSetup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Ensures [Backend.close] waits for in-flight calls.
 *
 * Caused a use-after-free which SIGSEGVs/SIGABRTed the JVM (exit code 134).
 *
 * https://github.com/ankidroid/Anki-Android/issues/21455
 */
class BackendCloseRaceTest {
    @BeforeEach
    fun loadLibrary() {
        ensureSetup()
    }

    @Test
    fun closeDoesNotInterruptInFlightCalls() {
        val backend = getBackend()
        backend.openCollection(":memory:")
        val warmup = backend.fullQuery(longQuery(rows = 1_000))
        assertEquals(1_000L, warmup[0].jsonArray[0].jsonPrimitive.long)

        var queryError: Exception? = null
        var queryCount: Long? = null
        val queryThread =
            thread(name = "backend-slow-query") {
                try {
                    // keeps the backend busy inside a single native call for over a second
                    val result = backend.fullQuery(longQuery(rows = 50_000_000))
                    queryCount = result[0].jsonArray[0].jsonPrimitive.long
                } catch (e: Exception) {
                    queryError = e
                }
            }

        sleep(500.milliseconds) // let the query enter native code
        backend.close()
        queryThread.join(1.minutes)
        assertFalse(queryThread.isAlive, "query thread did not finish")

        // Acceptable outcomes:
        // * close() waited for the in-flight call: the query succeeds.
        // * the call lost the race and was cleanly rejected.
        if (queryError != null) {
            assertTrue(queryError is BackendException, "unexpected query error: $queryError")
            assertEquals("Backend has been closed", queryError.message)
        } else {
            assertEquals(50_000_000L, queryCount)
        }
    }

    /**
     * SQLite has no sleep(), so counting a generated series is a simple busy-loop.
     */
    private fun longQuery(rows: Int) =
        "WITH RECURSIVE c(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM c WHERE x < $rows) " +
            "SELECT count(*) FROM c"
}

private fun sleep(duration: Duration) = Thread.sleep(duration.inWholeMilliseconds)

private fun Thread.join(timeout: Duration) = join(timeout.inWholeMilliseconds)
