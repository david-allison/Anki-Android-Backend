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
package org.slf4j.impl

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import org.slf4j.helpers.MessageFormatter
import org.slf4j.helpers.NOPLogger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Copy of :rsdroid's test binding (test classpaths cannot be shared across modules
 * without publishing test fixtures).
 *
 * A minimal SLF4J 1.7-style binding: slf4j-api 1.7.x discovers it by this exact
 * fully-qualified name and calls [getSingleton] to obtain the logger factory.
 *
 * This serves two purposes:
 * 1. keeps unit-test logging silent (the role slf4j-nop would otherwise play)
 * 2. lets `Slf4jBindingTest` assert that 1.7-style bindings are honoured:
 *    AnkiDroid routes this library's logging to Timber through one
 *    (com.arcao:slf4j-timber), and SLF4J 2.x silently ignores them
 */
class StaticLoggerBinder private constructor() {
    val loggerFactory: ILoggerFactory = TestBindingLoggerFactory()

    fun getLoggerFactoryClassStr(): String = TestBindingLoggerFactory::class.java.name

    companion object {
        private val SINGLETON = StaticLoggerBinder()

        @JvmStatic
        fun getSingleton(): StaticLoggerBinder = SINGLETON

        // read via a static field reference by slf4j-api's version sanity check
        @JvmField
        val REQUESTED_API_VERSION: String = "1.7.36"
    }
}

/**
 * Discards all log output like slf4j-nop — but with a distinct type tests can
 * assert on, and recording warnings so tests can assert diagnostics actually fire.
 */
class TestBindingLoggerFactory : ILoggerFactory {
    override fun getLogger(name: String): Logger = WarningRecordingLogger

    companion object {
        /** Formatted messages of every [Logger.warn] call since [clearWarnings] */
        val warnings = CopyOnWriteArrayList<String>()

        fun clearWarnings() = warnings.clear()
    }
}

/** Silent, except warnings are recorded into [TestBindingLoggerFactory.warnings] */
private object WarningRecordingLogger : Logger by NOPLogger.NOP_LOGGER {
    override fun warn(msg: String) {
        TestBindingLoggerFactory.warnings.add(msg)
    }

    override fun warn(
        format: String,
        arg: Any?,
    ) {
        TestBindingLoggerFactory.warnings.add(MessageFormatter.format(format, arg).message)
    }

    override fun warn(
        format: String,
        arg1: Any?,
        arg2: Any?,
    ) {
        TestBindingLoggerFactory.warnings.add(MessageFormatter.format(format, arg1, arg2).message)
    }

    override fun warn(
        format: String,
        vararg arguments: Any?,
    ) {
        TestBindingLoggerFactory.warnings.add(MessageFormatter.arrayFormat(format, arguments).message)
    }
}
