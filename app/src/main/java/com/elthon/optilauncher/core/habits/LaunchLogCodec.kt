package com.elthon.optilauncher.core.habits

/**
 * Codec for the launcher's own, bounded launch log.
 *
 * This records ONLY launches NoBlooks initiated — it is not a device-wide usage
 * log, it never leaves the device, and it is trimmed on every write so the file
 * cannot grow without bound. Encoding is plain "pkg|epochMs" lines.
 */
object LaunchLogCodec {

    const val MAX_ENTRIES = 300
    const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
    private const val LINE_SEP = "\n"
    private const val FIELD_SEP = "|"

    fun encode(entries: List<LocalLaunch>, nowMs: Long): String =
        trim(entries, nowMs)
            .joinToString(LINE_SEP) { "${it.packageName}$FIELD_SEP${it.epochMs}" }

    fun decode(raw: String?, nowMs: Long = System.currentTimeMillis()): List<LocalLaunch> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<LocalLaunch>()
        for (line in raw.split(LINE_SEP)) {
            if (line.isBlank()) continue
            val idx = line.lastIndexOf(FIELD_SEP)
            if (idx <= 0) continue
            val pkg = line.substring(0, idx)
            val ts = line.substring(idx + 1).toLongOrNull() ?: continue
            if (pkg.isEmpty()) continue
            out.add(LocalLaunch(pkg, ts))
        }
        return trim(out, nowMs)
    }

    /** Oldest-first, age-bounded, count-bounded. */
    fun trim(entries: List<LocalLaunch>, nowMs: Long): List<LocalLaunch> {
        val fresh = entries.filter { it.epochMs in 1..nowMs && nowMs - it.epochMs <= MAX_AGE_MS }
        if (fresh.size <= MAX_ENTRIES) return fresh.sortedBy { it.epochMs }
        return fresh.sortedBy { it.epochMs }.takeLast(MAX_ENTRIES)
    }

    fun append(existing: List<LocalLaunch>, launch: LocalLaunch, nowMs: Long): List<LocalLaunch> =
        trim(existing + launch, nowMs)
}
