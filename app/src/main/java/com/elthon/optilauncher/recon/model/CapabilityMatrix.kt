package com.elthon.optilauncher.recon.model

/**
 * Platform capability matrix for OPTI-LAUNCHER's future versions.
 *
 * Classification rules (strict, on purpose):
 *  - SUPPORTED          -> the capability was actually observed working in a scan.
 *  - PERMISSION_REQUIRED-> a public API exists but needs a permission/consent grant we
 *                          deliberately do not hold yet.
 *  - SYSTEM_RESTRICTED  -> the platform offers no public route for an ordinary app.
 *  - OEM_DEPENDENT      -> behaviour depends on the manufacturer's power/kill policy.
 *  - UNTESTED           -> neither demonstrated nor ruled out in this build.
 *  - UNKNOWN            -> the probe itself failed or was inconclusive.
 *
 * A manifest line, a resolved intent, or a non-empty docs page is never proof. Where a
 * probe ran, its actual observation is pasted into [CapabilityEntry.evidence] so a future
 * reader can audit the claim instead of trusting it.
 */
object CapabilityMatrix {

    const val LAUNCH_APP = "launch_app"
    const val ENUMERATE_LAUNCHABLE = "enumerate_launchable_apps"
    const val PREDICT_USAGE = "predict_or_learn_app_usage"
    const val READ_USAGE_HISTORY = "read_usage_history"
    const val CONTROL_FLOATING_WINDOWS = "control_floating_windows"
    const val INSPECT_OTHER_PROCESSES = "inspect_other_processes"
    const val REMOVE_FROM_RECENTS = "remove_other_apps_from_recents"
    const val TERMINATE_OTHER_APPS = "terminate_other_apps"
    const val PREVENT_BACKGROUND_KILL = "prevent_background_processes_from_being_killed"
    const val RESTRICTED_SETTINGS = "access_restricted_settings_or_system_interfaces"
    const val OPERATE_IN_BACKGROUND = "operate_automatically_under_background_restrictions"
    const val HOME_ROLE = "request_and_hold_home_role"

    /** Only the facts a given probe actually established; nulls mean "not measured". */
    data class MatrixInput(
        val launchableActivityCount: Int? = null,
        val launchableActivityError: String? = null,
        val homeRoleAvailable: Boolean? = null,
        val homeRoleHeld: Boolean? = null,
        val canDrawOverlays: Boolean? = null,
        val usageStatsSampleCount: Int? = null,
        val usageStatsError: String? = null,
        val backgroundRestricted: Boolean? = null,
        val visibleRunningProcessCount: Int? = null,
        val ownTaskCount: Int? = null,
        val batteryOptimizationIgnored: Boolean? = null
    )

