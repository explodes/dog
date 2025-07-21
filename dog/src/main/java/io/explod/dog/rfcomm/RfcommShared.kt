@file:OptIn(ExperimentalCoroutinesApi::class)

package io.explod.dog.rfcomm

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import io.explod.dog.common.IOPartialIdentityLink
import io.explod.dog.conn.ChainId
import io.explod.dog.conn.Link
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.protocol.FullIdentity
import io.explod.dog.protocol.PartialIdentity
import io.explod.dog.protocol.Protocol
import io.explod.dog.protocol.ServiceInfo
import io.explod.dog.protocol.UserInfo
import io.explod.dog.util.FailureReason
import io.explod.dog.util.ReaderWriterCloser
import io.explod.dog.util.Result
import io.explod.dog.util.Result.Companion.Err
import io.explod.dog.util.deviceString
import io.explod.dog.util.getParcelableExtraCompat
import io.explod.loggly.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import java.io.InputStream
import java.io.OutputStream

/** Broadcast receiver for bond state changes for a specific device. */
internal class BondStateBroadcastReceiver(
    private val device: BluetoothDevice,
    private val bondStateDeferred: CompletableDeferred<Boolean>,
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = (intent ?: return).action // July 7, 2025: TIL
        if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
            if (deviceMatches(intent)) {
                bondStateDeferred.complete(isBonded(intent))
            }
        }
    }

    private fun deviceMatches(intent: Intent): Boolean {
        val incomingDevice =
            intent.getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        return incomingDevice == device
    }

    private fun isBonded(intent: Intent): Boolean {
        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
        return bondState == BluetoothDevice.BOND_BONDED
    }

    fun filter(): IntentFilter {
        return IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    }
}

abstract class RfcommPartialIdentityLink(
    connection: LinkedConnection,
    private val device: BluetoothDevice,
    logger: Logger,
    currentPartialIdentity: PartialIdentity,
    currentFullIdentity: FullIdentity,
    applicationContext: Context,
    userInfo: UserInfo,
    protocol: Protocol,
    private val serviceInfo: ServiceInfo,
) :
    IOPartialIdentityLink(
        connection = connection,
        logger = logger,
        currentPartialIdentity = currentPartialIdentity,
        currentFullIdentity = currentFullIdentity,
        applicationContext = applicationContext,
        userInfo = userInfo,
        protocol = protocol,
    ) {

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun isBonded(): Boolean {
        return device.bondState == BluetoothDevice.BOND_BONDED
    }

    protected fun createSocket(socket: BluetoothSocket): ReaderWriterCloser {
        return object : ReaderWriterCloser {
            override val inputStream: InputStream
                get() = socket.inputStream

            override val outputStream: OutputStream
                get() = socket.outputStream

            override fun close() {
                socket.close()
            }

            override fun toString(): String {
                val fullIdentity = getFullIdentity()
                val partialIdentity = getPartialIdentity()
                val name = fullIdentity.partialIdentity.name ?: partialIdentity.name
                return if (name != null) {
                    "Socket(rfcomm=${serviceInfo.systemName},$name,${device.address})"
                } else {
                    "Socket(rfcomm=${serviceInfo.systemName},${device.address})"
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun advanceByBonding(): Result<Link, FailureReason> {
        val receiverBondStateDeferred = CompletableDeferred<Boolean>()
        val bondStateReceiver = BondStateBroadcastReceiver(device, receiverBondStateDeferred)
        ContextCompat.registerReceiver(
            applicationContext,
            bondStateReceiver,
            bondStateReceiver.filter(),
            RECEIVER_NOT_EXPORTED,
        )
        try {
            if (!device.createBond()) {
                return Err(FailureReason("Unable to start device bonding."))
            }
            val bondState = pollBondStatesWithTimeout(receiverBondStateDeferred)
            return if (bondState) {
                logger.debug("Bonding succeeded. Advancing...")
                advanceAlreadyBonded()
            } else {
                Err(FailureReason("Bonding failed."))
            }
        } catch (ex: Exception) {
            return Err(FailureReason("Bonding failed: ${ex.message}"))
        } finally {
            applicationContext.unregisterReceiver(bondStateReceiver)
        }
    }

    /** Waits for either the bondState to complete or the device to report being bonded. */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun pollBondStatesWithTimeout(
        receiverBondStateDeferred: CompletableDeferred<Boolean>
    ): Boolean {
        return coroutineScope {
            val loop = async {
                logger.debug("Polling for bonding state...")
                while (isActive) {
                    if (isBonded()) {
                        logger.debug("Got bonded device state: ${device.deviceString}")
                        receiverBondStateDeferred.complete(true)
                        return@async true
                    }
                    delay(BONDING_POLL_DELAY)
                }
                false
            }
            val await = async {
                logger.debug("Waiting for receiver bonding state...")
                val bondState = receiverBondStateDeferred.await()
                logger.debug(
                    "Got receiver bonding state: $bondState for device ${device.deviceString}"
                )
                bondState
            }
            select {
                await.onAwait {}
                loop.onAwait {}
                onTimeout(BONDING_TIMEOUT) { logger.debug("Timeout waiting for bonding.") }
            }
            await.cancel()
            loop.cancel()
            logger.debug("pollBondStatesWithTimeout exiting for device ${device.deviceString}")
            isBonded()
        }
    }

    companion object {
        private const val BONDING_TIMEOUT = 10_000L
        private const val BONDING_POLL_DELAY = 300L
    }
}
