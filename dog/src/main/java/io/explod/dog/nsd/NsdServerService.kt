package io.explod.dog.nsd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.explod.dog.common.ConnectionPreferenceConf
import io.explod.dog.common.IOConnectedLink
import io.explod.dog.common.IOFullIdentityLink
import io.explod.dog.common.RetrySignalConf
import io.explod.dog.conn.ConnectedLink
import io.explod.dog.conn.ConnectionState
import io.explod.dog.conn.FullIdentityLink
import io.explod.dog.conn.LinkListener
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.conn.LinkedConnectionListener
import io.explod.dog.conn.LinkedConnectionStateListener
import io.explod.dog.protocol.ConnectionType
import io.explod.dog.protocol.Identity
import io.explod.dog.protocol.Protocol.Server
import io.explod.dog.protocol.ServiceInfo
import io.explod.dog.protocol.UserInfo
import io.explod.dog.protocol.nsdServiceName
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resumeWithException

class NsdServerService
private constructor(
    private val serviceInfo: ServiceInfo,
    private val nsdAvailabilityConf: Provider<NsdAvailabilityConf>,
    private val serverNsdConnectionPreferenceConf: Provider<ConnectionPreferenceConf>,
    private val serverNsdRetrySignalConf: Provider<RetrySignalConf>,
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
                serverNsdRetrySignalConf.get().flow,
                serverNsdConnectionPreferenceConf.get().flow,
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
            serverNsdConnectionPreferenceConf
                .get()
                .setPreference(ConnectionPreferenceConf.Preference.CLOSED)
        }
    }

    private fun getLogic(): NsdServerServiceLogic {
        return NsdServerServiceLogic(
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
            serverNsdConnectionPreferenceConf: Provider<ConnectionPreferenceConf>,
            serverNsdRetrySignalConf: Provider<RetrySignalConf>,
            io: CoroutinePackage,
            logger: Logger,
            linkedConnectionListener: LinkedConnectionListener?,
            linkedConnectionStateListener: LinkedConnectionStateListener?,
            linkListener: LinkListener?,
        ): NsdServerService {
            return NsdServerService(
                serviceInfo = serviceInfo,
                nsdAvailabilityConf = nsdAvailabilityConf,
                serverNsdConnectionPreferenceConf = serverNsdConnectionPreferenceConf,
                serverNsdRetrySignalConf = serverNsdRetrySignalConf,
                io = io,
                logger = logger.child("NsdServer"),
                linkedConnectionListener = linkedConnectionListener,
                linkedConnectionStateListener = linkedConnectionStateListener,
                linkListener = linkListener,
                applicationContext = applicationContext,
            )
        }
    }
}