    fun build(input: MatrixInput): List<CapabilityEntry> = listOf(

        // 1 -----------------------------------------------------------------------
        entry(
            id = LAUNCH_APP,
            label = "Launching applications",
            status = CapabilityStatus.UNTESTED,
            kind = EvidenceKind.NOT_TESTED,
            reason = "No third-party app is started by this build, so the capability is " +
                "neither demonstrated nor disproven. Android documents that startActivity() " +
                "works without package visibility, but documentation is not a device test.",
            evidence = "Not exercised. Only system activities (role dialog, share sheet, " +
                "settings) are started by OPTI-LAUNCHER.",
            needs = "Nothing special for the launch call itself; visibility would only matter " +
                "if we pre-check targets with PackageManager."
        ),

        // 2 -----------------------------------------------------------------------
        enumerate(input),

        // 3 -----------------------------------------------------------------------
        entry(
            id = PREDICT_USAGE,
            label = "Predicting or learning app usage",
            status = CapabilityStatus.PERMISSION_REQUIRED,
            kind = EvidenceKind.DOCUMENTED,
            reason = "Usage-based prediction requires PACKAGE_USAGE_STATS (a special app-op " +
                "the user must grant in Settings) plus an on-device model. v0.1 contains no " +
                "model and holds no usage grant, so nothing here proves prediction is possible.",
            evidence = "Measured: usage-stats probe returned " +
                describeUsage(input) + ".",
            needs = "PACKAGE_USAGE_STATS consent, plus work that is explicitly out of scope " +
                "for this version."
        ),

        // 4 -----------------------------------------------------------------------
        entry(
            id = READ_USAGE_HISTORY,
            label = "Reading usage history",
            status = CapabilityStatus.PERMISSION_REQUIRED,
            kind = EvidenceKind.DOCUMENTED,
            reason = "UsageStatsManager.queryUsageStats() is a public API but returns data " +
                "only after the user grants usage access for this package.",
            evidence = "Measured: queryUsageStats() -> " + describeUsage(input) + ".",
            needs = "User-granted usage access via Settings (never requested by v0.1)."
        ),

        // 5 -----------------------------------------------------------------------
        entry(
            id = CONTROL_FLOATING_WINDOWS,
            label = "Controlling floating windows",
            status = when (input.canDrawOverlays) {
                null -> CapabilityStatus.UNKNOWN
                else -> CapabilityStatus.PERMISSION_REQUIRED
            },
            kind = if (input.canDrawOverlays == null) EvidenceKind.NOT_TESTED else EvidenceKind.MEASURED,
            reason = "Drawing our own floating window needs SYSTEM_ALERT_WINDOW consent. " +
                "Controlling *other* apps' floating windows has no public API for a normal app.",
            evidence = "Measured: Settings.canDrawOverlays(this) = " +
                (input.canDrawOverlays?.toString() ?: "probe failed") + ".",
            needs = "Special-app-access consent for our own overlay; nothing available for " +
                "other apps' overlays without system privileges."
        ),

        // 6 -----------------------------------------------------------------------
        entry(
            id = INSPECT_OTHER_PROCESSES,
            label = "Inspecting other processes",
            status = when {
                input.visibleRunningProcessCount == null -> CapabilityStatus.UNKNOWN
                input.visibleRunningProcessCount <= 1 -> CapabilityStatus.SYSTEM_RESTRICTED
                else -> CapabilityStatus.UNTESTED
            },
            kind = when {
                input.visibleRunningProcessCount == null -> EvidenceKind.NOT_TESTED
                else -> EvidenceKind.MEASURED
            },
            reason = "Since Android 5.0, ActivityManager.getRunningAppProcesses() is " +
                "documented to return only the caller's own process to non-system apps.",
            evidence = "Measured: getRunningAppProcesses() returned " +
                (input.visibleRunningProcessCount?.toString() ?: "probe failed") +
                " process entry/entries for this app.",
            needs = "System/privileged privileges (or shell/adb) for a real process list."
        ),

        // 7 -----------------------------------------------------------------------
        entry(
            id = REMOVE_FROM_RECENTS,
            label = "Removing other apps from Recents",
            status = CapabilityStatus.SYSTEM_RESTRICTED,
            kind = EvidenceKind.DOCUMENTED,
            reason = "A normal app can only finish/remove its own tasks via " +
                "ActivityManager.getAppTasks(). There is no public API to evict another " +
                "app's task from the Recents screen.",
            evidence = "Measured: getAppTasks() saw " +
                (input.ownTaskCount?.toString() ?: "probe failed") +
                " task(s), all belonging to this package. No public eviction API exists in " +
                "the API 35 SDK.",
            needs = "System privileges; hidden APIs are deliberately out of scope."
        ),

        // 8 -----------------------------------------------------------------------
        entry(
            id = TERMINATE_OTHER_APPS,
            label = "Terminating other apps",
            status = CapabilityStatus.PERMISSION_REQUIRED,
            kind = EvidenceKind.DOCUMENTED,
            reason = "killBackgroundProcesses() exists behind the normal " +
                "KILL_BACKGROUND_PROCESSES permission, but Android documents it as best-effort " +
                "and it never touches foreground or protected processes. v0.1 does not declare " +
                "it and has not tested it — 'declared' would not mean 'works'.",
            evidence = "Not measured: permission not declared, no kill attempted.",
            needs = "KILL_BACKGROUND_PROCESSES declaration; realistically system-level force " +
                "stop for anything beyond cached processes."
        ),

        // 9 -----------------------------------------------------------------------
        entry(
            id = PREVENT_BACKGROUND_KILL,
            label = "Preventing background processes from being killed",
            status = CapabilityStatus.OEM_DEPENDENT,
            kind = EvidenceKind.DOCUMENTED,
            reason = "Android gives no third-party app a guarantee against being killed. " +
                "Doze, App Standby buckets, cached-process trimming and OEM task killers all " +
                "apply. Being the Home app typically helps in practice, but that is " +
                "manufacturer policy, not an API contract, and it is untested here.",
            evidence = "Measured inputs only: backgroundRestricted=" +
                (input.backgroundRestricted?.toString() ?: "n/a") +
                ", standby bucket probe recorded in the SYSTEM STATE section. No keep-alive " +
                "strategy exists in v0.1.",
            needs = "OEM-specific integrations; nothing portable is promised."
        ),

        // 10 ----------------------------------------------------------------------
        entry(
            id = RESTRICTED_SETTINGS,
            label = "Accessing restricted settings or system interfaces",
            status = CapabilityStatus.SYSTEM_RESTRICTED,
            kind = EvidenceKind.DOCUMENTED,
            reason = "Settings.Secure/Global writes, role-holder management " +
                "(MANAGE_ROLE_HOLDERS) and other system interfaces require signature-level " +
                "permissions, an adb grant, or a privileged/system app. v0.1 uses none of them " +
                "and does not bypass restrictions through reflection or hidden APIs.",
            evidence = "Measured: declared permission count = see PERMISSIONS section; " +
                "no privileged permission is held. Settings entry points that exist on this " +
                "device are listed in SYSTEM STATE.",
            needs = "Signature/privileged permissions or manual adb grants, decided case by " +
                "case in a later version."
        ),

        // 11 ----------------------------------------------------------------------
        entry(
            id = OPERATE_IN_BACKGROUND,
            label = "Operating automatically under Android background restrictions",
            status = CapabilityStatus.UNTESTED,
            kind = EvidenceKind.NOT_TESTED,
            reason = "v0.1 intentionally runs no background service, no polling loop and no " +
                "keep-alive, so automatic operation has not been attempted. Public routes " +
                "(foreground services, exact alarms, user-triggered exemptions) exist but their " +
                "real behaviour on this device is unknown.",
            evidence = "Not measured: no background component is declared in the manifest.",
            needs = "A design decision plus explicit user consent for any exemption; " +
                "exact-alarm and foreground-service rules vary by target SDK and OEM."
        ),

        // 12 ----------------------------------------------------------------------
        homeRole(input)
    )

