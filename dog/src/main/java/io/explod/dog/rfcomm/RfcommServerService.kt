@file:OptIn(ExperimentalAtomicApi::class, ExperimentalCoroutinesApi::class)

package io.explod.dog.rfcomm

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.collection.LruCache
import io.explod.dog.common.ConnectionPreferenceConf
import io.explod.dog.common.RetrySignalConf
import io.explod.dog.conn.ConnectionState
import io.explod.dog.conn.LinkListener
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.conn.LinkedConnectionListener
import io.explod.dog.conn.LinkedConnectionStateListener
import io.explod.dog.protocol.ConnectionType
import io.explod.dog.protocol.Identity
import io.explod.dog.protocol.Protocol.Server
import io.explod.dog.protocol.ServiceInfo
import io.explod.dog.protocol.UserInfo
import io.explod.dog.util.BtStr
import io.explod.dog.util.ConfService
import io.explod.dog.util.CoroutinePackage
import io.explod.dog.util.Provider
import io.explod.dog.util.ReaderWriterCloser
import io.explod.dog.util.deviceString
import io.explod.dog.util.locked
import io.explod.dog.util.state.PermissionsChecker
import io.explod.loggly.Logger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resumeWithException

class RfcommServerService
private constructor(
    private val serviceInfo: ServiceInfo,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val bluetoothPermissionsConf: Provider<BluetoothPermissionsConf>,
    private val bleAvailabilityConf: Provider<BleAvailabilityConf>,
    private val serverRfcommConnectionPreferenceConf: Provider<ConnectionPreferenceConf>,
    private val serverBluetoothRetrySignalConf: Provider<RetrySignalConf>,
    io: CoroutinePackage,
    private val logger: Logger,
    private val linkedConnectionListener: LinkedConnectionListener?,
    private val linkedConnectionStateListener: LinkedConnectionStateListener?,
    private val linkListener: LinkListener?,
    private val applicationContext: Context,
) : ConfService(logger, io) {

    private val userInfo = locked { UserInfo() }

    fun setUserInfo(userInfo: UserInfo) {
        logger.debug("setConfig: config=$userInfo")
        this.userInfo.locked { setValue(userInfo) }
    }

    override fun getConfFlow(): Flow<Boolean> {
        return combine(
                bluetoothPermissionsConf.get().flow,
                bleAvailabilityConf.get().flow,
                serverRfcommConnectionPreferenceConf.get().flow,
                serverBluetoothRetrySignalConf.get().flow,
                ::shouldRun,
            )
            .distinctUntilChanged()
    }

    private fun shouldRun(
        permissionsGranted: PermissionsChecker.PermissionStatus,
        availability: BleAvailabilityConf.Availability,
        connectionPreference: ConnectionPreferenceConf.Preference,
        retry: RetrySignalConf.Retry,
    ): Boolean {
        fun calculate(): Boolean {
            when (permissionsGranted) {
                PermissionsChecker.PermissionStatus.DENIED -> return false
                PermissionsChecker.PermissionStatus.GRANTED -> true
            }
            when (connectionPreference) {
                ConnectionPreferenceConf.Preference.CLOSED -> return false
                ConnectionPreferenceConf.Preference.OPEN -> true
            }
            when (availability) {
                BleAvailabilityConf.Availability.NOT_AVAILABLE -> return false
                BleAvailabilityConf.Availability.AVAILABLE -> true
            }
            when (retry) {
                RetrySignalConf.Retry.READY -> true
                RetrySignalConf.Retry.BACKOFF -> return false
            }
            return true
        }

        val shouldRun = calculate()
        logger.debug(
            "shouldRun: shouldRun=$shouldRun connectionPreference=$connectionPreference, permissionsGranted=$permissionsGranted, availability=$availability, retry=$retry"
        )
        return shouldRun
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override suspend fun runService() {
        logger.info("Booting server...")
        try {
            getLogic()?.run()
        } catch (e: Exception) {
            logger.error("Server shutdown due to error: ${e.message}", e)
        } finally {
            serverRfcommConnectionPreferenceConf
                .get()
                .setPreference(ConnectionPreferenceConf.Preference.CLOSED)
        }
    }

    private fun getLogic(): RfcommServerServiceLogic? {
        val bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            return null
        }
        return RfcommServerServiceLogic(
            bleScanner = bleScanner,
            serviceInfo = serviceInfo,
            logger = logger,
            linkedConnectionListener = linkedConnectionListener,
            linkedConnectionStateListener = linkedConnectionStateListener,
            linkListener = linkListener,
            applicationContext = applicationContext,
            userInfo = userInfo.locked { it },
        )
    }

    companion object {
        fun create(
            serviceInfo: ServiceInfo,
            bluetoothAdapter: BluetoothAdapter?,
            bluetoothPermissionsConf: Provider<BluetoothPermissionsConf>,
            bleAvailabilityConf: Provider<BleAvailabilityConf>,
            serverRfcommConnectionPreferenceConf: Provider<ConnectionPreferenceConf>,
            serverBluetoothRetrySignalConf: Provider<RetrySignalConf>,
            io: CoroutinePackage,
            logger: Logger,
            linkedConnectionListener: LinkedConnectionListener?,
            linkedConnectionStateListener: LinkedConnectionStateListener?,
            linkListener: LinkListener?,
            applicationContext: Context,
        ): RfcommServerService {
            return RfcommServerService(
                serviceInfo = serviceInfo,
                bluetoothAdapter = bluetoothAdapter,
                bluetoothPermissionsConf = bluetoothPermissionsConf,
                bleAvailabilityConf = bleAvailabilityConf,
                serverRfcommConnectionPreferenceConf = serverRfcommConnectionPreferenceConf,
                serverBluetoothRetrySignalConf = serverBluetoothRetrySignalConf,
                io = io,
                logger = logger.child("RfcommServer"),
                linkedConnectionListener = linkedConnectionListener,
                linkedConnectionStateListener = linkedConnectionStateListener,
                linkListener = linkListener,
                applicationContext = applicationContext,
            )
        }
    }
}

