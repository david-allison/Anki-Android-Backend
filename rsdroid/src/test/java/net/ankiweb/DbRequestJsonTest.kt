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

import net.ankiweb.rsdroid.dbRequestJson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The DB request envelope was historically built with org.json; these goldens pin
 * the kotlinx-serialization replacement to the exact same wire format, including
 * org.json's number quirk: whole Doubles serialise without ".0", making the
 * backend bind them as INTEGER (SQLite typing is visible to queries).
 */
class DbRequestJsonTest {
    @Test
    fun envelopeMatchesOrgJsonFormat() {
        val json =
            dbRequestJson(
                "select ?, ?, ?, ?, ?, ?",
                arrayOf(null, "quote\" emoji🎴", 42L, 1.0, 2.5, true),
            ).toStringUtf8()
        assertEquals(
            """{"kind":"query","sql":"select ?, ?, ?, ?, ?, ?","args":[null,"quote\" emoji🎴",42,1,2.5,true],"first_row_only":false}""",
            json,
        )
    }

    @Test
    fun wholeDoublesBindAsIntegers() {
        val json = dbRequestJson("select ?", arrayOf(3.0)).toStringUtf8()
        assertEquals("""{"kind":"query","sql":"select ?","args":[3],"first_row_only":false}""", json)
    }

    @Test
    fun firstRowOnlyIsSet() {
        val json = dbRequestJson("select 1", emptyArray(), firstRowOnly = true).toStringUtf8()
        assertEquals("""{"kind":"query","sql":"select 1","args":[],"first_row_only":true}""", json)
    }
}
