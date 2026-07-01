/*
 * Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.ankiweb

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory.getBackend
import net.ankiweb.rsdroid.testing.RustBackendLoader.ensureSetup
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.impl.TestBindingLoggerFactory

/**
 * [Backend.checkOperationsRunOnMainThread] is a debug-only diagnostic: when
 * enabled, a backend operation executed on the Android main thread logs a
 * warning identifying the caller — and must never throw.
 *
 * Robolectric tests run on the Robolectric main (looper) thread, so every
 * backend call in this test is an operation "on the UI thread".
 */
@RunWith(AndroidJUnit4::class)
class MainThreadWarningTest {
    @Before
    fun setUp() {
        ensureSetup()
        TestBindingLoggerFactory.clearWarnings()
        Backend.checkOperationsRunOnMainThread = true
    }

    @After
    fun tearDown() {
        // global flag: leaking it would make unrelated tests log warnings
        Backend.checkOperationsRunOnMainThread = false
    }

    @Test
    fun warnsOnMainThreadOperations() {
        getBackend().use { backend ->
            backend.openCollection(":memory:")
            backend.fullQuery("select 1", null)
        }
        assertTrue(
            "expected an 'Op on UI thread' warning, got: ${TestBindingLoggerFactory.warnings}",
            TestBindingLoggerFactory.warnings.any { it.startsWith("Op on UI thread") },
        )
        assertTrue(
            "expected the offending SQL to be logged",
            TestBindingLoggerFactory.warnings.contains("select 1"),
        )
    }
}
