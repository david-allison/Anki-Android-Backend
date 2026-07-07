package net.ankiweb.rsdroid

import androidx.annotation.CheckResult

/**
 * JNI entry points implemented by the `rsdroid` native library; [Backend] wraps
 * them. The JVM binds these lazily by name, so the library must have been loaded
 * (see [Backend]) before the first call.
 */
internal object NativeMethods {
    @CheckResult
    external fun runMethodRaw(
        backendPointer: Long,
        service: Int,
        method: Int,
        args: ByteArray,
    ): Array<ByteArray?>?

    @CheckResult
    external fun openBackend(data: ByteArray): Array<ByteArray?>?

    external fun closeBackend(backendPointer: Long)
}