private class RfcommServerServiceLogic(
    private val bleScanner: BluetoothLeScanner,
    private val serviceInfo: ServiceInfo,
    private val logger: Logger,
    private val linkedConnectionListener: LinkedConnectionListener?,
    private val linkedConnectionStateListener: LinkedConnectionStateListener?,
    private val linkListener: LinkListener?,
    private val applicationContext: Context,
    private val userInfo: UserInfo,
) {
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    suspend fun run() {
        logger.debug("Starting scan...")
        var scanCallback: RfcommServerScanCallback? = null

        fun unregister() {
            scanCallback?.let { bleScanner.stopScan(it) }
        }

        suspendCancellableCoroutine { continuation ->
            val serviceFilter = ScanFilter.Builder().build()
            val scanFilters = listOf(serviceFilter)
            val scanSettings =
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0L)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .build()
            scanCallback =
                RfcommServerScanCallback(
                    continuation = continuation,
                    logger = logger,
                    serviceInfo = serviceInfo,
                    linkedConnectionListener = linkedConnectionListener,
                    linkedConnectionStateListener = linkedConnectionStateListener,
                    linkListener = linkListener,
                    applicationContext = applicationContext,
                    userInfo = userInfo,
                )
            logger.debug("Scan starting...")
            bleScanner.startScan(scanFilters, scanSettings, scanCallback)
            continuation.invokeOnCancellation { unregister() }
        }
    }
}

private class RfcommServerScanCallback(
    private val continuation: CancellableContinuation<Unit>,
    private val logger: Logger,
    private val serviceInfo: ServiceInfo,
    private val linkedConnectionListener: LinkedConnectionListener?,
    private val linkedConnectionStateListener: LinkedConnectionStateListener?,
    private val linkListener: LinkListener?,
    private val applicationContext: Context,
    private val userInfo: UserInfo,
) : ScanCallback() {

    private val discovered = LruCache<BluetoothDevice, Boolean>(100)

    override fun onScanFailed(errorCode: Int) {
        val errorString = BtStr.scanResultErrorCode(errorCode)
        logger.error("onScanFailed: errorString=$errorString")
        if (continuation.isActive) {
            continuation.resumeWithException(IOException("Scan failed with error $errorString"))
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
        logger.debug("onBatchScanResults: results=$results")
        results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        if (!continuation.isActive) {
            return
        }

        val device = result.device ?: return
        if (discovered[device] == true) {
            return
        }
        discovered.put(device, true)

        val serviceUuidMatch =
            result.scanRecord?.serviceUuids?.any { it.uuid.toString() == serviceInfo.uuidString } ==
                true
        if (!serviceUuidMatch) {
            return
        }
        logger.debug("onScanResult: device=${device.deviceString} matching service.")

        val connection =
            LinkedConnection.create(
                logger = logger,
                linkedConnectionStateListener = linkedConnectionStateListener,
                linkListener = linkListener,
            )
        linkedConnectionListener?.onConnection(connection)

        val currentRemoteIdentity =
            Identity(
                name = if (device.name.isNullOrBlank()) null else device.deviceString,
                deviceType = null,
                connectionType = ConnectionType.BLUETOOTH,
                appBytes = null,
            )
        connection.setLink(
            RfcommServerUnidentifiedLink(
                connection = connection,
                device = device,
                serviceInfo = serviceInfo,
                logger = logger,
                currentRemoteIdentity = currentRemoteIdentity,
                applicationContext = applicationContext,
                userInfo = userInfo,
            ),
            ConnectionState.OPENING,
        )
    }
}

private class RfcommServerUnidentifiedLink(
    connection: LinkedConnection,
    private val device: BluetoothDevice,
    logger: Logger,
    currentRemoteIdentity: Identity,
    userInfo: UserInfo,
    applicationContext: Context,
    private val serviceInfo: ServiceInfo,
) :
    RfcommUnidentifiedLink(
        device = device,
        connection = connection,
        logger = logger,
        currentRemoteIdentity = currentRemoteIdentity,
        applicationContext = applicationContext,
        userInfo = userInfo,
        protocol = Server,
        serviceInfo = serviceInfo,
    ) {
    override fun createReaderWriterCloser(): ReaderWriterCloser {
        val socket = device.createInsecureRfcommSocketToServiceRecord(serviceInfo.uuid)
        socket.connect()
        return createSocket(socket)
    }
}
