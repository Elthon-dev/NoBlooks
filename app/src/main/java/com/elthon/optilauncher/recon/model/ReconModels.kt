package com.elthon.optilauncher.recon.model

/**
 * Pure Kotlin data model for the OPTI-LAUNCHER reconnaissance report.
 *
 * This package must never import `android.*`: it is shared between the app and the
 * JVM unit tests, and keeping it framework-free is what lets the report format be
 * tested without Robolectric or a device.
 *
 * Everything in here is either (a) a value measured at runtime by [com.elthon.optilauncher.recon.ReconScanner]
 * or (b) an explicitly labelled documentation note. Nothing in here is invented.
 */

const val REPORT_SCHEMA_VERSION = "opti-launcher.recon/1"
const val SCANNER_VERSION = "0.1.0"

/** Why a scan ran. Recorded in the report so a log reader can tell fresh from resume. */
enum class ScanTrigger { APP_START, FIRST_RUN, USER_REQUEST, RESUME }

/** How a statement in the report was established. */
enum class EvidenceKind {
    /** Observed at runtime on the device running the scan. */
    MEASURED,
    /** Stated by official Android documentation / the public SDK surface. */
    DOCUMENTED,
    /** Neither measured nor backed by a checkable statement — explicitly untested. */
    NOT_TESTED
}

enum class CapabilityStatus {
    SUPPORTED,
    PERMISSION_REQUIRED,
    SYSTEM_RESTRICTED,
    OEM_DEPENDENT,
    UNTESTED,
    UNKNOWN
}

/** A probe that failed. Failures are recorded, never swallowed and never fatal. */
data class ProbeError(
    val section: String,
    val probe: String,
    val errorType: String,
    val message: String
)

data class DeviceInfo(
    val manufacturer: String?,
    val brand: String?,
    val model: String?,
    val device: String?,
    val product: String?,
    val hardware: String?,
    val board: String?,
    val socModel: String?,
    val androidRelease: String?,
    val apiLevel: Int?,
    val codename: String?,
    val incremental: String?,
    val securityPatchLevel: String?,
    val baseOs: String?,
    val buildId: String?,
    val supportedAbis: List<String>,
    val supported64BitAbis: List<String>,
    val supported32BitAbis: List<String>,
    val vmName: String?,
    val vmVersion: String?,
    val javaVersion: String?,
    val osArch: String?,
    val availableProcessors: Int?,
    val isLowRamDevice: Boolean?
)

data class AppInfo(
    val applicationId: String,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val compileSdk: Int?,
    val targetSdk: Int?,
    val buildType: String?,
    val debuggable: Boolean?,
    val firstInstallTimeMs: Long?,
    val lastUpdateTimeMs: Long?,
    val installerPackageName: String?
)

data class DisplayInfo(
    val displayId: Int?,
    val widthPx: Int?,
    val heightPx: Int?,
    val densityDpi: Int?,
    val refreshRateHz: Float?,
    val displayState: String?,
    val rotation: String?,
    val currentMode: String?,
    val supportedModeCount: Int?,
    val supportedRefreshRatesHz: List<Float>
)

data class ConfigurationInfo(
    val orientation: String?,
    val screenWidthDp: Int?,
    val screenHeightDp: Int?,
    val smallestScreenWidthDp: Int?,
    val densityDpi: Int?,
    val fontScale: Float?,
    val uiMode: String?,
    val nightMode: String?,
    val locale: String?,
    val layoutDirection: String?
)

data class InsetsSnapshot(
    val source: String,
    val measuredAtEpochMs: Long,
    val systemBarsLeft: Int?,
    val systemBarsTop: Int?,
    val systemBarsRight: Int?,
    val systemBarsBottom: Int?,
    val statusBarTop: Int?,
    val navigationBarsBottom: Int?,
    val displayCutoutLeft: Int?,
    val displayCutoutTop: Int?,
    val displayCutoutRight: Int?,
    val displayCutoutBottom: Int?
)

data class ProcessMemoryReport(
    val totalPssKb: Long?,
    val totalPrivateDirtyKb: Long?,
    val totalSwapPssKb: Long?,
    val nativeHeapSizeBytes: Long?,
    val nativeHeapAllocatedBytes: Long?,
    val nativeHeapFreedBytes: Long?,
    val runtimeMaxHeapBytes: Long?,
    val runtimeTotalHeapBytes: Long?,
    val runtimeFreeHeapBytes: Long?
)

data class SystemMemoryReport(
    val totalBytes: Long?,
    val availableBytes: Long?,
    val lowMemory: Boolean?,
    val thresholdBytes: Long?,
    val processMemory: ProcessMemoryReport?
)

data class StorageReport(
    val label: String,
    val path: String,
    val totalBytes: Long?,
    val availableBytes: Long?,
    val freeBytes: Long?
)

data class HomeRoleReport(
    val roleManagerAvailable: Boolean?,
    val roleAvailable: Boolean?,
    val roleHeld: Boolean?,
    val requestIntentCreated: Boolean?,
    val requestIntentResolved: Boolean?,
    val requestIntentResolveNote: String?,
    val defaultHomeResolve: String?
)

data class LauncherReport(
    val servicePresent: Boolean?,
    val profileCount: Int?,
    val launchableActivityCount: Int?,
    val outcome: String,
    val note: String?,
    val homeCandidateCount: Int?
)

data class PermissionEntry(
    val permission: String,
    val granted: Boolean
)

data class PermissionsReport(
    val declaredCount: Int,
    val declared: List<PermissionEntry>,
    val hasInternet: Boolean,
    val hasQueryAllPackages: Boolean,
    val note: String
)

data class SystemStateReport(
    val ignoringBatteryOptimizations: Boolean?,
    val powerSaveMode: Boolean?,
    val deviceIdleMode: Boolean?,
    val backgroundRestricted: Boolean?,
    val standbyBucket: Int?,
    val standbyBucketName: String?,
    val usageStatsSampleCount: Int?,
    val canDrawOverlays: Boolean?,
    val accessibilityServiceQueryPerformed: Boolean,
    val enabledAccessibilityServiceCount: Int?,
    val settingsIntents: List<NameValue>,
    val thermalStatus: String?
)

data class NameValue(val name: String, val value: String)

data class CapabilityEntry(
    val id: String,
    val label: String,
    val status: CapabilityStatus,
    val evidenceKind: EvidenceKind,
    val reason: String,
    val evidence: String,
    val needs: String
)

data class ReconReport(
    val schemaVersion: String,
    val scannerVersion: String,
    val generatedAtEpochMs: Long,
    val generatedAtUtc: String,
    val generatedAtDeviceLocal: String,
    val scanTrigger: ScanTrigger,
    val scanDurationMs: Long,
    val device: DeviceInfo,
    val app: AppInfo,
    val display: DisplayInfo,
    val configuration: ConfigurationInfo,
    val insets: List<InsetsSnapshot>,
    val systemMemory: SystemMemoryReport,
    val storage: List<StorageReport>,
    val homeRole: HomeRoleReport,
    val launcher: LauncherReport,
    val permissions: PermissionsReport,
    val systemState: SystemStateReport,
    val capabilityMatrix: List<CapabilityEntry>,
    val probeErrors: List<ProbeError>,
    val measurementNotes: List<String>,
    val documentationNotes: List<String>
)
