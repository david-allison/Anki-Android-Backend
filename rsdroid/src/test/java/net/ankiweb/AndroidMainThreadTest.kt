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

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankiweb.rsdroid.AndroidMainThread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On a plain JVM there is no usable android.os.Looper (the unit-test android.jar
 * throws "not mocked" from getMainLooper()): detection must return false rather
 * than throw — the behaviour a pure java-library consumer relies on.
 */
class AndroidMainThreadJvmTest {
    @Test
    fun neverMainThreadWithoutAndroid() {
        assertFalse(AndroidMainThread.currentThreadIsMainThread())
    }
}

/** With a functioning Looper (Robolectric), detection must match a direct Looper call */
@RunWith(AndroidJUnit4::class)
class AndroidMainThreadRobolectricTest {
    @Test
    fun detectsMainThread() {
        // Robolectric tests run on the main looper thread
        assertTrue(AndroidMainThread.currentThreadIsMainThread())
    }

    @Test
    fun detectsNonMainThread() {
        var seenAsMainThread: Boolean? = null
        val thread = Thread { seenAsMainThread = AndroidMainThread.currentThreadIsMainThread() }
        thread.start()
        thread.join()
        assertFalse(seenAsMainThread!!)
    }
}
