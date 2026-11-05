package dev.modifyx.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import dev.modifyx.core.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ComponentType(val label: String) {
    ACTIVITY("Activity"),
    SERVICE("Service"),
    RECEIVER("Receiver"),
    PROVIDER("Provider"),
}

enum class ComponentState(val label: String) {
    DEFAULT("default"),
    ENABLED("enabled"),
    DISABLED("disabled"),
    USER_DISABLED("disabled by user"),
    UNTIL_USED("until used"),
}

data class ComponentRow(
    val name: String,
    val shortName: String,
    val type: ComponentType,
    val exported: Boolean,
    val enabled: Boolean,
    val state: ComponentState,
    val permission: String?,
)

data class ControlResult(
    val success: Boolean,
    val verified: Boolean,
    val message: String,
    val command: String = "",
    val output: String = "",
)

enum class StandbyBucket(val value: Int, val label: String, val blurb: String) {
    EXEMPTED(5, "Exempted", "Never restricted by the system"),
    ACTIVE(10, "Active", "In use right now"),
    WORKING_SET(20, "Working set", "Used regularly"),
    FREQUENT(30, "Frequent", "Opened often"),
    RARE(40, "Rare", "Rarely opened, background jobs deferred"),
    RESTRICTED(45, "Restricted", "Almost never allowed to run"),
    NEVER(50, "Never", "Never runs in the background"),
    ;

    companion object {
        fun from(raw: Int?): StandbyBucket? = entries.firstOrNull { it.value == raw }
    }
}

/**
 * Per-package controls that go beyond uninstall/disable: individual manifest components,
 * runtime permission grants and the app standby bucket. Every call runs a real `pm`/`am`
 * command and re-reads the platform state before reporting success.
 */
class AppControls(
    private val context: Context,
    private val repository: PackageRepository,
) {

    private val pm: PackageManager = context.packageManager

    suspend fun components(pkg: String): List<ComponentRow> = withContext(Dispatchers.IO) {
        val info = repository.queryPackage(
            pkg,
            PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                PackageManager.MATCH_DISABLED_COMPONENTS,
        ) ?: return@withContext emptyList()

        val rows = ArrayList<ComponentRow>()
        fun add(components: Array<out ComponentInfo>?, type: ComponentType) {
            components?.forEach { component ->
                val name = component.name ?: return@forEach
                val state = stateOf(pkg, name)
                rows += ComponentRow(
                    name = name,
                    shortName = name.removePrefix("$pkg.").ifBlank { name.substringAfterLast('.') },
                    type = type,
                    exported = component.exported,
                    enabled = when (state) {
                        ComponentState.DEFAULT -> component.enabled
                        ComponentState.ENABLED -> true
                        else -> false
                    },
                    state = state,
                    permission = permissionOf(component),
                )
            }
        }
        add(info.activities, ComponentType.ACTIVITY)
        add(info.services, ComponentType.SERVICE)
        add(info.receivers, ComponentType.RECEIVER)
        add(info.providers, ComponentType.PROVIDER)
        rows.sortedWith(compareBy({ it.type.ordinal }, { it.shortName.lowercase() }))
    }

    /** Only the concrete component types expose their guarding permission. */
    private fun permissionOf(component: ComponentInfo): String? = when (component) {
        is ActivityInfo -> component.permission
        is ServiceInfo -> component.permission
        is ProviderInfo -> component.readPermission ?: component.writePermission
        else -> null
    }

    private fun stateOf(pkg: String, component: String): ComponentState = runCatching {
        when (pm.getComponentEnabledSetting(ComponentName(pkg, component))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> ComponentState.ENABLED
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> ComponentState.DISABLED
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> ComponentState.USER_DISABLED
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> ComponentState.UNTIL_USED
            else -> ComponentState.DEFAULT
        }
    }.getOrDefault(ComponentState.DEFAULT)

    suspend fun setComponent(pkg: String, component: String, enable: Boolean): ControlResult =
        withContext(Dispatchers.IO) {
            if (!PrivilegeManager.canModify()) return@withContext noPrivilege()
            val verb = if (enable) "enable" else "disable"
            var command = "pm $verb --user 0 $pkg/$component"
            var result = PrivilegeManager.shell().exec(command, 30_000)
            if (!result.success) {
                command = "pm $verb $pkg/$component"
                result = PrivilegeManager.shell().exec(command, 30_000)
            }

            val state = stateOf(pkg, component)
            val verified = if (enable) {
                state == ComponentState.ENABLED || state == ComponentState.DEFAULT
            } else {
                state == ComponentState.DISABLED ||
                    state == ComponentState.USER_DISABLED ||
                    state == ComponentState.UNTIL_USED
            }
            ControlResult(
                success = verified && result.success,
                verified = verified,
                message = when {
                    verified -> if (enable) "Component enabled." else "Component disabled."
                    result.success -> "Command ran but the component state did not change."
                    else -> result.output.lineSequence().firstOrNull { it.isNotBlank() } ?: "Command failed."
                },
                command = command,
                output = result.output,
            )
        }

    suspend fun setPermission(pkg: String, permission: String, grant: Boolean): ControlResult =
        withContext(Dispatchers.IO) {
            if (!PrivilegeManager.canModify()) return@withContext noPrivilege()
            val verb = if (grant) "grant" else "revoke"
            val command = "pm $verb $pkg $permission"
            val result = PrivilegeManager.shell().exec(command, 30_000)
            val expected = if (grant) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
            val verified = runCatching { pm.checkPermission(permission, pkg) == expected }.getOrDefault(false)
            ControlResult(
                success = verified && result.success,
                verified = verified,
                message = when {
                    verified -> if (grant) "Permission granted." else "Permission revoked."
                    result.success -> "Command ran but the permission state did not change. It may not be a runtime permission."
                    else -> result.output.lineSequence().firstOrNull { it.isNotBlank() } ?: "Command failed."
                },
                command = command,
                output = result.output,
            )
        }

    suspend fun standbyBucket(pkg: String): StandbyBucket? = withContext(Dispatchers.IO) {
        if (!PrivilegeManager.canModify()) return@withContext null
        val result = PrivilegeManager.shell().exec("am get-standby-bucket $pkg", 15_000)
        if (!result.success) return@withContext null
        val raw = Regex("-?\\d+").find(result.stdout)?.value?.toIntOrNull() ?: return@withContext null
        StandbyBucket.from(raw)
    }

    suspend fun setStandbyBucket(pkg: String, bucket: StandbyBucket): ControlResult =
        withContext(Dispatchers.IO) {
            if (!PrivilegeManager.canModify()) return@withContext noPrivilege()
            val command = "am set-standby-bucket $pkg ${bucket.value}"
            val result = PrivilegeManager.shell().exec(command, 20_000)
            val current = standbyBucket(pkg)
            val verified = current == bucket
            ControlResult(
                success = verified && result.success,
                verified = verified,
                message = when {
                    verified -> "Standby bucket set to ${bucket.label}."
                    result.success -> "Command ran but the bucket reads back as ${current?.label ?: "unknown"}."
                    else -> result.output.lineSequence().firstOrNull { it.isNotBlank() } ?: "Command failed."
                },
                command = command,
                output = result.output,
            )
        }

    private fun noPrivilege() = ControlResult(
        success = false,
        verified = false,
        message = "No root or Shizuku shell: nothing was executed.",
    )
}
