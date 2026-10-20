package com.elthon.optilauncher.core.util

/**
 * Which OEM ROMs are known to freeze or kill third-party launchers in the
 * background, and therefore where the user should be pointed to exempt this app.
 *
 * This is pure and tiny on purpose: it makes the detection unit-testable and keeps
 * the Android-specific intent wiring in the data layer. It encodes *known public
 * behaviour*, not a guess — the ColorOS/OxygenOS home-return delay is documented by
 * launcher maintainers (Niagara, Nova, Neo-Launcher) and by dontkillmyapp.com, and
 * every one of those reports says the same thing: it is the system's home transition
 * plus aggressive process killing, not the launcher's own rendering.
 *
 * It deliberately does **not** try to detect whether this app is currently exempt.
 * That state is not readable through any supported API, and claiming it would be a
 * fabricated signal. The UI says "may" and offers the settings screen; it never
 * asserts the exemption is on or off.
 */
enum class OemBackgroundPolicy {
    /** ColorOS / Realme UI / OxygenOS family (OPPO, OnePlus, Realme): the aggressive one. */
    COLOROS_FAMILY,

    /** Other ROMs with known aggressive battery managers, for a softer hint. */
    OTHER_AGGRESSIVE,

    /** Stock-ish Android: nothing to suggest. */
    NONE;

    val wantsKeepAliveHint: Boolean get() = this != NONE

    companion object {
        /**
         * Classifies from the platform strings Android exposes to every app. Keys off
         * `Build.MANUFACTURER`/`Build.BRAND` and the ColorOS-specific system property
         * marker that OPPO/OnePlus ROMs set (`ro.build.version.opporom` etc. are not
         * readable without shell, so we use the manufacturer/brand fields only).
         */
        fun classify(manufacturer: String?, brand: String?): OemBackgroundPolicy {
            val m = manufacturer.orEmpty().lowercase()
            val b = brand.orEmpty().lowercase()
            val token = "$m $b"
            return when {
                token.contains("oppo") ||
                    token.contains("oneplus") ||
                    token.contains("realme") -> COLOROS_FAMILY

                token.contains("xiaomi") || token.contains("redmi") ||
                    token.contains("poco") || token.contains("huawei") ||
                    token.contains("honor") || token.contains("vivo") ||
                    token.contains("iqoo") || token.contains("meizu") ||
                    token.contains("tecno") || token.contains("infinix") -> OTHER_AGGRESSIVE

                else -> NONE
            }
        }
    }
}
