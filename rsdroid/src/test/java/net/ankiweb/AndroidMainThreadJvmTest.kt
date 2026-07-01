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

import net.ankiweb.rsdroid.AndroidMainThread
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * On a plain JVM there is no usable android.os.Looper: detection must return
 * false rather than throw — the behaviour a pure java-library consumer relies on.
 *
 * The positive (Android) path is covered by :rsdroid-android's
 * MainThreadWarningTest under Robolectric, and by :rsdroid-instrumented on
 * devices; AndroidMainThread is internal, so it cannot be tested directly from
 * another module.
 */
class AndroidMainThreadJvmTest {
    @Test
    fun neverMainThreadWithoutAndroid() {
        assertFalse(AndroidMainThread.currentThreadIsMainThread())
    }
}
