package com.elthon.optilauncher.recon

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.elthon.optilauncher.BuildConfig
import com.elthon.optilauncher.recon.model.AppInfo
import com.elthon.optilauncher.recon.model.CapabilityMatrix
import com.elthon.optilauncher.recon.model.DeviceInfo
import com.elthon.optilauncher.recon.model.HomeRoleReport
import com.elthon.optilauncher.recon.model.LauncherReport
import com.elthon.optilauncher.recon.model.NameValue
import com.elthon.optilauncher.recon.model.PermissionEntry
import com.elthon.optilauncher.recon.model.PermissionsReport
import com.elthon.optilauncher.recon.model.ProbeError
import com.elthon.optilauncher.recon.model.ProcessMemoryReport
import com.elthon.optilauncher.recon.model.ReconReport
import com.elthon.optilauncher.recon.model.ScanTrigger
import com.elthon.optilauncher.recon.model.StorageReport
import com.elthon.optilauncher.recon.model.SystemMemoryReport
import com.elthon.optilauncher.recon.model.SystemStateReport
import com.elthon.optilauncher.recon.model.REPORT_SCHEMA_VERSION
import com.elthon.optilauncher.recon.model.SCANNER_VERSION
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The actual reconnaissance pass.
 *
 * Rules this class plays by:
 *  - every probe is individually guarded; a failure becomes a [ProbeError] in the report
 *    and the rest of the scan continues,
 *  - nothing here ever requests a permission, a role, a grant or a network call,
 *  - other apps are counted, never named: no package ids, labels or icons are stored,
 *  - only the app's *own* process/task/memory state is read in any detail.
 *
 * Instantiated per scan (it accumulates per-scan state), always called off the UI thread.
 */
class ReconScanner(private val context: Context) {

    private val errors = mutableListOf<ProbeError>()
    private val measurementNotes = mutableListOf<String>()
    private val documentationNotes = mutableListOf<String>()

    fun scan(trigger: ScanTrigger, snapshot: UiSnapshot): ReconReport {
        val startedAt = SystemClock.elapsedRealtime()
        errors.clear()
        measurementNotes.clear()
        documentationNotes.clear()
        errors.addAll(snapshot.errors)

        val pm = context.packageManager
        val pkg = context.packageName

        val device = collectDevice()
        val app = collectApp(pm, pkg)
        val systemMemory = collectMemory()
        val storage = collectStorage()
        val (homeRole, launcher) = collectLauncherAndRole(pm, pkg)
        val permissions = collectPermissions(pm, pkg)
        val systemState = collectSystemState(pkg)

        val matrixInput = CapabilityMatrix.MatrixInput(
            launchableActivityCount = launcher.launchableActivityCount,
            launchableActivityError = launcher.note?.takeIf { launcher.outcome == "ERROR" },
            homeRoleAvailable = homeRole.roleAvailable,
            homeRoleHeld = homeRole.roleHeld,
            canDrawOverlays = systemState.canDrawOverlays,
            usageStatsSampleCount = systemState.usageStatsSampleCount,
            usageStatsError = errors.firstOrNull { it.section == "system_state" && it.probe.contains("queryUsageStats", ignoreCase = true) }
                ?.message,
            backgroundRestricted = systemState.backgroundRestricted,
            visibleRunningProcessCount = visibleProcessCount,
            ownTaskCount = ownTaskCount,
            batteryOptimizationIgnored = systemState.ignoringBatteryOptimizations
        )

        addStaticNotes()

        val nowMs = System.currentTimeMillis()
        return ReconReport(
            schemaVersion = REPORT_SCHEMA_VERSION,
            scannerVersion = SCANNER_VERSION,
            generatedAtEpochMs = nowMs,
            generatedAtUtc = formatUtc(nowMs),
            generatedAtDeviceLocal = formatLocal(nowMs),
            scanTrigger = trigger,
            scanDurationMs = SystemClock.elapsedRealtime() - startedAt,
            device = device,
            app = app,
            display = snapshot.display,
            configuration = snapshot.configuration,
            insets = snapshot.insets,
            systemMemory = systemMemory,
            storage = storage,
            homeRole = homeRole,
            launcher = launcher,
            permissions = permissions,
            systemState = systemState,
            capabilityMatrix = CapabilityMatrix.build(matrixInput),
            probeErrors = errors.toList(),
            measurementNotes = measurementNotes.toList(),
            documentationNotes = documentationNotes.toList()
        )
    }

