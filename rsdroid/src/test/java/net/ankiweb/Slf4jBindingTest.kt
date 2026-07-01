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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.impl.TestBindingLoggerFactory

/**
 * Guards the slf4j-api major version.
 *
 * AnkiDroid routes this library's logging to Timber via com.arcao:slf4j-timber,
 * a 1.7-style binding (an `org.slf4j.impl.StaticLoggerBinder` on the classpath).
 * SLF4J 2.x discovers providers via ServiceLoader instead and silently ignores
 * 1.7-style bindings, so publishing a dependency on slf4j-api 2.x would disable
 * all of this library's (and AnkiDroid's other) SLF4J logging in the app.
 *
 * If this test fails after a dependency bump: keep slf4j-api on 1.7.x, or first
 * coordinate AnkiDroid's move to a ServiceLoader-based Timber provider.
 */
class Slf4jBindingTest {
    @Test
    fun slf4jApiHonours17StyleBindings() {
        assertEquals(
            TestBindingLoggerFactory::class.java,
            LoggerFactory.getILoggerFactory().javaClass,
            "slf4j-api no longer binds via StaticLoggerBinder (bumped to 2.x?). " +
                "This silently breaks AnkiDroid's slf4j-timber bridge - see the class KDoc",
        )
    }
}
