package io.explod.dog.nsd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.explod.dog.common.ConnectionPreferenceConf
import io.explod.dog.common.IOConnectedLink
import io.explod.dog.common.IOFullIdentityLink
import io.explod.dog.common.RetrySignalConf
import io.explod.dog.conn.ChainId
import io.explod.dog.conn.ConnectedLink
import io.explod.dog.conn.ConnectionState
import io.explod.dog.conn.Link
import io.explod.dog.conn.LinkListener
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.conn.LinkedConnectionListener
import io.explod.dog.conn.LinkedConnectionStateListener
import io.explod.dog.protocol.ConnectionType
import io.explod.dog.protocol.FullIdentity
import io.explod.dog.protocol.PartialIdentity
import io.explod.dog.protocol.Protocol.Client
import io.explod.dog.protocol.ServiceInfo
import io.explod.dog.protocol.UserInfo
import io.explod.dog.protocol.nsdServiceType
import io.explod.dog.util.ConfService
import io.explod.dog.util.CoroutinePackage
import io.explod.dog.util.FailureReason
import io.explod.dog.util.Provider
import io.explod.dog.util.ReaderWriterCloser
import io.explod.dog.util.Result
import io.explod.dog.util.Result.Companion.Ok
import io.explod.dog.util.locked
import io.explod.loggly.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.Socket

class NsdClientService
private constructor(
    private val serviceInfo: ServiceInfo,
    private val nsdAvailabilityConf: Provider<NsdAvailabilityConf>,
    private val clientNsdConnectionPreferenceConf: Provider<ConnectionPreferenceConf>,
    private val clientNsdRetrySignalConf: Provider<RetrySignalConf>,
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
                nsdAvailabilityConf.get().flow,
                clientNsdRetrySignalConf.get().flow,
                clientNsdConnectionPreferenceConf.get().flow,
                ::shouldRun,
            )
            .distinctUntilChanged()
    }

    private fun shouldRun(
        availability: NsdAvailabilityConf.Availability,
        retry: RetrySignalConf.Retry,
        connectionPreference: ConnectionPreferenceConf.Preference,
    ): Boolean {
        fun calculate(): Boolean {
            when (connectionPreference) {
                ConnectionPreferenceConf.Preference.CLOSED -> return false
                ConnectionPreferenceConf.Preference.OPEN -> true
            }
            when (availability) {
                NsdAvailabilityConf.Availability.AVAILABLE -> true
            }
            when (retry) {
                RetrySignalConf.Retry.READY -> true
                RetrySignalConf.Retry.BACKOFF -> return false
            }
            return true
        }

        val shouldRun = calculate()
        logger.debug(
            "shouldRun->$shouldRun: connectionPreference=$connectionPreference, availability=$availability, retry=$retry"
        )
        return shouldRun
    }

    override suspend fun runService() {
        logger.info("Booting server...")
        try {
            val logic = getLogic()
            logic.run()
        } catch (e: Exception) {
            logger.error("Server shutdown due to error: ${e.message}", e)
        } finally {
            clientNsdConnectionPreferenceConf
                .get()
                .setPreference(ConnectionPreferenceConf.Preference.CLOSED)
        }
    }

    private fun getLogic(): NsdClientServiceLogic {
        return NsdClientServiceLogic(
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
            applicationContext: Context,
            serviceInfo: ServiceInfo,
            nsdAvailabilityConf: Provider<NsdAvailabilityConf>,
            clientNsdConnectionPreferenceConf: Provider<ConnectionPreferenceConf>,
            clientNsdRetrySignalConf: Provider<RetrySignalConf>,
            io: CoroutinePackage,
            logger: Logger,
            linkedConnectionListener: LinkedConnectionListener?,
            linkedConnectionStateListener: LinkedConnectionStateListener?,
            linkListener: LinkListener?,
        ): NsdClientService {
            return NsdClientService(
                serviceInfo = serviceInfo,
                nsdAvailabilityConf = nsdAvailabilityConf,
                clientNsdConnectionPreferenceConf = clientNsdConnectionPreferenceConf,
                clientNsdRetrySignalConf = clientNsdRetrySignalConf,
                io = io,
                logger = logger.child("NsdClient"),
                linkedConnectionListener = linkedConnectionListener,
                linkedConnectionStateListener = linkedConnectionStateListener,
                linkListener = linkListener,
                applicationContext = applicationContext,
            )
        }
    }
}