    private fun enumerate(input: MatrixInput): CapabilityEntry = when {
        input.launchableActivityError != null -> entry(
            id = ENUMERATE_LAUNCHABLE,
            label = "Enumerating launchable applications",
            status = CapabilityStatus.UNKNOWN,
            kind = EvidenceKind.NOT_TESTED,
            reason = "The enumeration probe threw, so neither availability nor blocking is " +
                "established. Failing probes are reported, not guessed around.",
            evidence = "Probe error: ${input.launchableActivityError}",
            needs = "Re-run the scan; if it keeps failing, check package-visibility declarations."
        )

        input.launchableActivityCount == null -> entry(
            id = ENUMERATE_LAUNCHABLE,
            label = "Enumerating launchable applications",
            status = CapabilityStatus.UNKNOWN,
            kind = EvidenceKind.NOT_TESTED,
            reason = "LauncherApps was unavailable or not queried.",
            evidence = "No measurement.",
            needs = "A LauncherApps service on the device."
        )

        input.launchableActivityCount == 0 -> entry(
            id = ENUMERATE_LAUNCHABLE,
            label = "Enumerating launchable applications",
            status = CapabilityStatus.UNKNOWN,
            kind = EvidenceKind.MEASURED,
            reason = "getActivityList() returned an empty list. That can mean a genuinely " +
                "empty result or package-visibility filtering; a single empty scan cannot tell " +
                "which, so no stronger claim is made.",
            evidence = "Measured: 0 launchable activities returned.",
            needs = "A repeat scan, or comparison against a launcher that is known to work."
        )

        else -> entry(
            id = ENUMERATE_LAUNCHABLE,
            label = "Enumerating launchable applications",
            status = CapabilityStatus.SUPPORTED,
            kind = EvidenceKind.MEASURED,
            reason = "LauncherApps returned real data through public API with only the " +
                "<queries>MAIN/LAUNCHER declaration — no QUERY_ALL_PACKAGES.",
            evidence = "Measured: " + input.launchableActivityCount +
                " launchable activity/activities returned (count only; no names, labels, " +
                "package ids or icons are stored).",
            needs = "Nothing further for enumeration."
        )
    }

