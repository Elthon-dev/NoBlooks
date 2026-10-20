# OPTI-LAUNCHER v0.1 — debug recon prototype.
# No network, no analytics, no component keeps the process alive.

# Keep the recon models readable in stack traces (report data classes are tiny).
-keep class com.elthon.optilauncher.recon.model.** { *; }

# Shizuku instantiates the user service by name in a foreign process and talks to it
# through the generated AIDL stub, so keep both ends intact under R8.
-keep class com.elthon.optilauncher.data.ShellUserService { *; }
-keep class com.elthon.optilauncher.IShellService { *; }
-keep class com.elthon.optilauncher.IShellService$Stub { *; }
