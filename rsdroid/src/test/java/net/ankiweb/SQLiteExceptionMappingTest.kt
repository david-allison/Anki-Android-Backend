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

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.BackendError
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendException.BackendDbException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the BackendException -> android.database.sqlite exception mapping used by
 * the androidx.sqlite bridge. Robolectric-based: the android exception classes
 * cannot be constructed against the non-mocked android.jar.
 */
@RunWith(AndroidJUnit4::class)
class SQLiteExceptionMappingTest {
    @Test
    fun diskFullMapsToSQLiteFullException() {
        val mapped = fromDbError("DbError { info: \"DiskFull\", kind: Other }").toSQLiteException("select 1")
        assertEquals(SQLiteFullException::class.java, mapped.javaClass)
    }

    @Test
    fun corruptionMapsToSQLiteDatabaseCorruptException() {
        val message = "DbError { info: \"DatabaseCorrupt\", kind: Other }"
        val mapped = fromDbError(message).toSQLiteException("select 1")
        assertEquals(SQLiteDatabaseCorruptException::class.java, mapped.javaClass)
        assertEquals("error while compiling: \"select 1\": $message", mapped.message)
    }

    @Test
    fun constraintViolationMapsToSQLiteConstraintException() {
        val mapped = fromDbError("ConstraintViolation oops").toSQLiteException("insert into x")
        assertEquals(SQLiteConstraintException::class.java, mapped.javaClass)
    }

    @Test
    fun invalidParameterCountMapsToIllegalArgumentException() {
        val mapped = fromDbError("InvalidParameterCount(1, 2)").toSQLiteException("select ?")
        assertEquals(IllegalArgumentException::class.java, mapped.javaClass)
        assertEquals(
            "Cannot bind argument at index 1 because the index is out of range.  The statement has 2 parameters.",
            mapped.message,
        )
    }

    @Test
    fun unknownDbErrorMapsToSQLiteException() {
        val mapped = fromDbError("something unrecognised").toSQLiteException("select 1")
        assertEquals(SQLiteException::class.java, mapped.javaClass)
        assertEquals("error while compiling: \"select 1\": something unrecognised", mapped.message)
    }

    @Test
    fun nonDbBackendExceptionMapsToSQLiteException() {
        val mapped = BackendException("boom").toSQLiteException("select 1")
        assertEquals(SQLiteException::class.java, mapped.javaClass)
        assertEquals("error while compiling: \"select 1\": boom", mapped.message)
    }

    private fun fromDbError(message: String) =
        BackendDbException.fromDbError(
            BackendError
                .newBuilder()
                .setKind(BackendError.Kind.DB_ERROR)
                .setMessage(message)
                .build(),
        )
}
