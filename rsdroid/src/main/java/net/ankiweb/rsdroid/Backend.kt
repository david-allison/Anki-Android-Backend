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
import anki.ankidroid.DbResponse
import anki.backend.BackendError
import anki.backend.BackendInit
import anki.backend.GeneratedBackend
import anki.generic.Int64
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.ankiweb.rsdroid.database.NotImplementedException
import net.ankiweb.rsdroid.database.SQLHandler
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val logger = LoggerFactory.getLogger(Backend::class.java)

/**
 * A handle to an instance of the Anki backend: Anki Desktop's Rust core, driven
 * over JNI. A backend can open at most one collection at a time.
 *
 * Obtain instances via [BackendFactory.getBackend]. The `rsdroid` native library
 * must have been loaded first: on Android, load the copy packaged in the
 * `anki-android-backend-android` AAR with `System.loadLibrary("rsdroid")`; on a
 * desktop JVM, use `RustBackendLoader` from `anki-android-backend-testing`, or
 * load a host build of `librsdroid` yourself.
 *
 * Most of the API is inherited from [GeneratedBackend], generated from the
 * service definitions in `anki/proto/anki`. Calls are blocking and should be
 * dispatched off the main thread; enable [checkOperationsRunOnMainThread] to log
 * offenders during development.
 *
 * Backends are [Closeable]: [close] releases the native instance, after which
 * this object must not be used.
 *
 * @param langs the language(s) used for translations and error messages,
 * see [BackendFactory.defaultLanguages]
 */
