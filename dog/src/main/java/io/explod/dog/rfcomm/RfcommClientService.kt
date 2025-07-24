@file:OptIn(ExperimentalAtomicApi::class)

package io.explod.dog.rfcomm

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import io.explod.dog.common.ConnectionPreferenceConf
import io.explod.dog.common.RetrySignalConf
import io.explod.dog.conn.ConnectionState
import io.explod.dog.conn.LinkListener
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.conn.LinkedConnectionListener
import io.explod.dog.conn.LinkedConnectionStateListener
import io.explod.dog.protocol.ConnectionType
import io.explod.dog.protocol.Identity
import io.explod.dog.protocol.Protocol.Client
import io.explod.dog.protocol.ServiceInfo
import io.explod.dog.protocol.UserInfo
import io.explod.dog.util.BtStr.advertiseErrorCode
import io.explod.dog.util.ConfService
import io.explod.dog.util.CoroutinePackage
import io.explod.dog.util.Provider
import io.explod.dog.util.ReaderWriterCloser
import io.explod.dog.util.deviceString
import io.explod.dog.util.locked
import io.explod.dog.util.state.PermissionsChecker
import io.explod.loggly.Logger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resumeWithException

class RfcommClientService
private constructor(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val serviceInfo: ServiceInfo,
    private val bluetoothPermissionsConf: Provider<BluetoothPermissionsConf>,
    private val bleAvailabilityConf: Provider<BleAvailabilityConf>,
    private val clientRfcommConnectionPreferenceConf: Provider<ConnectionPreferenceConf>,
    private val clientBluetoothRetrySignalConf: Provider<RetrySignalConf>,
    private val io: CoroutinePackage,
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
                clientBluetoothRetrySignalConf.get().flow,
                clientRfcommConnectionPreferenceConf.get().flow,
                ::shouldRun,
            )
            .distinctUntilChanged()
    }

    private fun shouldRun(
        permissionsGranted: PermissionsChecker.PermissionStatus,
        availability: BleAvailabilityConf.Availability,
        retry: RetrySignalConf.Retry,
        connectionPreference: ConnectionPreferenceConf.Preference,
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
            "shouldRun->$shouldRun: connectionPreference=$connectionPreference, permissionsGranted=$permissionsGranted, availability=$availability, retry=$retry"
        )
        return shouldRun
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT]
    )
    override suspend fun runService() {
        logger.info("Booting server...")
        try {
            val logic = getLogic()
            logic?.run()
        } catch (e: Exception) {
            logger.error("Server shutdown due to error: ${e.message}", e)
        } finally {
            clientRfcommConnectionPreferenceConf
                .get()
                .setPreference(ConnectionPreferenceConf.Preference.CLOSED)
        }
    }

    private fun getLogic(): RfcommClientServiceLogic? {
        val bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bluetoothAdapter == null || bleAdvertiser == null) {
            return null
        }
        return RfcommClientServiceLogic(
            bluetoothAdapter = bluetoothAdapter,
            bleAdvertiser = bleAdvertiser,
            serviceInfo = serviceInfo,
            logger = logger,
            io = io,
            linkedConnectionListener = linkedConnectionListener,
            linkedConnectionStateListener = linkedConnectionStateListener,
            linkListener = linkListener,
            applicationContext = applicationContext,
            userInfo = userInfo.locked { it },
        )
    }

    companion object {
        fun create(
            applicationContext: Context,
            bluetoothAdapter: BluetoothAdapter?,
            serviceInfo: ServiceInfo,
            bluetoothPermissionsConf: Provider<BluetoothPermissionsConf>,
            bleAvailabilityConf: Provider<BleAvailabilityConf>,
            clientRfcommConnectionPreferenceConf: Provider<ConnectionPreferenceConf>,
            clientBluetoothRetrySignalConf: Provider<RetrySignalConf>,
            io: CoroutinePackage,
            logger: Logger,
            linkedConnectionListener: LinkedConnectionListener?,
            linkedConnectionStateListener: LinkedConnectionStateListener?,
            linkListener: LinkListener?,
        ): RfcommClientService {
            return RfcommClientService(
                bluetoothAdapter = bluetoothAdapter,
                serviceInfo = serviceInfo,
                bluetoothPermissionsConf = bluetoothPermissionsConf,
                bleAvailabilityConf = bleAvailabilityConf,
                clientRfcommConnectionPreferenceConf = clientRfcommConnectionPreferenceConf,
                clientBluetoothRetrySignalConf = clientBluetoothRetrySignalConf,
                io = io,
                logger = logger.child("RfcommClient"),
                linkedConnectionListener = linkedConnectionListener,
                linkedConnectionStateListener = linkedConnectionStateListener,
                linkListener = linkListener,
                applicationContext = applicationContext,
            )
        }
    }
}

