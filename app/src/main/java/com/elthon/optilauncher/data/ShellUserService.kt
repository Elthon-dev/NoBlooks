package com.elthon.optilauncher.data

import com.elthon.optilauncher.IShellService
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Runs inside the process Shizuku starts for us, as shell (uid 2000) or root. That is
 * the whole point: here `svc wifi enable` and `svc data enable` are not theatre, they
 * actually flip the radios — which a normal app process can never do.
 *
 * Keep this class tiny and dependency-free: it is loaded by a foreign process that
 * knows nothing about our app classes beyond what Shizuku hands over.
 */
class ShellUserService : IShellService.Stub() {

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.forEachLine { output.append(it).append('\n') }
            }
            process.waitFor()
            output.toString().trim()
        } catch (t: Throwable) {
            "error: ${t.message}"
        }
    }

}
