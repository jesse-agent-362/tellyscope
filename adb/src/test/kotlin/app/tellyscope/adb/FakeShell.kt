// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

/** Answers by the first matching command prefix and records what ran. */
class FakeShell(private val answers: Map<String, ShellResult> = emptyMap()) : AdbSession {
    val ran = mutableListOf<String>()
    var closed = false
    var failWith: Exception? = null

    override suspend fun shell(command: String): ShellResult {
        failWith?.let { throw it }
        ran += command
        return answers.entries.firstOrNull { command.startsWith(it.key) }?.value ?: ShellResult("", 0)
    }

    override fun close() { closed = true }
}

fun ok(output: String) = ShellResult(output, 0)
