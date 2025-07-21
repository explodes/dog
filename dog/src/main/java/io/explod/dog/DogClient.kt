package io.explod.dog

import android.bluetooth.BluetoothAdapter
import android.content.Context
import io.explod.dog.common.ConnectionPreferenceConf
import io.explod.dog.common.RetrySignalConf
import io.explod.dog.conn.LinkListener
import io.explod.dog.conn.LinkedConnectionListener
import io.explod.dog.conn.LinkedConnectionStateListener
import io.explod.dog.nsd.NsdAvailabilityConf
import io.explod.dog.nsd.NsdClientService
import io.explod.dog.protocol.ServiceInfo
import io.explod.dog.protocol.UserInfo
import io.explod.dog.rfcomm.BleAvailabilityConf
import io.explod.dog.rfcomm.BluetoothPermissionsConf
import io.explod.dog.rfcomm.RfcommClientService
import io.explod.dog.util.CoroutinePackage
import io.explod.dog.util.Provider
import io.explod.dog.util.provideSingleton
import io.explod.loggly.Logger

class DogClient
internal constructor(
    applicationContext: Context,
    bluetoothAdapter: BluetoothAdapter?,
    serviceInfo: ServiceInfo,
    bluetoothPermissionsConf: Provider<BluetoothPermissionsConf>,
    bleAvailabilityConf: Provider<BleAvailabilityConf>,
    private val io: CoroutinePackage,
    private val logger: Logger,
    linkedConnectionListener: LinkedConnectionListener?,
    linkedConnectionStateListener: LinkedConnectionStateListener?,
    linkListener: LinkListener?,
    nsdAvailabilityConf: Provider<NsdAvailabilityConf>,
    private val allowedTechnologies: Set<Dog.Tech>,
) {

    private val clientRfcommConnectionPreferenceConf = provideSingleton {
        ConnectionPreferenceConf()
    }

    private val clientBluetoothRetrySignalConf = provideSingleton { RetrySignalConf() }

    private val clientNsdConnectionPreferenceConf = provideSingleton { ConnectionPreferenceConf() }

    private val clientNsdRetrySignalConf = provideSingleton { RetrySignalConf() }

    private val rfcommClientService =
        provideSingleton {
                RfcommClientService.create(
                    bluetoothAdapter = bluetoothAdapter,
                    serviceInfo = serviceInfo,
                    bluetoothPermissionsConf = bluetoothPermissionsConf,
                    bleAvailabilityConf = bleAvailabilityConf,
                    clientRfcommConnectionPreferenceConf = clientRfcommConnectionPreferenceConf,
                    clientBluetoothRetrySignalConf = clientBluetoothRetrySignalConf,
                    io = io,
                    logger = logger,
                    linkedConnectionListener = linkedConnectionListener,
                    linkedConnectionStateListener = linkedConnectionStateListener,
                    linkListener = linkListener,
                    applicationContext = applicationContext,
                )
            }
            .apply { get().observeConf() }

    private val nsdClientService =
        provideSingleton {
                NsdClientService.create(
                    io = io,
                    logger = logger,
                    linkedConnectionListener = linkedConnectionListener,
                    linkedConnectionStateListener = linkedConnectionStateListener,
                    linkListener = linkListener,
                    applicationContext = applicationContext,
                    serviceInfo = serviceInfo,
                    nsdAvailabilityConf = nsdAvailabilityConf,
                    clientNsdConnectionPreferenceConf = clientNsdConnectionPreferenceConf,
                    clientNsdRetrySignalConf = clientNsdRetrySignalConf,
                )
            }
            .apply { get().observeConf() }

    fun findServer(userInfo: UserInfo) {
        logger.debug("Finding server...")
        // TODO: designate end time (timeout?, after connection?, both?)

        // RFCOMM
        if (Dog.Tech.RFCOMM in allowedTechnologies) {
            rfcommClientService.get().setUserInfo(userInfo)
            clientRfcommConnectionPreferenceConf
                .get()
                .setPreference(ConnectionPreferenceConf.Preference.OPEN)
        }

        // NSD
        if (Dog.Tech.NSD in allowedTechnologies) {
            nsdClientService.get().setUserInfo(userInfo)
            clientNsdConnectionPreferenceConf
                .get()
                .setPreference(ConnectionPreferenceConf.Preference.OPEN)
        }
    }

    fun stopFindingServer() {
        logger.debug("Stop finding server...")

        // RFCOMM
        if (Dog.Tech.RFCOMM in allowedTechnologies) {
            clientRfcommConnectionPreferenceConf
                .get()
                .setPreference(ConnectionPreferenceConf.Preference.CLOSED)
        }

        // NSD
        if (Dog.Tech.NSD in allowedTechnologies) {
            clientNsdConnectionPreferenceConf
                .get()
                .setPreference(ConnectionPreferenceConf.Preference.CLOSED)
        }
    }
}
