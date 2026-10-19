package com.elthon.optilauncher.recon.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders a [ReconReport] as plain text.
 *
 * Design rules:
 *  - null always renders as the literal word "unavailable" — never as 0, "", or a guess;
 *  - sections 1-10 contain runtime measurements only, and each is labelled as such;
 *  - documentation/interpretation lives in the CAPABILITY MATRIX and NOTES sections;
 *  - the output is stable and line-oriented so it can be pasted into a chat session
 *    or diffed between scans.
 */
object ReportFormatter {

    private const val RULE = "================================================================"
    private const val RULE2 = "----------------------------------------------------------------"

    private val UTC: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
    private val LOCAL: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneId.systemDefault())

    fun format(report: ReconReport): String = buildString {
        appendLine(RULE)
        appendLine("OPTI-LAUNCHER RECON REPORT")
        appendLine("RECON SHELL v${report.scannerVersion}")
        appendLine(RULE)
        kv("schema_version", report.schemaVersion)
        kv("generated_at_epoch_ms", report.generatedAtEpochMs)
        kv("generated_at_utc", report.generatedAtUtc)
        kv("generated_at_device_local", report.generatedAtDeviceLocal)
        kv("scan_trigger", report.scanTrigger.name)
        kv("scan_duration_ms", report.scanDurationMs)
        kv("probe_error_count", report.probeErrors.size)
        appendLine("note: sections 1-10 hold values measured at runtime on this device;")
        appendLine("      CAPABILITY MATRIX and NOTES hold interpretation and documentation.")
        appendLine()

        section("1. DEVICE AND ANDROID (measured)")
        d("manufacturer", report.device.manufacturer)
        d("brand", report.device.brand)
        d("model", report.device.model)
        d("device", report.device.device)
        d("product", report.device.product)
        d("hardware", report.device.hardware)
        d("board", report.device.board)
        d("soc_model", report.device.socModel)
        d("android_release", report.device.androidRelease)
        d("api_level", report.device.apiLevel)
        d("codename", report.device.codename)
        d("incremental", report.device.incremental)
        d("security_patch_level", report.device.securityPatchLevel)
        d("base_os", report.device.baseOs)
        d("build_id", report.device.buildId)
        d("supported_abis", report.device.supportedAbis)
        d("supported_64bit_abis", report.device.supported64BitAbis)
        d("supported_32bit_abis", report.device.supported32BitAbis)
        d("vm_name", report.device.vmName)
        d("vm_version", report.device.vmVersion)
        d("java_version", report.device.javaVersion)
        d("os_arch", report.device.osArch)
        d("available_processors", report.device.availableProcessors)
        d("is_low_ram_device", report.device.isLowRamDevice)
        appendLine()

        section("2. APPLICATION AND BUILD (measured)")
        d("application_id", report.app.applicationId)
        d("version_name", report.app.versionName)
        d("version_code", report.app.versionCode)
        d("min_sdk", report.app.minSdk)
        d("compile_sdk", report.app.compileSdk)
        d("target_sdk", report.app.targetSdk)
        d("build_type", report.app.buildType)
        d("debuggable", report.app.debuggable)
        d("first_install_time_utc", report.app.firstInstallTimeMs?.let { formatUtc(it) })
        d("last_update_time_utc", report.app.lastUpdateTimeMs?.let { formatUtc(it) })
        d("installer_package", report.app.installerPackageName)
        appendLine()

        section("3. DISPLAY (measured)")
        d("display_id", report.display.displayId)
        d("width_px", report.display.widthPx)
        d("height_px", report.display.heightPx)
        d("density_dpi", report.display.densityDpi)
        d("refresh_rate_hz", report.display.refreshRateHz)
        d("display_state", report.display.displayState)
        d("rotation", report.display.rotation)
        d("current_mode", report.display.currentMode)
        d("supported_mode_count", report.display.supportedModeCount)
        d("supported_refresh_rates_hz", report.display.supportedRefreshRatesHz)
        appendLine()

        section("4. CONFIGURATION (measured)")
        d("orientation", report.configuration.orientation)
        d("screen_width_dp", report.configuration.screenWidthDp)
        d("screen_height_dp", report.configuration.screenHeightDp)
        d("smallest_screen_width_dp", report.configuration.smallestScreenWidthDp)
        d("density_dpi", report.configuration.densityDpi)
        d("font_scale", report.configuration.fontScale)
        d("ui_mode", report.configuration.uiMode)
        d("night_mode", report.configuration.nightMode)
        d("locale", report.configuration.locale)
        d("layout_direction", report.configuration.layoutDirection)
        appendLine()

        section("5. WINDOW INSETS (measured)")
        if (report.insets.isEmpty()) {
            appendLine("insets = unavailable (no snapshot captured yet)")
        } else {
            report.insets.forEachIndexed { index, snap ->
                d("insets[${index}].source", snap.source)
                d("insets[${index}].captured_at_utc", formatUtc(snap.measuredAtEpochMs))
                d("insets[${index}].system_bars_left_px", snap.systemBarsLeft)
                d("insets[${index}].system_bars_top_px", snap.systemBarsTop)
                d("insets[${index}].system_bars_right_px", snap.systemBarsRight)
                d("insets[${index}].system_bars_bottom_px", snap.systemBarsBottom)
                d("insets[${index}].status_bar_top_px", snap.statusBarTop)
                d("insets[${index}].navigation_bar_bottom_px", snap.navigationBarsBottom)
                d("insets[${index}].display_cutout_left_px", snap.displayCutoutLeft)
                d("insets[${index}].display_cutout_top_px", snap.displayCutoutTop)
                d("insets[${index}].display_cutout_right_px", snap.displayCutoutRight)
                d("insets[${index}].display_cutout_bottom_px", snap.displayCutoutBottom)
            }
        }
        appendLine()

        section("6. MEMORY (measured)")
        val mem = report.systemMemory
        d("system_total_bytes", mem.totalBytes)
        d("system_available_bytes", mem.availableBytes)
        d("system_low_memory", mem.lowMemory)
        d("system_threshold_bytes", mem.thresholdBytes)
        val pm = mem.processMemory
        if (pm == null) {
            d("process_memory", null)
        } else {
            d("process_total_pss_kb", pm.totalPssKb)
            d("process_total_private_dirty_kb", pm.totalPrivateDirtyKb)
            d("process_total_swap_pss_kb", pm.totalSwapPssKb)
            d("process_native_heap_size_bytes", pm.nativeHeapSizeBytes)
            d("process_native_heap_allocated_bytes", pm.nativeHeapAllocatedBytes)
            d("process_native_heap_freed_bytes", pm.nativeHeapFreedBytes)
            d("runtime_max_heap_bytes", pm.runtimeMaxHeapBytes)
            d("runtime_total_heap_bytes", pm.runtimeTotalHeapBytes)
            d("runtime_free_heap_bytes", pm.runtimeFreeHeapBytes)
            val used = pm.runtimeTotalHeapBytes?.let { t ->
                pm.runtimeFreeHeapBytes?.let { f -> t - f }
            }
            d("runtime_used_heap_bytes", used)
        }
        appendLine()

        section("7. STORAGE (measured)")
        if (report.storage.isEmpty()) {
            appendLine("storage = unavailable")
        } else {
            report.storage.forEach { s ->
                d("storage.${s.label}.path", s.path)
                d("storage.${s.label}.total_bytes", s.totalBytes)
                d("storage.${s.label}.available_bytes", s.availableBytes)
                d("storage.${s.label}.free_bytes", s.freeBytes)
            }
        }
        appendLine()

        section("8. HOME ROLE (measured)")
        val hr = report.homeRole
        d("role_manager_present", hr.roleManagerAvailable)
        d("role_available", hr.roleAvailable)
        d("role_held", hr.roleHeld)
        d("request_intent_created", hr.requestIntentCreated)
        d("request_intent_resolved_by_system", hr.requestIntentResolved)
        d("request_intent_resolve_note", hr.requestIntentResolveNote)
        d("default_home_resolution", hr.defaultHomeResolve)
        appendLine()

        section("9. LAUNCHER CAPABILITIES (measured)")
        val lr = report.launcher
        d("launcher_apps_service_present", lr.servicePresent)
        d("profile_count", lr.profileCount)
        d("launchable_activity_count", lr.launchableActivityCount)
        d("enumeration_outcome", lr.outcome)
        d("enumeration_note", lr.note)
        d("home_candidate_count", lr.homeCandidateCount)
        appendLine("data policy: counts only — no other app names, package ids, labels or")
        appendLine("             icons are read into, or persisted with, this report.")
        appendLine()

        section("10. PERMISSIONS (measured)")
        val perms = report.permissions
        d("declared_permission_count", perms.declaredCount)
        d("has_internet_permission", perms.hasInternet)
        d("has_query_all_packages", perms.hasQueryAllPackages)
        if (perms.declared.isEmpty()) {
            appendLine("declared_permissions = none")
        } else {
            perms.declared.forEach { p ->
                d("permission.${p.permission}", if (p.granted) "granted" else "not granted")
            }
        }
        appendLine("note: ${perms.note}")
        appendLine()

        section("11. SYSTEM STATE AND RESTRICTIONS (measured)")
        val ss = report.systemState
        d("ignoring_battery_optimizations", ss.ignoringBatteryOptimizations)
        d("power_save_mode", ss.powerSaveMode)
        d("device_idle_mode", ss.deviceIdleMode)
        d("background_restricted", ss.backgroundRestricted)
        d("standby_bucket", ss.standbyBucket)
        d("standby_bucket_name", ss.standbyBucketName)
        d("usage_stats_sample_count", ss.usageStatsSampleCount)
        d("can_draw_overlays", ss.canDrawOverlays)
        d("accessibility_enabled_service_query_performed", ss.accessibilityServiceQueryPerformed)
        d("enabled_accessibility_service_count", ss.enabledAccessibilityServiceCount)
        d("thermal_status", ss.thermalStatus)
        ss.settingsIntents.forEach { nv -> d("settings_screen.${nv.name}", nv.value) }
        appendLine()

        section("12. CAPABILITY MATRIX (interpretation)")
        report.capabilityMatrix.forEach { c ->
            appendLine("[${c.status}] ${c.id} — ${c.label}")
            appendLine("    evidence_kind = ${c.evidenceKind}")
            appendLine("    reason        = ${c.reason}")
            appendLine("    evidence      = ${c.evidence}")
            appendLine("    needs         = ${c.needs}")
        }
        appendLine()

        section("13. PROBE ERRORS AND UNKNOWNS")
        if (report.probeErrors.isEmpty()) {
            appendLine("probe_errors = none")
        } else {
            report.probeErrors.forEach { e ->
                appendLine("- section=${e.section} probe=${e.probe}")
                appendLine("  type=${e.errorType} message=${e.message}")
            }
        }
        appendLine()

        section("14. MEASUREMENT NOTES")
        if (report.measurementNotes.isEmpty()) appendLine("(none)")
        else report.measurementNotes.forEach { appendLine("- $it") }
        appendLine()

        section("15. DOCUMENTATION NOTES")
        if (report.documentationNotes.isEmpty()) appendLine("(none)")
        else report.documentationNotes.forEach { appendLine("- $it") }
        appendLine()
        appendLine(RULE2)
        appendLine("END OF REPORT (schema ${report.schemaVersion})")
        appendLine(RULE2)
    }

    fun formatUtc(epochMs: Long): String =
        runCatching { UTC.format(Instant.ofEpochMilli(epochMs)) }.getOrElse { "unavailable" }

    fun formatLocal(epochMs: Long): String =
        runCatching { LOCAL.format(Instant.ofEpochMilli(epochMs)) }.getOrElse { "unavailable" }

    private fun StringBuilder.section(title: String) {
        appendLine(RULE2)
        appendLine(title)
        appendLine(RULE2)
    }

    private fun StringBuilder.kv(key: String, value: Any?) {
        appendLine("$key = ${render(value)}")
    }

    private fun StringBuilder.d(key: String, value: Any?) = kv(key, value)


    private fun render(value: Any?): String = when (value) {
        null -> "unavailable"
        is Boolean -> value.toString()
        is Float -> if (value.isNaN()) "unavailable" else value.toString()
        is Double -> if (value.isNaN()) "unavailable" else value.toString()
        is Collection<*> -> if (value.isEmpty()) "unavailable" else value.joinToString(", ")
        else -> value.toString()
    }
}
