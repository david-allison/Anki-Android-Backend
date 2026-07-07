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

typealias CustomBackendCreator = (languages: Iterable<String>) -> Backend

/**
 * Creates [Backend] instances.
 *
 * Each [getBackend] call returns a new backend, owned by the caller:
 * [Backend.close] it when finished. The `rsdroid` native library must be loaded
 * before the first call; see [Backend] for platform specifics.
 */
object BackendFactory {
    /** To remove in 2.1.67 update */
    @JvmStatic
    @Suppress("unused")
    var defaultLegacySchema: Boolean = false

    /**
     * The language(s) the backend uses for translations and error messages when
     * [getBackend] is called without an explicit list.
     */
    var defaultLanguages: Iterable<String> = listOf("en")

    @JvmStatic
    private var backendForTesting: CustomBackendCreator? = null

    /**
     * Returns a new [Backend] using [languages] (or [defaultLanguages]) for
     * translations, unless a test override was installed via [setOverride].
     */
    @JvmStatic
    @JvmOverloads
    fun getBackend(languages: Iterable<String>? = null): Backend {
        val langs = languages ?: defaultLanguages
        return backendForTesting?.invoke(langs) ?: Backend(
            langs,
        )
    }

    /** Allows overriding the returned backend for unit tests */
    fun setOverride(creator: CustomBackendCreator?) {
        backendForTesting = creator
    }
}
