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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * The DB request envelope was historically built with Android's org.json; these
 * goldens pin the kotlinx-serialization replacement to the same wire format,
 * including org.json's number quirk: a Double equal to a whole number serialises
 * without a fraction, making the backend bind it as INTEGER (SQLite typing is
 * visible to queries).
 *
 * One deliberate byte-level divergence: org.json escaped '/' as '\/'; kotlinx
 * does not. serde parses both identically, so the wire is compatible.
 */
class DbRequestJsonTest {
    @Test
    fun envelopeMatchesOrgJsonFormat() {
        // note: booleans serialise for format parity, but the backend has no
        // SqlValue::Bool and rejects them at bind time (as it always has)
        val json =
            dbRequestJson(
                "select ?, ?, ?, ?, ?, ?",
                arrayOf<Any?>(null, "quote\" emoji🎴", 42L, 1.0, 2.5, true),
            ).toStringUtf8()
        assertEquals(
            """{"kind":"query","sql":"select ?, ?, ?, ?, ?, ?","args":[null,"quote\" emoji🎴",42,1,2.5,true],"first_row_only":false}""",
            json,
        )
    }

    @Test
    fun wholeDoublesBindAsIntegers() {
        val json = dbRequestJson("select ?", arrayOf<Any?>(3.0)).toStringUtf8()
        assertEquals("""{"kind":"query","sql":"select ?","args":[3],"first_row_only":false}""", json)
    }

    @Test
    fun wholeDoublesAboveTenMillionStillBindAsIntegers() {
        // Double.toString switches to E-notation at 1e7, but org.json serialised
        // whole values integrally at any magnitude - and epoch millis live up here
        val json =
            dbRequestJson(
                "select ?, ?, ?",
                arrayOf<Any?>(1.0E7, 1751234567890.0, 9.2233720368547758E18),
            ).toStringUtf8()
        assertEquals(
            """{"kind":"query","sql":"select ?, ?, ?","args":[10000000,1751234567890,9223372036854775807],"first_row_only":false}""",
            json,
        )
    }

    @Test
    fun floatsSerialiseLikeDoubles() {
        val json = dbRequestJson("select ?, ?, ?", arrayOf<Any?>(1.5f, 2.0f, 1.0E8f)).toStringUtf8()
        assertEquals(
            """{"kind":"query","sql":"select ?, ?, ?","args":[1.5,2,100000000],"first_row_only":false}""",
            json,
        )
    }

    @Test
    fun negativeZeroMatchesOrgJson() {
        // org.json special-cased Double -0.0; Float -0.0f fell through to the
        // whole-number path and serialised as 0
        val json = dbRequestJson("select ?, ?", arrayOf<Any?>(-0.0, -0.0f)).toStringUtf8()
        assertEquals("""{"kind":"query","sql":"select ?, ?","args":[-0,0],"first_row_only":false}""", json)
    }

    @Test
    fun longExtremes() {
        val json =
            dbRequestJson(
                "select ?, ?, ?",
                arrayOf<Any?>(-1L, Long.MAX_VALUE, Long.MIN_VALUE),
            ).toStringUtf8()
        assertEquals(
            """{"kind":"query","sql":"select ?, ?, ?","args":[-1,9223372036854775807,-9223372036854775808],"first_row_only":false}""",
            json,
        )
    }

    @Test
    fun escapingMatchesOrgJsonSemantics() {
        val json =
            dbRequestJson(
                "select ?, ?",
                arrayOf<Any?>("a/b", "line\nbreak\ttab\\bs\u0001"),
            ).toStringUtf8()
        assertEquals(
            """{"kind":"query","sql":"select ?, ?","args":["a/b","line\nbreak\ttab\\bs\u0001"],"first_row_only":false}""",
            json,
        )
    }

    @Test
    fun nonFiniteNumbersAreRejected() {
        for (value in listOf<Any>(Double.NaN, Double.POSITIVE_INFINITY, Float.NaN)) {
            assertThrows(IllegalArgumentException::class.java) {
                dbRequestJson("select ?", arrayOf<Any?>(value))
            }
        }
    }

    @Test
    fun blobsAreRejected() {
        // the protocol cannot represent blobs: failing beats binding "[B@..." TEXT
        assertThrows(IllegalArgumentException::class.java) {
            dbRequestJson("select ?", arrayOf<Any?>(byteArrayOf(1, 2)))
        }
    }

    @Test
    fun firstRowOnlyIsSet() {
        val json = dbRequestJson("select 1", emptyArray(), firstRowOnly = true).toStringUtf8()
        assertEquals("""{"kind":"query","sql":"select 1","args":[],"first_row_only":true}""", json)
    }
}
