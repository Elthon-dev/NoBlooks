# OPTI-LAUNCHER v0.1 — debug recon prototype.
# No network, no analytics, no component keeps the process alive.

# Keep the recon models readable in stack traces (report data classes are tiny).
-keep class com.elthon.optilauncher.recon.model.** { *; }