private class NsdClientServiceLogic(
    private val serviceInfo: ServiceInfo,
    private val logger: Logger,
    private val linkedConnectionListener: LinkedConnectionListener?,
    private val linkedConnectionStateListener: LinkedConnectionStateListener?,
    private val linkListener: LinkListener?,
    private val applicationContext: Context,
    private val userInfo: UserInfo,
) {
    suspend fun run() {
        suspendCancellableCoroutine<Unit> { continuation ->
            val nsdManager = applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
            val discoveredServices = mutableMapOf<NsdServiceInfoKey, NsdServiceInfo>()
            val discoveryListener =
                NsdDiscoveryListener(
                    nsdManager = nsdManager,
                    linkedConnectionListener = linkedConnectionListener,
                    linkedConnectionStateListener = linkedConnectionStateListener,
                    linkListener = linkListener,
                    discoveredServices = discoveredServices,
                    logger = logger,
                    applicationContext = applicationContext,
                    userInfo = userInfo,
                    serviceInfo = serviceInfo,
                )
            nsdManager.discoverServices(
                serviceInfo.nsdServiceType,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener,
            )
            continuation.invokeOnCancellation {
                logger.debug("Cancelling discovery...")
                nsdManager.stopServiceDiscovery(discoveryListener)
            }
        }
    }
}

private data class NsdServiceInfoKey(
    private val serviceName: String,
    private val serviceType: String,
) {

    companion object {
        fun from(serviceInfo: NsdServiceInfo): NsdServiceInfoKey {
            return NsdServiceInfoKey(
                serviceName = serviceInfo.serviceName,
                serviceType = serviceInfo.serviceType,
            )
        }
    }
}

private class NsdDiscoveryListener(
    private val nsdManager: NsdManager,
    private val linkedConnectionListener: LinkedConnectionListener?,
    private val linkedConnectionStateListener: LinkedConnectionStateListener?,
    private val linkListener: LinkListener?,
    private val discoveredServices: MutableMap<NsdServiceInfoKey, NsdServiceInfo>,
    private val logger: Logger,
    private val applicationContext: Context,
    private val userInfo: UserInfo,
    private val serviceInfo: ServiceInfo,
) : NsdManager.DiscoveryListener {

    override fun onDiscoveryStarted(regType: String) {
        logger.debug("Service discovery started: regType=$regType")
    }

    override fun onDiscoveryStopped(serviceType: String) {
        logger.debug("Service discovery stopped: serviceType=$serviceType")
    }

    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        logger.debug(
            "Service start discovery failed: serviceType=$serviceType errorCode=$errorCode"
        )
    }

    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        logger.debug("Service stop discovery failed: serviceType=$serviceType errorCode=$errorCode")
    }

    override fun onServiceFound(nsdServiceInfo: NsdServiceInfo) {
        logger.debug("Service service found: serviceInfo=$nsdServiceInfo")
        val serviceInfoKey = NsdServiceInfoKey.from(nsdServiceInfo)
        if (serviceInfoKey !in discoveredServices) {
            val resolveListener =
                NsdResolveListener(
                    linkedConnectionListener = linkedConnectionListener,
                    linkedConnectionStateListener = linkedConnectionStateListener,
                    linkListener = linkListener,
                    discoveredServices = discoveredServices,
                    logger = logger,
                    applicationContext = applicationContext,
                    userInfo = userInfo,
                    serviceInfo = serviceInfo,
                )
            nsdManager.resolveService(nsdServiceInfo, resolveListener)
        }
    }

    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
        logger.debug("Service service lost: serviceInfo=$serviceInfo")
    }
}

