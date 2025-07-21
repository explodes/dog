package io.explod.dog.util.state

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.explod.dog.util.state.PermissionsChecker.PermissionStatus
import io.explod.loggly.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

typealias PermissionMap = Map<String, PermissionStatus>

class PermissionsChecker(
    private val applicationContext: Context,
    private val permissionsToCheck: Collection<String>,
    logger: Logger,
) : PollerLooper.Checker<PermissionMap> {

    private val logger = logger.child("PermissionsChecker")

    override fun calculateState(lastState: PermissionMap?): Map<String, PermissionStatus> {
        val resultMap = mutableMapOf<String, PermissionStatus>()

        for (permission in permissionsToCheck) {
            val result = ContextCompat.checkSelfPermission(applicationContext, permission)
            val status =
                when (result) {
                    PackageManager.PERMISSION_GRANTED -> PermissionStatus.GRANTED
                    PackageManager.PERMISSION_DENIED -> PermissionStatus.DENIED
                    else -> PermissionStatus.DENIED
                }
            resultMap[permission] = status
        }

        if (lastState != resultMap) {
            logger.debug("New permissions: old=$lastState new=$resultMap")
        }

        return resultMap
    }

    enum class PermissionStatus {
        GRANTED,
        DENIED,
    }

    companion object {
        const val LOOP_DELAY = 5_000L
    }
}

fun PollerLooper<PermissionMap>.flowForPermission(permission: String): Flow<PermissionStatus> {
    return flow.map { it[permission] ?: PermissionStatus.DENIED }
}

fun PollerLooper<PermissionMap>.flowForPermissions(
    permissions: Collection<String>
): Flow<PermissionStatus> {
    return flow.map { m ->
        val allOk =
            permissions.all { p -> (m[p] ?: PermissionStatus.DENIED) == PermissionStatus.GRANTED }
        if (allOk) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.DENIED
        }
    }
}
