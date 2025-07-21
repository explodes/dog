package io.explod.dog.util

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanSettings

object BtStr {

    fun status(status: Int): String {
        return when (status) {
            BluetoothStatusCodes.SUCCESS -> "SUCCESS"
            BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION ->
                "ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION"
            BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND ->
                "ERROR_PROFILE_SERVICE_NOT_BOUND"
            BluetoothStatusCodes.ERROR_GATT_WRITE_NOT_ALLOWED -> "ERROR_GATT_WRITE_NOT_ALLOWED"
            BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY -> "ERROR_GATT_WRITE_REQUEST_BUSY"
            BluetoothStatusCodes.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
            else -> "UNKNOWN_STATUS_$status"
        }
    }

    fun scanResultErrorCode(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "SCAN_FAILED_ALREADY_STARTED"
            ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "SCAN_FAILED_ALREADY_STARTED"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "SCAN_FAILED_ALREADY_STARTED"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "SCAN_FAILED_ALREADY_STARTED"
            ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "SCAN_FAILED_ALREADY_STARTED"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                "SCAN_FAILED_ALREADY_STARTED"
            else -> "UNKNOWN_ERROR_CODE_$errorCode"
        }
    }

    fun advertiseErrorCode(errorCode: Int): String {
        return when (errorCode) {
            0 /* AdvertiseCallback.ADVERTISE_SUCCESS */ -> "ADVERTISE_SUCCESS"
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ADVERTISE_FAILED_ALREADY_STARTED"
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "ADVERTISE_FAILED_DATA_TOO_LARGE"
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED ->
                "ADVERTISE_FAILED_FEATURE_UNSUPPORTED"
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "ADVERTISE_FAILED_INTERNAL_ERROR"
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ->
                "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS"
            else -> "UNKNOWN_ERROR_CODE_$errorCode"
        }
    }

    fun callbackType(callbackType: Int): String {
        return when (callbackType) {
            ScanSettings.CALLBACK_TYPE_ALL_MATCHES -> "CALLBACK_TYPE_ALL_MATCHES"
            ScanSettings.CALLBACK_TYPE_FIRST_MATCH -> "CALLBACK_TYPE_FIRST_MATCH"
            ScanSettings.CALLBACK_TYPE_MATCH_LOST -> "CALLBACK_TYPE_MATCH_LOST"
            else -> "UNKNOWN_CALLBACK_TYPE_$callbackType"
        }
    }

    fun gattState(state: Int): String {
        return when (state) {
            BluetoothProfile.STATE_DISCONNECTED -> "STATE_DISCONNECTED"
            BluetoothProfile.STATE_CONNECTING -> "STATE_CONNECTING"
            BluetoothProfile.STATE_CONNECTED -> "STATE_CONNECTED"
            BluetoothProfile.STATE_DISCONNECTING -> "STATE_DISCONNECTING"
            else -> "STATE_UNKNOWN_$state"
        }
    }

    fun gattStatus(status: Int): String {
        return when (status) {
            BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
            BluetoothGatt.GATT_READ_NOT_PERMITTED -> "GATT_READ_NOT_PERMITTED"
            BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "GATT_WRITE_NOT_PERMITTED"
            BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> "GATT_INSUFFICIENT_AUTHENTICATION"
            BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> "GATT_REQUEST_NOT_SUPPORTED"
            BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> "GATT_INSUFFICIENT_ENCRYPTION"
            BluetoothGatt.GATT_INVALID_OFFSET -> "GATT_INVALID_OFFSET"
            BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION -> "GATT_INSUFFICIENT_AUTHORIZATION"
            BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "GATT_INVALID_ATTRIBUTE_LENGTH"
            BluetoothGatt.GATT_CONNECTION_CONGESTED -> "GATT_CONNECTION_CONGESTED"
            BluetoothGatt.GATT_CONNECTION_TIMEOUT -> "GATT_CONNECTION_TIMEOUT"
            BluetoothGatt.GATT_FAILURE -> "GATT_FAILURE"
            else -> "GATT_STATUS_UNKNOWN_$status"
        }
    }
}