private class NsdResolveListener(
    private val linkedConnectionListener: LinkedConnectionListener?,
    private val linkedConnectionStateListener: LinkedConnectionStateListener?,
    private val linkListener: LinkListener?,
    private val discoveredServices: MutableMap<NsdServiceInfoKey, NsdServiceInfo>,
    private val logger: Logger,
    private val applicationContext: Context,
    private val userInfo: UserInfo,
    private val serviceInfo: ServiceInfo,
) : NsdManager.ResolveListener {

    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
        logger.debug("Service service resolved: serviceInfo=$serviceInfo")
        val serviceInfoKey = NsdServiceInfoKey.from(serviceInfo)
        discoveredServices[serviceInfoKey] = serviceInfo // Save updated info
        connectService(serviceInfo)
    }

    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
        logger.debug(
            "Service service resolve failed: serviceInfo=$serviceInfo, errorCode=$errorCode"
        )
    }

    private fun connectService(nsdServiceInfo: NsdServiceInfo) {
        logger.debug("Service service connecting: serviceInfo=$serviceInfo")
        try {
            val port = nsdServiceInfo.port
            val address = nsdServiceInfo.host

            val connection =
                LinkedConnection.create(logger, linkedConnectionStateListener, linkListener)
            linkedConnectionListener?.onConnection(connection)

            val partialIdentity =
                PartialIdentity(
                    name = "$address:$port",
                    deviceType = null,
                    connectionType = ConnectionType.NSD,
                )
            val fullIdentity = FullIdentity(partialIdentity = partialIdentity, appBytes = null)
            val socket = Socket(address, port)
            val link =
                NsdClientPartialIdentityLink(
                    chainId = connection.chainId,
                    applicationContext = applicationContext,
                    connection = connection,
                    socket = socket,
                    logger = logger,
                    currentPartialIdentity = partialIdentity,
                    currentFullIdentity = fullIdentity,
                    userInfo = userInfo,
                    serviceInfo = serviceInfo,
                )
            connection.setLink(link, ConnectionState.OPENING)
        } catch (ex: Exception) {
            logger.error("Unable to create pending connection: ${ex.message}", ex)
        }
    }
}

private class NsdClientPartialIdentityLink(
    chainId: ChainId,
    connection: LinkedConnection,
    private val socket: Socket,
    logger: Logger,
    currentPartialIdentity: PartialIdentity,
    currentFullIdentity: FullIdentity,
    applicationContext: Context,
    userInfo: UserInfo,
    serviceInfo: ServiceInfo,
) :
    NsdPartialIdentityLink(
        chainId,
        socket = socket,
        connection = connection,
        logger = logger,
        currentPartialIdentity = currentPartialIdentity,
        currentFullIdentity = currentFullIdentity,
        applicationContext = applicationContext,
        userInfo = userInfo,
        protocol = Client,
        serviceInfo = serviceInfo,
    ) {
    override fun createFullIdentityLink(socket: ReaderWriterCloser): Result<Link, FailureReason> {
        return Ok(
            NsdClientFullIdentityLink(
                chainId = chainId,
                connection = connection,
                socket = socket,
                logger = logger,
                currentPartialIdentity = getPartialIdentity(),
                currentFullIdentity = getFullIdentity(),
            )
        )
    }

    override fun toString(): String {
        return "NsdClientPartialIdentityLink(device='$socket')"
    }
}

private class NsdClientFullIdentityLink(
    chainId: ChainId,
    connection: LinkedConnection,
    socket: ReaderWriterCloser,
    logger: Logger,
    currentPartialIdentity: PartialIdentity,
    currentFullIdentity: FullIdentity,
) :
    IOFullIdentityLink(
        chainId = chainId,
        connection = connection,
        socket = socket,
        logger = logger,
        currentPartialIdentity = currentPartialIdentity,
        currentFullIdentity = currentFullIdentity,
        protocol = Client,
    ) {
    override fun createConnectedLink(): Result<ConnectedLink, FailureReason> {
        return Ok(
            NsdClientConnectedLink(
                chainId = chainId,
                socket = socket,
                connection = connection,
                logger = logger,
                currentPartialIdentity = getPartialIdentity(),
                currentFullIdentity = getFullIdentity(),
            )
        )
    }

    override fun toString(): String {
        return "NsdClientFullIdentityLink(device='$socket')"
    }
}

private class NsdClientConnectedLink(
    chainId: ChainId,
    private val socket: ReaderWriterCloser,
    connection: LinkedConnection,
    logger: Logger,
    currentPartialIdentity: PartialIdentity,
    currentFullIdentity: FullIdentity,
) :
    IOConnectedLink(
        chainId = chainId,
        socket = socket,
        connection = connection,
        logger = logger,
        currentPartialIdentity = currentPartialIdentity,
        currentFullIdentity = currentFullIdentity,
    ) {
    override fun toString(): String {
        return "NsdClientConnectedLink(device='$socket')"
    }
}
