// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

/** The one thing collectors and actions need from a connection. Faked in tests. */
fun interface AdbShell {
    /** Runs [command] on the device and returns stdout. Throws [java.io.IOException] if the link is gone. */
    suspend fun shell(command: String): ShellResult
}

data class ShellResult(val output: String, val exitCode: Int)

/** An open connection to one device. */
interface AdbSession : AdbShell, AutoCloseable {
    /** Writes [bytes] to [remotePath] on the device, mode 0644. Throws [java.io.IOException] if the link is gone. */
    suspend fun push(bytes: ByteArray, remotePath: String)
}
