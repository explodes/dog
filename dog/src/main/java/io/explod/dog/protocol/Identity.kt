package io.explod.dog.protocol

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import io.explod.dog.util.ImmutableBytes
import java.util.Locale

data class PartialIdentity(
    val name: String?,
    val deviceType: DeviceType?,
    val connectionType: ConnectionType?,
)

data class FullIdentity(val partialIdentity: PartialIdentity, val appBytes: ImmutableBytes?)

fun createFullIdentity(
    applicationContext: Context,
    userInfo: UserInfo,
    connectionType: ConnectionType,
): FullIdentity {
    return FullIdentity(
        partialIdentity =
            createPartialIdentity(
                applicationContext = applicationContext,
                userInfo = userInfo,
                connectionType = connectionType,
            ),
        appBytes = userInfo.appBytes,
    )
}

fun createPartialIdentity(
    applicationContext: Context,
    userInfo: UserInfo,
    connectionType: ConnectionType,
): PartialIdentity {
    return PartialIdentity(
        name = userInfo.userName ?: getDeviceName(applicationContext),
        deviceType = getDeviceUiMode(applicationContext),
        connectionType = connectionType,
    )
}

private fun getDeviceName(applicationContext: Context): String {
    return getUserDeviceName(applicationContext) ?: getManufacturerDeviceName()
}

private fun getUserDeviceName(applicationContext: Context): String? {
    return Settings.Global.getString(
        applicationContext.contentResolver,
        Settings.Global.DEVICE_NAME,
    )
}

private fun getManufacturerDeviceName(): String {
    val manufacturer = Build.MANUFACTURER
    val model = Build.MODEL
    return if (
        model.lowercase(Locale.getDefault()).startsWith(manufacturer.lowercase(Locale.getDefault()))
    ) {
        capitalize(model)
    } else {
        "${capitalize(manufacturer)} $model"
    }
}

private fun capitalize(s: String?): String {
    if (s.isNullOrEmpty()) {
        return ""
    }
    val first = s[0]
    return if (Character.isLetter(first)) {
        first.uppercaseChar() + s.substring(1)
    } else {
        s
    }
}

private fun getDeviceUiMode(applicationContext: Context): DeviceType? {
    val uiModeManager =
        applicationContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return when (uiModeManager.currentModeType) {
        Configuration.UI_MODE_TYPE_TELEVISION -> DeviceType.TV
        Configuration.UI_MODE_TYPE_CAR -> DeviceType.CAR
        Configuration.UI_MODE_TYPE_DESK -> DeviceType.DESKTOP
        Configuration.UI_MODE_TYPE_WATCH -> DeviceType.WATCH
        Configuration.UI_MODE_TYPE_NORMAL ->
            if (isTablet(applicationContext)) {
                DeviceType.TABLET
            } else {
                DeviceType.PHONE
            }

        else -> null
    }
}

private const val TABLET_INCHES_THRESHOLD = 7.0

private fun isTablet(applicationContext: Context): Boolean {
    return !hasTelephony(applicationContext) ||
        getDeviceDiagonalInches(applicationContext) >= TABLET_INCHES_THRESHOLD
}

private fun getDeviceDiagonalInches(applicationContext: Context): Double {
    val metrics = applicationContext.resources.displayMetrics
    val yInches = metrics.heightPixels / metrics.ydpi
    val xInches = metrics.widthPixels / metrics.xdpi
    return Math.sqrt((xInches * xInches + yInches * yInches).toDouble())
}

private fun hasTelephony(applicationContext: Context): Boolean {
    val manager = applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    return manager.phoneType != TelephonyManager.PHONE_TYPE_NONE
}