    // -- per-scan values the capability matrix also needs -----------------------

    private var visibleProcessCount: Int? = null
    private var ownTaskCount: Int? = null

    // -- sections ----------------------------------------------------------------

    private fun collectDevice(): DeviceInfo {
        val am = context.getSystemService(ActivityManager::class.java)
        if (am == null) {
            errors.add(ProbeError("device", "getSystemService(ActivityManager)", "Unavailable", "ActivityManager service is null"))
        }

        return DeviceInfo(
            manufacturer = probe("device", "Build.MANUFACTURER", null) { Build.MANUFACTURER },
            brand = probe("device", "Build.BRAND", null) { Build.BRAND },
            model = probe("device", "Build.MODEL", null) { Build.MODEL },
            device = probe("device", "Build.DEVICE", null) { Build.DEVICE },
            product = probe("device", "Build.PRODUCT", null) { Build.PRODUCT },
            hardware = probe("device", "Build.HARDWARE", null) { Build.HARDWARE },
            board = probe("device", "Build.BOARD", null) { Build.BOARD },
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                probe("device", "Build.SOC_MODEL", null) { Build.SOC_MODEL }
            } else {
                null
            },
            androidRelease = probe("device", "Build.VERSION.RELEASE", null) { Build.VERSION.RELEASE },
            apiLevel = probe("device", "Build.VERSION.SDK_INT", null) { Build.VERSION.SDK_INT },
            codename = probe("device", "Build.VERSION.CODENAME", null) { Build.VERSION.CODENAME },
            incremental = probe("device", "Build.VERSION.INCREMENTAL", null) { Build.VERSION.INCREMENTAL },
            securityPatchLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                probe("device", "Build.VERSION.SECURITY_PATCH", null) { Build.VERSION.SECURITY_PATCH }
            } else {
                null
            },
            baseOs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                probe("device", "Build.VERSION.BASE_OS", null) { Build.VERSION.BASE_OS }
            } else {
                null
            },
            buildId = probe("device", "Build.ID", null) { Build.ID },
            supportedAbis = probe("device", "Build.SUPPORTED_ABIS", emptyList()) { Build.SUPPORTED_ABIS?.toList() ?: emptyList() },
            supported64BitAbis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                probe("device", "Build.SUPPORTED_64_BIT_ABIS", emptyList()) { Build.SUPPORTED_64_BIT_ABIS?.toList() ?: emptyList() }
            } else {
                emptyList()
            },
            supported32BitAbis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                probe("device", "Build.SUPPORTED_32_BIT_ABIS", emptyList()) { Build.SUPPORTED_32_BIT_ABIS?.toList() ?: emptyList() }
            } else {
                emptyList()
            },
            vmName = probe("device", "java.vm.name", null) { System.getProperty("java.vm.name") },
            vmVersion = probe("device", "java.vm.version", null) { System.getProperty("java.vm.version") },
            javaVersion = probe("device", "java.version", null) { System.getProperty("java.version") },
            osArch = probe("device", "os.arch", null) { System.getProperty("os.arch") },
            availableProcessors = probe("device", "Runtime.availableProcessors", null) { Runtime.getRuntime().availableProcessors() },
            isLowRamDevice = am?.let { probe("device", "ActivityManager.isLowRamDevice", null) { it.isLowRamDevice } }
        )
    }

    private fun collectApp(pm: PackageManager, pkg: String): AppInfo {
        val packageInfo = probe("app", "getPackageInfo", null) { pm.getPackageInfo(pkg, 0) }
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            probe("app", "getInstallSourceInfo", null) { pm.getInstallSourceInfo(pkg).installingPackageName }
        } else {
            @Suppress("DEPRECATION")
            probe("app", "getInstallerPackageName", null) { pm.getInstallerPackageName(pkg) }
        }

        return AppInfo(
            applicationId = pkg,
            versionName = probe("app", "BuildConfig.VERSION_NAME", null) { BuildConfig.VERSION_NAME },
            versionCode = probe("app", "BuildConfig.VERSION_CODE", null) { BuildConfig.VERSION_CODE.toLong() },
            minSdk = probe("app", "BuildConfig.CFG_MIN_SDK", null) { BuildConfig.CFG_MIN_SDK },
            compileSdk = probe("app", "BuildConfig.CFG_COMPILE_SDK", null) { BuildConfig.CFG_COMPILE_SDK },
            targetSdk = probe("app", "BuildConfig.CFG_TARGET_SDK", null) { BuildConfig.CFG_TARGET_SDK },
            buildType = probe("app", "BuildConfig.BUILD_TYPE", null) { BuildConfig.BUILD_TYPE },
            debuggable = probe("app", "BuildConfig.DEBUG", null) { BuildConfig.DEBUG },
            firstInstallTimeMs = probe("app", "PackageInfo.firstInstallTime", null) { packageInfo?.firstInstallTime },
            lastUpdateTimeMs = probe("app", "PackageInfo.lastUpdateTime", null) { packageInfo?.lastUpdateTime },
            installerPackageName = installer
        )
    }

    private fun collectMemory(): SystemMemoryReport {
        val am = context.getSystemService(ActivityManager::class.java)
        val memInfo = if (am == null) {
            null
        } else {
            probe("memory", "ActivityManager.getMemoryInfo", null) {
                ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            }
        }

        val memoryInfo = probe("memory", "Debug.getMemoryInfo", null) {
            Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        }

        val processMemory = ProcessMemoryReport(
            totalPssKb = memoryInfo?.let { probe("memory", "MemoryInfo.totalPss", null) { it.totalPss.toLong() } },
            totalPrivateDirtyKb = memoryInfo?.let { probe("memory", "MemoryInfo.totalPrivateDirty", null) { it.totalPrivateDirty.toLong() } },
            totalSwapPssKb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                memoryInfo?.let {
                    probe("memory", "MemoryInfo.totalSwappablePss", null) { it.totalSwappablePss.toLong() }
                }
            } else {
                null
            },
            nativeHeapSizeBytes = probe("memory", "Debug.getNativeHeapSize", null) { Debug.getNativeHeapSize() },
            nativeHeapAllocatedBytes = probe("memory", "Debug.getNativeHeapAllocatedSize", null) { Debug.getNativeHeapAllocatedSize() },
            nativeHeapFreedBytes = probe("memory", "Debug.getNativeHeapFreeSize", null) { Debug.getNativeHeapFreeSize() },
            runtimeMaxHeapBytes = probe("memory", "Runtime.maxMemory", null) { Runtime.getRuntime().maxMemory() },
            runtimeTotalHeapBytes = probe("memory", "Runtime.totalMemory", null) { Runtime.getRuntime().totalMemory() },
            runtimeFreeHeapBytes = probe("memory", "Runtime.freeMemory", null) { Runtime.getRuntime().freeMemory() }
        )

        return SystemMemoryReport(
            totalBytes = memInfo?.totalMem,
            availableBytes = memInfo?.availMem,
            lowMemory = memInfo?.lowMemory,
            thresholdBytes = memInfo?.threshold,
            processMemory = processMemory
        )
    }

    private fun collectStorage(): List<StorageReport> {
        val paths: List<Pair<String, String>> = buildList {
            probe("storage", "Environment.getDataDirectory", null) { Environment.getDataDirectory()?.path }
                ?.let { add("data_partition" to it) }
            probe("storage", "Context.dataDir", null) { context.dataDir?.path }
                ?.let { add("app_data" to it) }
            probe("storage", "Context.filesDir", null) { context.filesDir?.path }
                ?.let { add("files" to it) }
            probe("storage", "Context.cacheDir", null) { context.cacheDir?.path }
                ?.let { add("cache" to it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                probe("storage", "Context.codeCacheDir", null) { context.codeCacheDir?.path }
                    ?.let { add("code_cache" to it) }
            }
        }
        return paths.map { (label, path) -> statOf(label, path) }
    }

    private fun statOf(label: String, path: String): StorageReport =
        probe("storage", "StatFs($label)", StorageReport(label, path, null, null, null)) {
            val stat = StatFs(path)
            StorageReport(
                label = label,
                path = path,
                totalBytes = stat.totalBytes,
                availableBytes = stat.availableBytes,
                freeBytes = stat.freeBytes
            )
        }

    private fun collectLauncherAndRole(pm: PackageManager, pkg: String): Pair<HomeRoleReport, LauncherReport> {
        val roleState = HomeRoleProbe.probe(context)
        roleState.errorSection?.let { section ->
            errors.add(
                ProbeError(
                    section = section,
                    probe = roleState.errorProbe ?: "RoleManager",
                    errorType = roleState.errorType ?: "Throwable",
                    message = roleState.errorDetail ?: "unknown failure"
                )
            )
        }

        val homeRole = HomeRoleReport(
            roleManagerAvailable = roleState.roleManagerPresent,
            roleAvailable = roleState.roleAvailable,
            roleHeld = roleState.roleHeld,
            requestIntentCreated = roleState.requestIntentCreated,
            requestIntentResolved = roleState.requestIntentResolved,
            requestIntentResolveNote = roleState.requestIntentResolveNote,
            defaultHomeResolve = roleState.defaultHomeResolution
        )

        val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)
        var launchableCount: Int? = null
        var outcome: String
        var note: String?

        if (launcherApps == null) {
            outcome = "SERVICE_MISSING"
            note = "LauncherApps service is not available on this device build."
            errors.add(ProbeError("launcher", "getSystemService(LauncherApps)", "Unavailable", "LauncherApps service is null"))
        } else {
            try {
                val activities = launcherApps.getActivityList(null, Process.myUserHandle())
                launchableCount = activities?.size ?: 0
                outcome = if (launchableCount == 0) "EMPTY" else "OK"
                note = if (launchableCount == 0) {
                    "getActivityList() returned an empty list; package-visibility filtering cannot be ruled out."
                } else {
                    "Count only: no activity names, labels, package ids or icons are stored."
                }
            } catch (t: Throwable) {
                errors.add(ProbeError("launcher", "LauncherApps.getActivityList", t.javaClass.simpleName, t.message ?: t.toString()))
                outcome = "ERROR"
                note = "getActivityList() failed: ${t.javaClass.simpleName}"
            }
        }

        val profileCount = launcherApps?.let {
            probe("launcher", "LauncherApps.getProfiles", null) { it.getProfiles()?.size }
        }

        val homeCandidateCount = probe(
            "launcher",
            "queryIntentActivities(MAIN+HOME)",
            null
        ) {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.size
        }

        visibleProcessCount = context.getSystemService(ActivityManager::class.java)?.let { activityManager ->
            probe("processes", "ActivityManager.getRunningAppProcesses", null) { activityManager.runningAppProcesses?.size }
        }
        ownTaskCount = context.getSystemService(ActivityManager::class.java)?.let { activityManager ->
            probe("processes", "ActivityManager.getAppTasks", null) { activityManager.appTasks?.size }
        }

        if (pkg.isBlank()) {
            errors.add(ProbeError("launcher", "context.packageName", "InvalidState", "package name is empty"))
        }

        return homeRole to LauncherReport(
            servicePresent = launcherApps != null,
            profileCount = profileCount,
            launchableActivityCount = launchableCount,
            outcome = outcome,
            note = note,
            homeCandidateCount = homeCandidateCount
        )
    }

    private fun collectPermissions(pm: PackageManager, pkg: String): PermissionsReport {
        val packageInfo = probe("permissions", "getPackageInfo(GET_PERMISSIONS)", null) {
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        }
        val declaredNames = packageInfo?.requestedPermissions?.toList() ?: emptyList()

        val entries = declaredNames.map { name ->
            val granted = try {
                pm.checkPermission(name, pkg) == PackageManager.PERMISSION_GRANTED
            } catch (t: Throwable) {
                errors.add(ProbeError("permissions", "checkPermission($name)", t.javaClass.simpleName, t.message ?: t.toString()))
                false
            }
            PermissionEntry(permission = name, granted = granted)
        }

        return PermissionsReport(
            declaredCount = declaredNames.size,
            declared = entries,
            hasInternet = declaredNames.contains(Manifest.permission.INTERNET),
            hasQueryAllPackages = declaredNames.contains(QUERY_ALL_PACKAGES_PERMISSION),
            note = "This build declares no <uses-permission>; the list above is read from the " +
                "installed PackageInfo with checkPermission(), which needs no permission for " +
                "your own package."
        )
    }

    private fun collectSystemState(pkg: String): SystemStateReport {
        val power = context.getSystemService(PowerManager::class.java)
        val am = context.getSystemService(ActivityManager::class.java)
        val usm = context.getSystemService(UsageStatsManager::class.java)

        val ignoringBattery = if (power == null) null else probe(
            "system_state",
            "PowerManager.isIgnoringBatteryOptimizations",
            null
        ) { power.isIgnoringBatteryOptimizations(pkg) }

        val powerSaveMode = power?.let {
            probe("system_state", "PowerManager.isPowerSaveMode", null) { it.isPowerSaveMode }
        }

        val deviceIdleMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            power?.let { probe("system_state", "PowerManager.isDeviceIdleMode", null) { it.isDeviceIdleMode } }
        } else {
            null
        }

        val backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            am?.let { probe("system_state", "ActivityManager.isBackgroundRestricted", null) { it.isBackgroundRestricted } }
        } else {
            null
        }

        var usageStatsSampleCount: Int? = null
        var usageStatsError: String? = null
        if (usm == null) {
            errors.add(ProbeError("system_state", "getSystemService(UsageStatsManager)", "Unavailable", "UsageStatsManager service is null"))
        } else {
            try {
                val end = System.currentTimeMillis()
                val start = end - USAGE_SAMPLE_WINDOW_MS
                usageStatsSampleCount = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)?.size ?: 0
            } catch (t: Throwable) {
                usageStatsError = "${t.javaClass.simpleName}: ${t.message ?: t.toString()}"
                errors.add(ProbeError("system_state", "UsageStatsManager.queryUsageStats", t.javaClass.simpleName, t.message ?: t.toString()))
            }
        }

        val standbyBucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            usm?.let { probe("system_state", "UsageStatsManager.getAppStandbyBucket", null) { it.appStandbyBucket } }
        } else {
            null
        }

        val canDrawOverlays = probe("system_state", "Settings.canDrawOverlays", null) {
            Settings.canDrawOverlays(context)
        }

        var accessibilityQueryPerformed = true
        var accessibilityCount: Int? = null
        try {
            val accessibility = context.getSystemService(AccessibilityManager::class.java)
            accessibilityCount = accessibility
                ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.size
        } catch (t: Throwable) {
            accessibilityQueryPerformed = false
            errors.add(
                ProbeError(
                    "system_state",
                    "AccessibilityManager.getEnabledAccessibilityServiceList",
                    t.javaClass.simpleName,
                    t.message ?: t.toString()
                )
            )
        }

        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            power?.let {
                probe("system_state", "PowerManager.getCurrentThermalStatus", null) {
                    thermalLabel(it.currentThermalStatus)
                }
            }
        } else {
            null
        }

        val settingsIntents = HomeRoleProbe.settingsIntents(context)
            .map { (name, value) -> NameValue(name = name, value = value) }

        @Suppress("UNUSED_VARIABLE") val _pm = context.packageManager

        return SystemStateReport(
            ignoringBatteryOptimizations = ignoringBattery,
            powerSaveMode = powerSaveMode,
            deviceIdleMode = deviceIdleMode,
            backgroundRestricted = backgroundRestricted,
            standbyBucket = standbyBucket,
            standbyBucketName = standbyName(standbyBucket),
            usageStatsSampleCount = usageStatsSampleCount,
            canDrawOverlays = canDrawOverlays,
            accessibilityServiceQueryPerformed = accessibilityQueryPerformed,
            enabledAccessibilityServiceCount = accessibilityCount,
            settingsIntents = settingsIntents,
            thermalStatus = thermalStatus
        ).also {
            if (usageStatsError != null) {
                // Keep the matrix's usage column honest: the reason must reach the report.
                measurementNotes.add("Usage stats probe failed ($usageStatsError); rows 3 and 4 of the matrix carry that failure.")
            }
        }
    }

    // -- notes -------------------------------------------------------------------

    private fun addStaticNotes() {
        measurementNotes.addAll(
            listOf(
                "Count-only policy: launchable activities, user profiles, home candidates and enabled " +
                    "accessibility services are stored as numbers. No other application's package id, " +
                    "label or icon is written to this report.",
                "Accessibility services: only the *number* of enabled services is recorded; component " +
                    "names are read into memory and immediately discarded.",
                "getRunningAppProcesses() is documented to return only the caller's own process for a " +
                    "non-system app, so the recorded count is an observation, not a diagnosis of filtering.",
                "Process memory figures (PSS, native heap, Runtime heap) describe OPTI-LAUNCHER's own " +
                    "process at scan time and move constantly — treat them as a snapshot, not a budget.",
                "Window insets are measured on the UI thread immediately before the scan starts and " +
                    "attached as snapshots; values are pixels from the last dispatch.",
                "isIgnoringBatteryOptimizations() is queried for this package only; no battery exemption " +
                    "was requested and no settings screen was opened.",
                "An EMPTY launchable list means the API returned zero rows, not that no launchers exist.",
                "Every field in this report came from a public API call made by this build — no hidden " +
                    "API, no reflection into non-SDK interfaces, no bypass."
            )
        )

        documentationNotes.addAll(HomeRoleProbe.documentationNotes())
        documentationNotes.add(HomeRoleProbe.settingsDocumentation())
        documentationNotes.addAll(
            listOf(
                "startActivity() does not need package visibility, but enumerating targets does; that is " +
                    "why launching and enumeration are assessed as two separate capabilities.",
                "killBackgroundProcesses() requires KILL_BACKGROUND_PROCESSES and Android documents it as " +
                    "best-effort for cached processes; it never stops a foreground or protected process.",
                "Android offers no third-party guarantee that a process survives: Doze, App Standby, " +
                    "cached-process trimming and OEM task killers all apply independently.",
                "targetSdk 35 enforces edge-to-edge: system bar colour APIs are deprecated and the app " +
                    "must consume WindowInsets itself — which this build does on every window inset dispatch.",
                "androidx.core:core-splashscreen is the supported backport of the Android 12 SplashScreen " +
                    "API, used here via installSplashScreen().",
                "v0.1 declares zero permissions, starts no service, opens no network socket, and never " +
                    "requests usage access, accessibility, overlay or battery-exemption consent."
            )
        )
    }

    // -- helpers -----------------------------------------------------------------

    private fun <T> probe(section: String, name: String, fallback: T, block: () -> T): T =
        try {
            block()
        } catch (t: Throwable) {
            errors.add(ProbeError(section, name, t.javaClass.simpleName, t.message ?: t.toString()))
            fallback
        }

    private fun standbyName(bucket: Int?): String? = when (bucket) {
        null -> null
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE"
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
        UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && bucket == UsageStatsManager.STANDBY_BUCKET_RESTRICTED) {
                "RESTRICTED"
            } else {
                "UNKNOWN($bucket)"
            }
        }
    }

    private fun thermalLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "NONE"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "UNKNOWN($status)"
    }

    private fun formatUtc(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMs))

    private fun formatLocal(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(epochMs))

    companion object {
        private const val QUERY_ALL_PACKAGES_PERMISSION = "android.permission.QUERY_ALL_PACKAGES"
        private const val USAGE_SAMPLE_WINDOW_MS = 24L * 60L * 60L * 1000L
    }
}
