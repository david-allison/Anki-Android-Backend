/*
 * Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package net.ankiweb.rsdroid

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import anki.ankidroid.DBResponse
import anki.backend.BackendError
import anki.backend.BackendInit
import anki.backend.GeneratedBackend
import anki.generic.Int64
import com.google.protobuf.ByteString
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.InvalidProtocolBufferException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.Closeable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

open class BackendV1Impl(langs: Iterable<String> = listOf("en")) : GeneratedBackend(), BackendV1, Closeable {
    // Set on init; unset on .close(). Access via withBackend()
    private var backendPointer: Long? = null
    private val lock = ReentrantLock()
    // Only stored to satisfy .getPath() interface in SQL connection
    private var collectionPath: String? = null

    override fun isOpen(): Boolean {
        return backendPointer != null
    }


    /**
     * Open a backend instance, loading the shared library if not already loaded.
     */
    init {
        NativeMethods.ensureSetup()
        Timber.i("Opening rust backend with lang=$langs")
        val input = BackendInit.newBuilder()
                .addAllPreferredLangs(langs)
                .build()
                .toByteArray()
        val outBytes = unpackResult(NativeMethods.openBackend(input))
        backendPointer = Int64.parseFrom(outBytes).`val`
    }

    /**
     * Close the backend, and any open collection. This object can not be used after this.
     */
    override fun close() {
        Timber.i("Closing rust backend")
        lock.withLock {
            // Must be checked inside lock to avoid race
            if (backendPointer != null) {
                withBackend {
                    backendPointer = null
                    NativeMethods.closeBackend(it)
                }
            }
        }
    }

    /**
     * Open a collection. There must not already be an open collection.
     */
    override fun openCollection(collectionPath: String, mediaFolderPath: String, mediaDbPath: String, logPath: String, forceSchema11: Boolean) {
        super.openCollection(collectionPath, mediaFolderPath, mediaDbPath, logPath, forceSchema11)
        this.collectionPath = collectionPath
    }

    /**
     * Closes an open collection. There must be an open collection.
     */
    override fun closeCollection(downgradeToSchema11: Boolean) {
        cancelAllProtoQueries()
        collectionPath = null
        super.closeCollection(downgradeToSchema11)
    }

    /**
     * All backend methods (except for backend init/close) flow through this.
     */
    override fun runMethodRaw(service: Int, method: Int, input: ByteArray): ByteArray {
        return withBackend {
            unpackResult(NativeMethods.runMethodRaw(it, service, method, input))
        }
    }
    
    /**
     * Run the provided closure with locked access to the backend.
     * The backend maintains its own lock for backend commands, so this extra
     * level of locks is only useful for executing begin+sql+commit/rollback commands
     * without other commands being interleaved. When AnkiDroid has migrated to more
     * of the backend, it can probably remove this and leave the transaction handling
     * up to the backend.
     */
    private fun <T> withBackend(fn: (ptr: Long) -> T): T {
        lock.withLock {
            if (backendPointer == null) {
                throw BackendException("Backend has been closed")
            }
            return fn(backendPointer!!)
        }
    }

    // transactions hold the lock until commit/rollback

    override fun beginTransaction() {
        lock.lock()
        performTransaction(DbRequestKind.Begin)
    }

    override fun commitTransaction() {
        try {
            performTransaction(DbRequestKind.Commit)
        } finally {
            lock.unlock()
        }
    }

    override fun rollbackTransaction() {
        try {
            performTransaction(DbRequestKind.Rollback)
        } finally {
            lock.unlock()
        }
    }

    // other DB methods

    override fun closeDatabase() {
        closeCollection(false)
    }

    override fun getPath(): String {
        return collectionPath!!
    }

    @CheckResult
    override fun fullQuery(sql: String, vararg args: Any?): JSONArray {
        return try {
            Timber.i("Rust: SQL query: '%s'", sql)
            fullQueryInternal(sql, args as Array<Any?>)
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }

    @Throws(JSONException::class)
    private fun fullQueryInternal(sql: String, args: Array<Any?>): JSONArray {
        return JSONArray(runDbCommand(dbRequestJson(sql, args)).json.toString())
    }

    override fun insertForId(sql: String, args: Array<Any?>): Long {
        Timber.i("Rust: sql insert %s", sql)
        return super.insertForId(dbRequestJson(sql, args)).`val`
    }

    override fun executeGetRowsAffected(sql: String, args: Array<Any?>): Int {
        Timber.i("Rust: executeGetRowsAffected %s", sql)
        return runDbCommandForRowCount(dbRequestJson(sql, args)).`val`.toInt()
    }

    /* Begin Protobuf-based database streaming methods (#6) */
    override fun fullQueryProto(query: String, vararg args: Any?): DBResponse {
        Timber.d("Rust: fullQueryProto %s", query)
        return runDbCommandProto(dbRequestJson(query, args as Array<Any?>))
    }

    override fun getNextSlice(startIndex: Long, sequenceNumber: Int): DBResponse {
        Timber.d("Rust: getNextSlice %d", startIndex)
        return getNextResultPage(sequenceNumber, startIndex)
    }

    override fun cancelCurrentProtoQuery(sequenceNumber: Int) {
        Timber.d("cancelCurrentProtoQuery")
        flushQuery(sequenceNumber)
    }

    override fun cancelAllProtoQueries() {
        Timber.d("cancelAllProtoQueries")
        flushAllQueries()
    }

    private fun performTransaction(kind: DbRequestKind) {
        Timber.i("Rust: transaction %s", kind)
        runDbCommand(dbRequestJson(kind=kind))
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    override fun setPageSize(pageSizeInBytes: Long) {
        super.setPageSize(pageSizeInBytes)
    }

    override fun getColumnNames(sql: String): Array<String> {
        Timber.i("Rust: getColumnNames %s", sql)
        return getColumnNamesFromQuery(sql).valsList.toTypedArray()
    }
    
    //    @Override
    //    public AdBackend.SchedTimingTodayOut2 schedTimingTodayLegacy(long createdSecs, int createdMinsWest, long nowSecs, int nowMinsWest, int rolloverHour) {
    //        return ankiDroidBackend.schedTimingTodayLegacy(createdSecs, createdMinsWest, nowSecs, nowMinsWest, rolloverHour);
    //    }
    //
    //    @Override
    //    public AdBackend.LocalMinutesWestOut localMinutesWestLegacy(long collectionCreationTime) {
    //        return ankiDroidBackend.localMinutesWestLegacy(collectionCreationTime);
    //    }
    //
    //    @Override
    //    @RustCleanup("Architecture - backendPtr param is not required")
    //    public AdBackend.DebugActiveDatabaseSequenceNumbersOut debugActiveDatabaseSequenceNumbers(long backendPtr) {
    //        return ankiDroidBackend.debugActiveDatabaseSequenceNumbers(ensureBackend().toJni());
    //    }
    //
    //    @Override
    //    public void downgradeBackend(String collectionPath) {
    //        if (!new File(collectionPath).exists()) {
    //            throw new BackendException(collectionPath + " not found");
    //        }
    //
    //        ankiDroidBackend.downgradeBackend(collectionPath);
    //    }
}

/**
 * Build a JSON DB request
 */
private fun dbRequestJson(sql: String = "", args: Array<Any?> = arrayOf(), kind: DbRequestKind = DbRequestKind.Query, firstRowOnly: Boolean = false): ByteString {
    val o = JSONObject()
    o.put("kind", kind.name.lowercase())
    o.put("sql", sql)
    o.put("args", JSONArray(args))
    o.put("first_row_only", firstRowOnly)
    return ByteString.copyFromUtf8(o.toString())
}

enum class DbRequestKind {
    Query,
    Begin,
    Commit,
    Rollback,
    ExecuteMany
}

/**
 * Unpack success/error tuple from backend, and throw if error.
 */
private fun unpackResult(result: Array<ByteArray?>?): ByteArray {
    if (result == null) {
        throw BackendException("null return from backend method")
    }
    val (successBytes, errorBytes) = result
    if (errorBytes != null) {
        // convert the error to an exception
        val pbError: BackendError = try {
            BackendError.parseFrom(errorBytes)
        } catch (invalidProtocolBufferException: InvalidProtocolBufferException) {
            throw BackendException.fromException(invalidProtocolBufferException)
        }
        throw BackendException.fromError(pbError)
    } else if (successBytes != null) {
        return successBytes
    } else {
        // should not happen
        throw BackendException("both ok & err cases null")
    }
}