package com.elthon.optilauncher.recon

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Read-only probe of Home-role related system state.
 *
 * Shared by [ReconScanner] (report) and MainActivity (live "home role" status), so the
 * number on the home screen and the number in the report always come from one code path.
 *
 * Never requests a role, never launches an intent: it only inspects availability/hold
 * state and whether the system request flow would resolve.
 */
data class HomeRoleState(
    val roleManagerPresent: Boolean?,
    val roleAvailable: Boolean?,
    val roleHeld: Boolean?,
    val requestIntentCreated: Boolean?,
    val requestIntentResolved: Boolean?,
    val requestIntentResolveNote: String?,
    val defaultHomeResolution: String?,
    val errorSection: String? = null,
    val errorProbe: String? = null,
    val errorType: String? = null,
    val errorDetail: String? = null
) {
    val hasError: Boolean get() = errorSection != null
}

object HomeRoleProbe {

    fun probe(context: Context): HomeRoleState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return HomeRoleState(
                roleManagerPresent = false,
                roleAvailable = null,
                roleHeld = null,
                requestIntentCreated = null,
                requestIntentResolved = null,
                requestIntentResolveNote = "role request flow requires API 29+",
                defaultHomeResolution = resolveDefaultHome(context)
            )
        }

        var error: Triple<String, String, Throwable>? = null

        fun <T> attempt(section: String, name: String, fallback: T, block: () -> T): T =
            try {
                block()
            } catch (t: Throwable) {
                if (error == null) error = Triple(section, name, t)
                fallback
            }

        val roleManager: RoleManager? = attempt("home_role", "getSystemService(RoleManager)", null) {
            context.getSystemService(RoleManager::class.java)
        }

        val present = roleManager != null
        val available = if (roleManager == null) null else attempt("home_role", "isRoleAvailable", null) {
            roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
        }
        val held = if (roleManager == null) null else attempt("home_role", "isRoleHeld", null) {
            roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val requestIntent: Intent? = if (roleManager == null || available == false) null
        else attempt("home_role", "createRequestRoleIntent", null) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }

        val resolved = if (requestIntent == null) null else attempt(
            "home_role",
            "resolveActivity(createRequestRoleIntent)",
            null
        ) {
            context.packageManager.resolveActivity(requestIntent, 0) != null
        }

        val err = error
        return HomeRoleState(
            roleManagerPresent = present,
            roleAvailable = available,
            roleHeld = held,
            requestIntentCreated = requestIntent != null,
            requestIntentResolved = resolved,
            requestIntentResolveNote = requestIntentResolveNote(requestIntent, resolved),
            defaultHomeResolution = resolveDefaultHome(context),
            errorSection = err?.first,
            errorProbe = err?.second,
            errorType = err?.third?.javaClass?.simpleName,
            errorDetail = err?.third?.message ?: err?.third?.toString()
        )
    }

    /**
     * `resolveActivity()` is subject to package visibility filtering. We declare
     * `<queries>` for MAIN/HOME so a real result is meaningful, but a null result still
     * has to be reported as ambiguous rather than as "the dialog is gone".
     */
    private fun requestIntentResolveNote(requestIntent: Intent?, resolved: Boolean?): String = when {
        requestIntent == null -> "no request intent (role unavailable or RoleManager missing)"
        resolved == true -> "request intent resolves to a system activity"
        resolved == false -> "request intent did not resolve; may be package-visibility filtered"
        else -> "resolveActivity failed"
    }

    /**
     * Reports only *this* package, or the fact that another app is the default.
     * Another app's package id is deliberately never written down.
     */
    private fun resolveDefaultHome(context: Context): String = try {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        when {
            resolved == null -> "unresolvable_or_visibility_filtered"
            resolved.activityInfo?.packageName == context.packageName -> "self"
            else -> "other_app_is_default (identifier not recorded)"
        }
    } catch (t: Throwable) {
        "error: ${t.javaClass.simpleName}"
    }

    /** Extra documentation strings carried into the report's documentation section. */
    fun documentationNotes(): List<String> = listOf(
        "RoleManager home flow (API 29+): isRoleAvailable -> isRoleHeld -> createRequestRoleIntent -> " +
            "startActivityForResult; RESULT_OK means the role was granted, RESULT_CANCELED means dismissed.",
        "Home eligibility requires an intent filter with ACTION_MAIN + CATEGORY_HOME + CATEGORY_DEFAULT; " +
            "OPTI-LAUNCHER declares exactly that and nothing more.",
        "resolveActivity() is subject to package visibility filtering, so a null result is reported as " +
            "ambiguous rather than as proof the system dialog is missing.",
        "No role is ever requested automatically: the role request button is the only entry point."
    )

    /** Settings entry points this version can *report on* but never opens without a tap. */
    fun settingsIntents(context: Context): Map<String, String> {
        val pm = context.packageManager
        val packageUri = Uri.parse("package:${context.packageName}")
        val candidates: Map<String, Intent> = linkedMapOf(
            "usage_access" to Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            "battery_optimization" to Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            "accessibility" to Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            "overlay" to Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(packageUri),
            "write_settings" to Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData(packageUri),
            "developer_options" to Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            "default_home" to Intent(Settings.ACTION_HOME_SETTINGS)
        )
        return candidates.mapValues { (_, intent) ->
            try {
                if (pm.resolveActivity(intent, 0) != null) "available" else "not_resolvable_or_filtered"
            } catch (t: Throwable) {
                "error: ${t.javaClass.simpleName}"
            }
        }
    }

    fun settingsDocumentation(): String =
        "Settings entry points are reported as available/not_resolvable only — presence of an " +
            "entry point is not a permission grant, and nothing here opens automatically."
}
