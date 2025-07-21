package io.explod.dog

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import io.explod.dog.conn.LinkListener
import io.explod.dog.conn.LinkedConnectionListener
import io.explod.dog.conn.LinkedConnectionStateListener
import io.explod.dog.nsd.NsdAvailabilityConf
import io.explod.dog.protocol.ServiceInfo
import io.explod.dog.rfcomm.BleAvailabilityConf
import io.explod.dog.rfcomm.BluetoothPermissionsConf
import io.explod.dog.util.CoroutinePackage
import io.explod.dog.util.provideSingleton
import io.explod.dog.util.state.BluetoothChecker
import io.explod.dog.util.state.PermissionsChecker
import io.explod.dog.util.state.PollerLooper
import io.explod.loggly.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class Dog(
    context: Context,
    private val serviceInfo: ServiceInfo,
    private val logger: Logger,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val allowedTechnologies: Set<Tech> = setOf(Tech.NSD, Tech.RFCOMM),
) {
    private val applicationContext = context.applicationContext
    private val bluetoothManager =
        applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val io = CoroutinePackage.create(ioDispatcher)

    private val bluetoothPermissionsConf = provideSingleton {
        val permissionsChecker =
            PermissionsChecker(
                applicationContext = applicationContext,
                permissionsToCheck =
                    fun(): List<String> {
                        val perms = mutableListOf<String>()

                        // Bluetooth permissions.
                        perms.add(Manifest.permission.BLUETOOTH)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            perms.add(Manifest.permission.BLUETOOTH_SCAN)
                            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                        }

                        // Location / Nearby permissions
                        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
                        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)

                        return perms
                    }(),
                logger = logger,
            )
        val permissionsPollerLooper =
            PollerLooper(
                    loopDelay = PermissionsChecker.LOOP_DELAY,
                    jobScope = io.scope,
                    checker = permissionsChecker,
                )
                .apply {
                    // TODO: Remove need for start, using cold flows.
                    start()
                }
        BluetoothPermissionsConf(permissionsPollerLooper = permissionsPollerLooper)
    }

    private val bleAvailabilityConf = provideSingleton {
        val bluetoothChecker = BluetoothChecker(bluetoothAdapter = bluetoothAdapter)
        val bluetoothPollerLooper =
            PollerLooper(
                    loopDelay = BluetoothChecker.LOOP_DELAY,
                    jobScope = io.scope,
                    checker = bluetoothChecker,
                )
                .apply {
                    // TODO: Remove need for start, using cold flows.
                    start()
                }
        BleAvailabilityConf(bluetoothAdapter, bluetoothPollerLooper)
    }

    private val nsdAvailabilityConf = provideSingleton { NsdAvailabilityConf() }

    fun createClient(
        linkedConnectionListener: LinkedConnectionListener?,
        linkedConnectionStateListener: LinkedConnectionStateListener?,
        linkListener: LinkListener?,
    ): DogClient {
        return DogClient(
            bluetoothAdapter = bluetoothAdapter,
            serviceInfo = serviceInfo,
            bluetoothPermissionsConf = bluetoothPermissionsConf,
            bleAvailabilityConf = bleAvailabilityConf,
            io = io,
            logger = logger,
            linkedConnectionListener = linkedConnectionListener,
            linkedConnectionStateListener = linkedConnectionStateListener,
            linkListener = linkListener,
            applicationContext = applicationContext,
            nsdAvailabilityConf = nsdAvailabilityConf,
            allowedTechnologies = allowedTechnologies,
        )
    }

    fun createServer(
        linkedConnectionListener: LinkedConnectionListener?,
        linkedConnectionStateListener: LinkedConnectionStateListener?,
        linkListener: LinkListener?,
    ): DogServer {
        return DogServer(
            bluetoothAdapter = bluetoothAdapter,
            serviceInfo = serviceInfo,
            bluetoothPermissionsConf = bluetoothPermissionsConf,
            bleAvailabilityConf = bleAvailabilityConf,
            io = io,
            logger = logger,
            linkedConnectionListener = linkedConnectionListener,
            linkedConnectionStateListener = linkedConnectionStateListener,
            linkListener = linkListener,
            applicationContext = applicationContext,
            nsdAvailabilityConf = nsdAvailabilityConf,
            allowedTechnologies = allowedTechnologies,
        )
    }

    enum class Tech {
        NSD,
        RFCOMM,
    }
}