private class NsdServerServiceLogic(
    private val serviceInfo: ServiceInfo,
    private val logger: Logger,
    private val linkedConnectionListener: LinkedConnectionListener?,
    private val linkedConnectionStateListener: LinkedConnectionStateListener?,
    private val linkListener: LinkListener?,
    private val applicationContext: Context,
    private val userInfo: UserInfo,
) {

    val nsdManager = (applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager)
    var listener: NsdRegistrationListener? = null
    var openSocket: ServerSocket? = null

    val serviceType = serviceInfo.nsdServiceType
    var serviceName = serviceInfo.nsdServiceName

    private fun unregister() {
        listener?.let {
            nsdManager.unregisterService(it)
            listener = null
            openSocket?.close()
            openSocket = null
        }
    }

    suspend fun run() {
        suspendCancellableCoroutine<Unit> { continuation ->
            try {
                val serverSocket = ServerSocket(0)
                openSocket = serverSocket
                logger.info("ServerSocket initialized on port ${serverSocket.localPort}")

                val nsdServiceInfo =
                    NsdServiceInfo().apply {
                        this@apply.serviceName = this@NsdServerServiceLogic.serviceName
                        this@apply.serviceType = this@NsdServerServiceLogic.serviceType
                        this@apply.port = serverSocket.localPort
                    }

                val registrationSignal = CompletableDeferred<RegistrationResult>()
                listener = NsdRegistrationListener(registrationSignal, logger)
                nsdManager.registerService(nsdServiceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
                continuation.invokeOnCancellation { unregister() }
                when (val registrationResult = runBlocking { registrationSignal.await() }) {
                    is RegistrationResult.Failure -> {
                        unregister()
                        continuation.resumeWithException(IOException("NSD Registration failed"))
                        return@suspendCancellableCoroutine
                    }

                    is RegistrationResult.Success -> {
                        // Could be overwritten by the system.
                        serviceName = registrationResult.serviceName ?: serviceName
                    }
                }
                while (continuation.isActive) {
                    val socket = serverSocket.accept()
                    val connection =
                        LinkedConnection.create(
                            logger = logger,
                            linkedConnectionStateListener = linkedConnectionStateListener,
                            linkListener = linkListener,
                        )
                    linkedConnectionListener?.onConnection(connection)

                    val currentRemoteIdentity =
                        Identity(
                            name = "${socket.remoteSocketAddress}:${socket.port}",
                            deviceType = null,
                            connectionType = ConnectionType.BLUETOOTH,
                            appBytes = null,
                        )
                    val link =
                        NsdServerPartialIdentityLink(
                            connection = connection,
                            socket = socket,
                            serviceInfo = serviceInfo,
                            logger = logger,
                            currentRemoteIdentity = currentRemoteIdentity,
                            applicationContext = applicationContext,
                            userInfo = userInfo,
                        )
                    connection.setLink(link, ConnectionState.OPENING)
                }
            } catch (e: IOException) {
                logger.error("IO exception during run: ${e.message}", e)
                unregister()
                continuation.resumeWithException(e)
            } catch (e: Exception) {
                logger.error("Generic exception during run: ${e.message}", e)
                unregister()
                continuation.resumeWithException(e)
            }
        }
    }
}

private class NsdRegistrationListener(
    private val registrationSignal: CompletableDeferred<RegistrationResult>,
    private val logger: Logger,
) : NsdManager.RegistrationListener {

    override fun onServiceRegistered(service: NsdServiceInfo) {
        logger.info("NSD Service Registered: ${service.serviceName}")
        // Important: Store the final registered service name if it was modified by the system
        registrationSignal.complete(RegistrationResult.Success(service.serviceName))
    }

    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
        val errorMsg = "NSD Registration Failed. Error code: $errorCode, Service: $serviceInfo"
        logger.error(errorMsg)
        registrationSignal.complete(RegistrationResult.Failure(errorCode))
    }

    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
        logger.info("NSD Service Unregistered: ${serviceInfo.serviceName}")
        // This is usually called during cleanup, not during initial registration success/failure
    }

    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
        logger.error("NSD Unregistration Failed. Error code: $errorCode, Service: $serviceInfo")
        // This is usually called during cleanup, not during initial registration success/failure
    }
}

private sealed class RegistrationResult {
    data class Success(val serviceName: String?) : RegistrationResult()

    data class Failure(val errorCode: Int) : RegistrationResult()
}

private class NsdServerPartialIdentityLink(
    connection: LinkedConnection,
    private val socket: Socket,
    serviceInfo: ServiceInfo,
    logger: Logger,
    currentRemoteIdentity: Identity,
    userInfo: UserInfo,
    applicationContext: Context,
) :
    NsdPartialIdentityLink(
        connection = connection,
        serviceInfo = serviceInfo,
        logger = logger,
        currentRemoteIdentity = currentRemoteIdentity,
        userInfo = userInfo,
        protocol = Server,
        applicationContext = applicationContext,
        socket = socket,
    ) {

    override fun createFullIdentityLink(
        socket: ReaderWriterCloser
    ): Result<FullIdentityLink, FailureReason> {
        return Ok(
            NsdServerFullIdentityLink(
                socket = socket,
                connection = connection,
                logger = logger,
                currentRemoteIdentity = getIdentity(),
            )
        )
    }

    override fun toString(): String {
        return "NsdServerPartialIdentityLink(device='$socket')"
    }
}

private class NsdServerFullIdentityLink(
    socket: ReaderWriterCloser,
    connection: LinkedConnection,
    logger: Logger,
    currentRemoteIdentity: Identity,
) :
    IOFullIdentityLink(
        connection = connection,
        socket = socket,
        logger = logger,
        currentRemoteIdentity = currentRemoteIdentity,
        protocol = Server,
    ) {
    override fun createConnectedLink(): Result<ConnectedLink, FailureReason> {
        return Ok(
            NsdServerConnectedLink(
                socket = socket,
                connection = connection,
                logger = logger,
                currentRemoteIdentity = getIdentity(),
            )
        )
    }

    override fun toString(): String {
        return "NsdServerFullIdentityLink(device='$socket')"
    }
}

private class NsdServerConnectedLink(
    private val socket: ReaderWriterCloser,
    connection: LinkedConnection,
    logger: Logger,
    currentRemoteIdentity: Identity,
) :
    IOConnectedLink(
        socket = socket,
        connection = connection,
        logger = logger,
        currentRemoteIdentity = currentRemoteIdentity,
    ) {
    override fun toString(): String {
        return "NsdServerConnectedLink(device='$socket')"
    }
}
