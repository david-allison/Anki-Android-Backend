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

import anki.backend.BackendError
import net.ankiweb.rsdroid.BackendException.BackendDbException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbFileTooNewException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbFileTooOldException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbFullException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbMissingEntityException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * DB errors arrive as a [BackendError] with a message; [BackendDbException.fromDbError]
 * turns recognised messages into typed exceptions. Consumers (AnkiDroid's startup
 * error routing) rely on these types, so the sniffing rules are pinned here.
 */
class BackendExceptionTest {
    @Test
    fun diskFullIsTyped() {
        val exception = fromDbError("DbError { info: \"SqliteFailure(Error { code: DiskFull })\", kind: Other }")
        assertEquals(BackendDbFullException::class.java, exception.javaClass)
        // remains a BackendDbException: pre-existing catch sites must keep matching
        assertTrue(exception is BackendDbException)
    }

    @Test
    fun corruptionIsTyped() {
        val exception = fromDbError("DbError { info: \"SqliteFailure(Error { code: DatabaseCorrupt })\", kind: Other }")
        assertEquals(BackendDbCorruptException::class.java, exception.javaClass)
        assertTrue(exception is BackendDbException)
    }

    @Test
    fun fileTooNewIsTyped() {
        assertEquals(
            BackendDbFileTooNewException::class.java,
            fromDbError("DbError { info: \"\", kind: FileTooNew }").javaClass,
        )
    }

    @Test
    fun fileTooOldIsTyped() {
        assertEquals(
            BackendDbFileTooOldException::class.java,
            fromDbError("DbError { info: \"\", kind: FileTooOld }").javaClass,
        )
    }

    @Test
    fun missingEntityIsTyped() {
        assertEquals(
            BackendDbMissingEntityException::class.java,
            fromDbError("DbError { info: \"\", kind: MissingEntity }").javaClass,
        )
    }

    @Test
    fun lockedIsTyped() {
        val exception = fromDbError("Anki already open, or media currently syncing.")
        assertEquals(BackendDbLockedException::class.java, exception.javaClass)
        assertTrue(exception is BackendDbException)
    }

    @Test
    fun allRecognisedTypesAreDbExceptions() {
        // `catch (e: BackendDbException)` must cover every typed DB error
        for (message in listOf(
            "DbError { info: \"\", kind: FileTooNew }",
            "DbError { info: \"\", kind: FileTooOld }",
            "DbError { info: \"\", kind: MissingEntity }",
            "Anki already open, or media currently syncing.",
            "DiskFull",
            "DatabaseCorrupt",
        )) {
            assertTrue(fromDbError(message) is BackendDbException, "for message: $message")
        }
    }

    @Test
    fun lockedWinsOverSniffedMarkers() {
        // the fixed lock message is a stronger signal than substring sniffs
        assertEquals(
            BackendDbLockedException::class.java,
            fromDbError("Anki already open, or media currently syncing. DiskFull").javaClass,
        )
    }

    @Test
    fun kindOtherWithoutMarkersIsUntyped() {
        assertEquals(
            BackendDbException::class.java,
            fromDbError("DbError { info: \"something else\", kind: Other }").javaClass,
        )
    }

    @Test
    fun unknownMessageIsUntyped() {
        assertEquals(BackendDbException::class.java, fromDbError("no recognisable marker").javaClass)
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