    private fun homeRole(input: MatrixInput): CapabilityEntry = when {
        input.homeRoleAvailable == null -> entry(
            id = HOME_ROLE,
            label = "Requesting and holding the Home role",
            status = CapabilityStatus.UNKNOWN,
            kind = EvidenceKind.NOT_TESTED,
            reason = "RoleManager could not be queried on this run.",
            evidence = "No measurement.",
            needs = "API 29+ with a RoleManager service."
        )

        !input.homeRoleAvailable -> entry(
            id = HOME_ROLE,
            label = "Requesting and holding the Home role",
            status = CapabilityStatus.UNKNOWN,
            kind = EvidenceKind.MEASURED,
            reason = "RoleManager reports the HOME role is not available on this device/OEM " +
                "build, so the documented request flow cannot be evaluated.",
            evidence = "Measured: isRoleAvailable(ROLE_HOME) = false.",
            needs = "An OEM build that exposes the HOME role through RoleManager."
        )

        input.homeRoleHeld == true -> entry(
            id = HOME_ROLE,
            label = "Requesting and holding the Home role",
            status = CapabilityStatus.SUPPORTED,
            kind = EvidenceKind.MEASURED,
            reason = "The documented request flow was used and the role is held now.",
            evidence = "Measured: isRoleAvailable = true, isRoleHeld = true.",
            needs = "Nothing — but losing/re-gaining the role still depends on user choice."
        )

        else -> entry(
            id = HOME_ROLE,
            label = "Requesting and holding the Home role",
            status = CapabilityStatus.PERMISSION_REQUIRED,
            kind = EvidenceKind.MEASURED,
            reason = "The role is available but not held: the grant requires explicit user " +
                "consent through the system role dialog. Availability alone is not proof that " +
                "the grant completes.",
            evidence = "Measured: isRoleAvailable = true, isRoleHeld = false.",
            needs = "The user choosing OPTI-LAUNCHER in the system Home dialog."
        )
    }

    private fun describeUsage(input: MatrixInput): String = when {
        input.usageStatsError != null -> "error ${input.usageStatsError}"
        input.usageStatsSampleCount == null -> "probe not run"
        else -> "${input.usageStatsSampleCount} entries"
    }

    private fun entry(
        id: String,
        label: String,
        status: CapabilityStatus,
        kind: EvidenceKind,
        reason: String,
        evidence: String,
        needs: String
    ) = CapabilityEntry(
        id = id,
        label = label,
        status = status,
        evidenceKind = kind,
        reason = reason.trimIndent(),
        evidence = evidence.trimIndent(),
        needs = needs.trimIndent()
    )

    /** Required by the v0.1 spec: every id above must appear in every report. */
    fun requiredIds(): List<String> = listOf(
        LAUNCH_APP, ENUMERATE_LAUNCHABLE, PREDICT_USAGE, READ_USAGE_HISTORY,
        CONTROL_FLOATING_WINDOWS, INSPECT_OTHER_PROCESSES, REMOVE_FROM_RECENTS,
        TERMINATE_OTHER_APPS, PREVENT_BACKGROUND_KILL, RESTRICTED_SETTINGS,
        OPERATE_IN_BACKGROUND, HOME_ROLE
    )
}