private class RfcommClientServiceLogic(
    private val bluetoothAdapter: BluetoothAdapter,
    private val bleAdvertiser: BluetoothLeAdvertiser,
    private val serviceInfo: ServiceInfo,
    private val logger: Logger,
    private val io: CoroutinePackage,
    private val linkedConnectionListener: LinkedConnectionListener?,
    private val linkedConnectionStateListener: LinkedConnectionStateListener?,
    private val linkListener: LinkListener?,
    private val applicationContext: Context,
    private val userInfo: UserInfo,
    private val onceOnly: Boolean = false,
) {
    private var callback: BleAdvertiseCallback? = null
    private var serverSocket: BluetoothServerSocket? = null

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT]
    )
    suspend fun run() {
        suspendCancellableCoroutine { continuation ->
            try {
                runCoroutine(continuation)
            } catch (e: IOException) {
                logger.error("IO exception during startService: ${e.message}", e)
                unregister()
                continuation.resumeWithException(e)
            } catch (e: Exception) {
                logger.error("Generic exception during startService: ${e.message}", e)
                unregister()
                continuation.resumeWithException(e)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun unregister() {
        callback?.let {
            bleAdvertiser.stopAdvertising(callback)
            callback = null
            serverSocket?.close()
            serverSocket = null
        }
    }

    @Throws(IOException::class, Exception::class)
    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT]
    )
    private fun runCoroutine(continuation: CancellableContinuation<Unit>) {
        val startSignal = CompletableDeferred<StartResult>()

        val settings =
            AdvertiseSettings.Builder()
                .setTimeout(0) // No timeout
                .setConnectable(true)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .build()

        val advertiseData = AdvertiseData.Builder().setIncludeDeviceName(false).build()

        val scanData =
            AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid.fromString(serviceInfo.uuidString))
                .setIncludeDeviceName(true)
                .build()
        callback = BleAdvertiseCallback(logger, startSignal)

        logger.debug("Advertising starting...")
        bleAdvertiser.startAdvertising(settings, advertiseData, scanData, callback)
        continuation.invokeOnCancellation { unregister() }
        when (val result = runBlocking { startSignal.await() }) {
            is StartResult.Failure -> {
                unregister()
                continuation.resumeWithException(
                    IOException("Starting BLE advertising failed: ${result.errorString}")
                )
                return
            }

            is StartResult.Success -> {
                // Ok! Continue.
            }
        }

        logger.debug("Creating RFComm server...")
        val bleServerSocket =
            bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                serviceInfo.friendlyName,
                serviceInfo.uuid,
            )
        this.serverSocket = bleServerSocket
        while (continuation.isActive) {
            logger.debug("Waiting for client RFComm connection...")
            acceptAndHandleConnection(bleServerSocket)
            if (onceOnly) {
                break
            }
        }
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT]
    )
    private fun acceptAndHandleConnection(serverSocket: BluetoothServerSocket) {
        val socket = serverSocket.accept()
        logger.debug("Client connection accepted.")
        io.scope.launch { handleConnection(socket) }
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT]
    )
    private fun handleConnection(socket: BluetoothSocket) {
        val device = socket.remoteDevice
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            logger.debug("Client connection bonded.")
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
            val link =
                RfcommClientUnidentifiedLink(
                    applicationContext = applicationContext,
                    connection = connection,
                    socket = socket,
                    device = device,
                    logger = logger,
                    currentRemoteIdentity = currentRemoteIdentity,
                    userInfo = userInfo,
                    serviceInfo = serviceInfo,
                )
            connection.setLink(link, ConnectionState.OPENING)
        } else {
            // I think this won't be hit:
            // We cannot accept a connection until we're bonded, which is started by the server.
            logger.never("Skipping unbonded device ${device.deviceString}.")
            socket.close()
        }
    }
}

private class BleAdvertiseCallback(
    private val logger: Logger,
    private val startSignal: CompletableDeferred<StartResult>,
) : AdvertiseCallback() {
    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
        logger.debug("successfully started.")
        setResult(StartResult.Success)
    }

    override fun onStartFailure(errorCode: Int) {
        val errorString = advertiseErrorCode(errorCode)
        logger.debug("failed to start: errorString=$errorString")
        setResult(StartResult.Failure(errorString))
    }

    private fun setResult(result: StartResult) {
        startSignal.complete(result)
    }
}

private sealed class StartResult {
    data object Success : StartResult()

    data class Failure(val errorString: String) : StartResult()
}

private class RfcommClientUnidentifiedLink(
    connection: LinkedConnection,
    private val socket: BluetoothSocket,
    device: BluetoothDevice,
    logger: Logger,
    currentRemoteIdentity: Identity,
    applicationContext: Context,
    userInfo: UserInfo,
    serviceInfo: ServiceInfo,
) :
    RfcommUnidentifiedLink(
        device = device,
        connection = connection,
        logger = logger,
        currentRemoteIdentity = currentRemoteIdentity,
        applicationContext = applicationContext,
        userInfo = userInfo,
        protocol = Client,
        serviceInfo = serviceInfo,
    ) {
    override fun createReaderWriterCloser(): ReaderWriterCloser {
        return createSocket(socket)
    }
}
