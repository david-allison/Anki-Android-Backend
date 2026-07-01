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

import net.ankiweb.rsdroid.BackendFactory.getBackend
import net.ankiweb.rsdroid.testing.RustBackendLoader.ensureSetup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The java-library use case: the backend must create a collection and answer
 * queries on a plain JVM, with no Android classes involved anywhere. (The
 * androidx.sqlite bridge equivalent lives in :rsdroid-android under Robolectric.)
 */
class CollectionCreationJvmTest {
    @BeforeEach
    fun setUp() {
        ensureSetup()
    }

    @Test
    fun collectionOpensAndAnswersQueries() {
        getBackend().use { backend ->
            backend.openCollection(":memory:")
            val response = backend.fullQueryProto("select count(*) from col", emptyArray())
            assertEquals(1, response.rowCount)
            assertEquals(1, response.result.getRows(0).fieldsCount)
        }
    }
}