open class Backend(
    langs: Iterable<String> = listOf("en"),
) : GeneratedBackend(),
    SQLHandler,
    Closeable {
    // Set on init; unset on .close(). Access via withBackend()
    private var backendPointer: Long? = null

    /**
     * AnkiDroid#21455): Ensures [close] cannot free the backend during a [runMethodRaw] call.
     */
    private val backendLock = ReentrantReadWriteLock()

    val tr: Translations by lazy {
        Translations(this)
    }

    fun isOpen(): Boolean = backendPointer != null

    /**
     * Open a collection. There must not already be an open collection.
     *
     * @throws BackendException.BackendDbException
     * @throws BackendException.BackendDbException.BackendDbLockedException
     * @throws BackendException.BackendDbException.BackendDbFileTooNewException
     * @throws BackendException.BackendDbException.BackendDbFileTooOldException
     * @throws BackendException.BackendDbException.BackendDbMissingEntityException
     * @throws BackendException.BackendDbException.BackendDbFullException
     * @throws BackendException.BackendDbException.BackendDbCorruptException
     */
    fun openCollection(collectionPath: String) {
        val (mediaFolder, mediaDb) =
            if (collectionPath == ":memory:") {
                listOf("", "")
            } else {
                listOf(
                    collectionPath.replace(".anki2", ".media"),
                    collectionPath.replace(".anki2", ".media.db"),
                )
            }
        openCollection(collectionPath, mediaFolder, mediaDb)
    }

    /** Forces a full media check on next sync. Only valid with new backend. */
    @Suppress("unused") // used in AnkiDroid
    fun removeMediaDb(colPath: String) {
        val file = File(colPath.replace(".anki2", ".media.db"))
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Open a backend instance. The native library must already be loaded:
     * see the class documentation.
     */
    init {
        logger.debug("Opening rust backend with lang={}", langs)
        val input =
            BackendInit
                .newBuilder()
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
        logger.debug("Closing rust backend")
        backendLock.write {
            NativeMethods.closeBackend(backendPointer!!)
            backendPointer = null
        }
    }

    /**
     * Closes an open collection. There must be an open collection.
     */
    override fun closeCollection(downgradeToSchema11: Boolean) {
        cancelAllProtoQueries()
        super.closeCollection(downgradeToSchema11)
    }

    /**
     * All backend methods (except for backend init/close) flow through this.
     */
    override fun runMethodRaw(
        service: Int,
        method: Int,
        input: ByteArray,
    ): ByteArray =
        withBackend {
            unpackResult(NativeMethods.runMethodRaw(it, service, method, input))
        }

    /**
     * Run the provided closure with access to the backend.
     * @throws BackendException if backend closed.
     */
    private fun <T> withBackend(fn: (ptr: Long) -> T): T =
        backendLock.read {
            val pointer = backendPointer ?: throw BackendException("Backend has been closed")
            fn(pointer)
        }

    // other DB methods

    override fun closeDatabase(): Unit = throw NotImplementedException("should close collection, not db")

    override fun getPath(): String? = throw NotImplementedException()

    @CheckResult
    override fun fullQuery(
        query: String,
        bindArgs: Array<Any?>?,
    ): JsonArray {
        val output = runDbCommand(dbRequestJson(query, bindArgs ?: emptyArray())).toStringUtf8()
        return Json.parseToJsonElement(output).jsonArray
    }

    override fun insertForId(
        sql: String,
        bindArgs: Array<Any?>?,
    ): Long = super.insertForId(dbRequestJson(sql, bindArgs ?: emptyArray()))

    override fun executeGetRowsAffected(
        sql: String,
        bindArgs: Array<Any?>?,
    ): Int = runDbCommandForRowCount(dbRequestJson(sql, bindArgs ?: emptyArray())).toInt()

    // Begin Protobuf-based database streaming methods (#6)
    override fun fullQueryProto(
        query: String,
        bindArgs: Array<out Any?>,
    ): DbResponse = runDbCommandProto(dbRequestJson(query, bindArgs))

    override fun getNextSlice(
        startIndex: Long,
        sequenceNumber: Int,
    ): DbResponse = getNextResultPage(sequenceNumber, startIndex)

    override fun cancelCurrentProtoQuery(sequenceNumber: Int) {
        flushQuery(sequenceNumber)
    }

    override fun cancelAllProtoQueries() {
        flushAllQueries()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun setPageSize(pageSizeBytes: Long) {
        super.setPageSize(pageSizeBytes)
    }

    override fun getColumnNames(sql: String): Array<String> = getColumnNamesFromQuery(sql).toTypedArray()

    companion object {
        const val MAX_MEDIA_FILENAME_LENGTH = 120

        const val MAX_MEDIA_FILENAME_LENGTH_SERVER = 255

        const val MAX_INDIVIDUAL_MEDIA_FILE_SIZE = 100 * 1024 * 1024L
    }
}

/**
 * Build a JSON DB request.
 *
 * TODO: consider a typed protobuf request upstream: the RunDbCommand* RPCs in
 *  anki/proto/anki/ankidroid.proto take generic.Json. That would remove the JSON
 *  encode/parse on every statement, and would allow binding arbitrary blob bytes.
 */
internal fun dbRequestJson(
    sql: String = "",
    bindArgs: Array<out Any?> = emptyArray(),
    firstRowOnly: Boolean = false,
): ByteString {
    val request =
        buildJsonObject {
            put("kind", "query")
            put("sql", sql)
            putJsonArray("args") { bindArgs.forEach { add(it.toBindArgJson()) } }
            put("first_row_only", firstRowOnly)
        }
    return ByteString.copyFromUtf8(request.toString())
}

/**
 * Matches the serialisation of Android's org.json (previously used here), because
 * SQLite typing is query-visible: a Double/Float equal to a whole number
 * serialises without a fraction — even above 1e7, where Double.toString switches
 * to E-notation — and the backend binds it as INTEGER rather than REAL. Number
 * types org.json didn't whitelist (e.g. BigDecimal) fall through to quoted
 * strings, exactly as its wrap() emitted them.
 */
@OptIn(ExperimentalSerializationApi::class)
private fun Any?.toBindArgJson(): JsonElement =
    when (this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        // Preserve org.json's signed-byte array encoding. The backend accepts
        // bytes 0..127 as blobs and rejects negative values, as before.
        is ByteArray -> JsonArray(map { JsonPrimitive(it) })
        is Double, is Float -> {
            val value = (this as Number).toDouble()
            require(!value.isNaN() && !value.isInfinite()) {
                "JSON does not allow non-finite numbers: $value"
            }
            val asLong = value.toLong()
            val literal =
                when {
                    // org.json's NEGATIVE_ZERO case: Double only, Float falls through
                    this is Double && equals(-0.0) -> "-0"
                    value == asLong.toDouble() -> asLong.toString()
                    else -> toString()
                }
            JsonUnquotedLiteral(literal)
        }
        is Int, is Long, is Short, is Byte -> JsonUnquotedLiteral(toString())
        else -> JsonPrimitive(toString())
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
        val pbError: BackendError =
            try {
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
