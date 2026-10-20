// Runs in Shizuku's shell/root process, so it can actually flip radios.
package com.elthon.optilauncher;

interface IShellService {
    // Writes the command's stdout+stderr (stdout first) and exits with 0 on success.
    String exec(String command);
}
