package io.explod.dog_app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import io.explod.loggly.Logger

@Composable
fun PermissionsUi(
    modifier: Modifier = Modifier,
    logger: Logger,
    onPermissionsGranted: (granted: Boolean) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Permissions are required before using this app!")
        PermissionsButton(logger, onPermissionsGranted)
    }
}

@Composable
fun PermissionsButton(logger: Logger, onPermissionsGranted: (granted: Boolean) -> Unit) {
    val context = LocalContext.current
    val permissions = bluetoothPermissions()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionsMap ->
            val allGranted = permissionsMap.values.all { it }
            if (allGranted && checkAllPermissions(context, permissions)) {
                // Permission Accepted: Do something
                logger.debug("PERMISSIONS GRANTED")
                onPermissionsGranted(true)
            } else {
                // Permission Denied: Do something
                logger.debug("PERMISSIONS DENIED")
                onPermissionsGranted(false)
            }
        }

    Button(
        onClick = {
            // Check permission

            val permissionsGranted = checkAllPermissions(context, permissions)
            if (permissionsGranted) {
                logger.debug("Permissions already granted.")
                onPermissionsGranted(true)
            } else {
                // Asking for permission
                logger.debug("Asking for permission.")
                launcher.launch(permissions.toTypedArray())
            }
        }
    ) {
        Text(text = "Check and Request Permissions!!!")
    }
}

fun checkAllPermissions(context: Context, permissions: Collection<String>): Boolean {
    for (permission in permissions) {
        val granted = ContextCompat.checkSelfPermission(context, permission)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            return false
        }
    }
    return true
}

fun bluetoothPermissions(): Collection<String> {
    val perms =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            listOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    return perms
}

@Preview()
@Composable
fun PermissionsUiPreview() {
    PermissionsUi(logger = Logger.create("PermissionsUiPreview"), onPermissionsGranted = {})
}
