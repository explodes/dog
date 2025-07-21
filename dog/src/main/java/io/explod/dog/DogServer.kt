package io.explod.dog

import android.bluetooth.BluetoothAdapter
import android.content.Context
import io.explod.dog.common.ConnectionPreferenceConf
import io.explod.dog.common.RetrySignalConf
import io.explod.dog.conn.LinkListener
import io.explod.dog.conn.LinkedConnectionListener
import io.explod.dog.conn.LinkedConnectionStateListener
import io.explod.dog.nsd.NsdAvailabilityConf
import io.explod.dog.nsd.NsdServerService
import io.explod.dog.protocol.ServiceInfo
import io.explod.dog.protocol.UserInfo
import io.explod.dog.rfcomm.BleAvailabilityConf
import io.explod.dog.rfcomm.BluetoothPermissionsConf
import io.explod.dog.rfcomm.RfcommServerService
import io.explod.dog.util.CoroutinePackage
import io.explod.dog.util.Provider
import io.explod.dog.util.provideSingleton
import io.explod.loggly.Logger

class DogServer
internal constructor(
    bluetoothAdapter: BluetoothAdapter?,
    serviceInfo: ServiceInfo,
    bluetoothPermissionsConf: Provider<BluetoothPermissionsConf>,
    bleAvailabilityConf: Provider<BleAvailabilityConf>,
    private val io: CoroutinePackage,
    private val logger: Logger,
    linkedConnectionListener: LinkedConnectionListener?,
    linkedConnectionStateListener: LinkedConnectionStateListener?,
    linkListener: LinkListener?,
    applicationContext: Context,
    nsdAvailabilityConf: Provider<NsdAvailabilityConf>,
    private val allowedTechnologies: Set<Dog.Tech>,
) {

    private val serverRfcommConnectionPreferenceConf = provideSingleton {
        ConnectionPreferenceConf()
    }

    private val serverBluetoothRetrySignalConf = provideSingleton { RetrySignalConf() }

    private val serverNsdConnectionPreferenceConf = provideSingleton { ConnectionPreferenceConf() }

    private val serverNsdRetrySignalConf = provideSingleton { RetrySignalConf() }

    private val rfcommServerService =
        provideSingleton {
                RfcommServerService.create(
                    bluetoothAdapter = bluetoothAdapter,
                    serviceInfo = serviceInfo,
                    bluetoothPermissionsConf = bluetoothPermissionsConf,
                    bleAvailabilityConf = bleAvailabilityConf,
                    serverRfcommConnectionPreferenceConf = serverRfcommConnectionPreferenceConf,
                    serverBluetoothRetrySignalConf = serverBluetoothRetrySignalConf,
                    io = io,
                    logger = logger,
                    linkedConnectionListener = linkedConnectionListener,
                    linkedConnectionStateListener = linkedConnectionStateListener,
                    linkListener = linkListener,
                    applicationContext = applicationContext,
                )
            }
            .apply { get().observeConf() }

    private val nsdServerService =
        provideSingleton {
                NsdServerService.create(
                    nsdAvailabilityConf = nsdAvailabilityConf,
                    io = io,
                    logger = logger,
                    applicationContext = applicationContext,
                    serviceInfo = serviceInfo,
                    serverNsdConnectionPreferenceConf = serverNsdConnectionPreferenceConf,
                    serverNsdRetrySignalConf = serverNsdRetrySignalConf,
                    linkedConnectionListener = linkedConnectionListener,
                    linkedConnectionStateListener = linkedConnectionStateListener,
                    linkListener = linkListener,
                )
            }
            .apply { get().observeConf() }

    fun startServer(userInfo: UserInfo) {
        logger.debug("Starting server...")
        // TODO: designate end time (timeout?, after connection?, both?)

        // RFCOMM
        if (Dog.Tech.RFCOMM in allowedTechnologies) {
            rfcommServerService.get().setUserInfo(userInfo)
            serverRfcommConnectionPreferenceConf
                .get()
                .setPreference(ConnectionPreferenceConf.Preference.OPEN)
        }

        // NSD
        if (Dog.Tech.NSD in allowedTechnologies) {
            nsdServerService.get().setUserInfo(userInfo)
            serverNsdConnectionPreferenceConf
                .get()
                .setPreference(ConnectionPreferenceConf.Preference.OPEN)
        }
    }

    fun stopServer() {
        logger.debug("Stopping server...")

        // RFCOMM
        if (Dog.Tech.RFCOMM in allowedTechnologies) {
            serverRfcommConnectionPreferenceConf
                .get()
                .setPreference(ConnectionPreferenceConf.Preference.CLOSED)
        }

        // NSD
        if (Dog.Tech.NSD in allowedTechnologies) {
            serverNsdConnectionPreferenceConf
                .get()
                .setPreference(ConnectionPreferenceConf.Preference.CLOSED)
        }
    }
}
