package io.explod.dog.protocol

import io.explod.dog.conn.Connection
import io.explod.dog.protocol.MessageType.JOIN
import io.explod.dog.protocol.MessageType.REJECTED
import io.explod.dog.util.FailureReason
import io.explod.dog.util.ImmutableBytes
import io.explod.dog.util.Ok
import io.explod.dog.util.Result
import io.explod.dog.util.Result.Companion.Err
import io.explod.dog.util.Result.Companion.Ok
import io.explod.dog.util.mapOk
import io.explod.loggly.Logger
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MessageType {
    const val HELLO = 3
    const val FULL_IDENTITY = 11
    const val JOIN = 42
    const val REJECTED = 13
    const val DEDUP = 77

    fun toString(type: Int): String {
        return when (type) {
            FULL_IDENTITY -> "FULL_IDENTITY"
            HELLO -> "HELLO"
            else -> "UNKNOWN_$type"
        }
    }
}

interface Protocol {
    @Throws(IOException::class)
    fun identify(
        inputStream: InputStream,
        outputStream: OutputStream,
        localIdentity: Identity,
    ): Result<Identity, FailureReason>

    @Throws(IOException::class)
    fun join(
        inputStream: InputStream,
        outputStream: OutputStream,
        logger: Logger,
        join: Join,
    ): Result<Join, FailureReason>

    suspend fun shareDeduplicationIdentity(
        connection: Connection,
        logger: Logger,
        localNonce: DedupNonce,
    ): Result<DedupNonce, FailureReason>

    object Client : Protocol {
        @Throws(IOException::class)
        override fun identify(
            inputStream: InputStream,
            outputStream: OutputStream,
            localIdentity: Identity,
        ): Result<Identity, FailureReason> {
            // Hello. Client writes first.
            outputStream.write(MessageType.HELLO)
            if (inputStream.read() != MessageType.HELLO) {
                return Err(FailureReason("Invalid hello message."))
            }

            // Identity. Client writes first.
            IO.IdentitySharing.writeIdentities(outputStream, localIdentity)
            val result = IO.IdentitySharing.readIdentities(inputStream)
            return result
        }

        @Throws(IOException::class)
        override fun join(
            inputStream: InputStream,
            outputStream: OutputStream,
            logger: Logger,
            join: Join,
        ): Result<Join, FailureReason> {
            // Client reads first.
            val allowed =
                IO.JoinNegotiation.readJoinOrReject(inputStream, logger).err {
                    return@join Err(it)
                }
            // Write even if rejected, unless there was an error or protocol error.
            IO.JoinNegotiation.writeJoinOrReject(outputStream, join.value).err {
                return@join Err(it)
            }
            return allowed.mapOk { remoteJoin ->
                // Only successfully join if we both are allowed.
                if (join == Join.ACCEPT) {
                    remoteJoin
                } else {
                    Join.REJECT
                }
            }
        }

        override suspend fun shareDeduplicationIdentity(
            connection: Connection,
            logger: Logger,
            localNonce: DedupNonce,
        ): Result<DedupNonce, FailureReason> {
            val messageTypeBuff = ByteBuffer.allocate(1).order(ByteOrder.BIG_ENDIAN)
            val longBuff = ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)

            // Client writes first.
            // Message Type
            messageTypeBuff.rewind()
            messageTypeBuff.put(MessageType.DEDUP.toByte())
            connection.send(messageTypeBuff.array()).err {
                return Err(FailureReason("Error sending hello message."))
            }
            // Nonce
            longBuff.rewind()
            longBuff.putLong(localNonce.value)
            connection.send(longBuff.array()).err {
                return Err(FailureReason("Error sending nonce."))
            }

            // Server writes second.
            // Message Type
            connection.receive(messageTypeBuff.array()).err {
                return Err(FailureReason("Error reading message type."))
            }
            val messageType = messageTypeBuff.get(0).toInt()
            if (messageType != MessageType.DEDUP) {
                return Err(FailureReason("Invalid hello message."))
            }
            // Nonce
            connection.receive(longBuff.array()).err {
                return Err(FailureReason("Error reading nonce."))
            }
            val remoteNonce = longBuff.getLong(0)
            return Ok(DedupNonce(remoteNonce))
        }
    }

    object Server : Protocol {
        @Throws(IOException::class)
        override fun identify(
            inputStream: InputStream,
            outputStream: OutputStream,
            localIdentity: Identity,
        ): Result<Identity, FailureReason> {
            // Hello. Client writes first.
            if (inputStream.read() != MessageType.HELLO) {
                return Err(FailureReason("Invalid hello message."))
            }
            outputStream.write(MessageType.HELLO)

            // Identity. Client writes first.
            val result = IO.IdentitySharing.readIdentities(inputStream)
            IO.IdentitySharing.writeIdentities(outputStream, localIdentity)
            return result
        }

        @Throws(IOException::class)
        override fun join(
            inputStream: InputStream,
            outputStream: OutputStream,
            logger: Logger,
            join: Join,
        ): Result<Join, FailureReason> {
            // Server writes first.
            IO.JoinNegotiation.writeJoinOrReject(outputStream, join.value).err {
                return@join Err(it)
            }
            // Read even if rejected, unless there was an error or protocol error.
            val allowed =
                IO.JoinNegotiation.readJoinOrReject(inputStream, logger).err {
                    return@join Err(it)
                }
            return allowed.mapOk { remoteJoin ->
                // Only successfully join if we both are allowed.
                if (join == Join.ACCEPT) {
                    remoteJoin
                } else {
                    Join.REJECT
                }
            }
        }

        override suspend fun shareDeduplicationIdentity(
            connection: Connection,
            logger: Logger,
            localNonce: DedupNonce,
        ): Result<DedupNonce, FailureReason> {
            val messageTypeBuff = ByteBuffer.allocate(1).order(ByteOrder.BIG_ENDIAN)
            val longBuff = ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)

            // Client writes first.
            // Message Type
            connection.receive(messageTypeBuff.array()).err {
                return Err(FailureReason("Error reading message type."))
            }
            val messageType = messageTypeBuff.get(0).toInt()
            if (messageType != MessageType.DEDUP) {
                return Err(FailureReason("Invalid hello message."))
            }
            // Nonce
            connection.receive(longBuff.array()).err {
                return Err(FailureReason("Error reading nonce."))
            }
            val remoteNonce = longBuff.getLong(0)

            // Server writes second.
            // Message Type
            messageTypeBuff.rewind()
            messageTypeBuff.put(MessageType.DEDUP.toByte())
            connection.send(messageTypeBuff.array()).err {
                return Err(FailureReason("Error sending hello message."))
            }
            // Nonce
            longBuff.rewind()
            longBuff.putLong(localNonce.value)
            connection.send(longBuff.array()).err {
                return Err(FailureReason("Error sending nonce."))
            }

            return Ok(DedupNonce(remoteNonce))
        }
    }

    enum class Join(internal val value: Int) {
        ACCEPT(JOIN),
        REJECT(REJECTED),
    }

    private object IO {
        object IdentitySharing {

            @Throws(IOException::class)
            fun readIdentities(inputStream: InputStream): Result<Identity, FailureReason> {
                // Read full identity.
                if (inputStream.read() != MessageType.FULL_IDENTITY) {
                    return Err(FailureReason("Invalid full identity message response."))
                }
                val connectionType = ConnectionType.fromByte(inputStream.read().toByte())
                val deviceType = DeviceType.fromByte(inputStream.read().toByte())
                val name = inputStream.readVarintLengthAndArray()?.let { String(it) }
                val appBytes =
                    inputStream.readVarintLengthAndArray()?.let { ImmutableBytes.create(it) }
                val identity = Identity(
                    name = name,
                    connectionType = connectionType,
                    deviceType = deviceType,
                    appBytes = appBytes,
                )
                return Ok(identity)
            }

            @Throws(IOException::class)
            fun writeIdentities(outputStream: OutputStream, localIdentity: Identity) {
                outputStream.write(MessageType.FULL_IDENTITY)
                outputStream.write(localIdentity.connectionType?.byte?.toInt() ?: 0)
                outputStream.write(localIdentity.deviceType?.byte?.toInt() ?: 0)
                outputStream.writeVarintLengthAndArray(localIdentity.name?.toByteArray())
                outputStream.writeVarintLengthAndArray(localIdentity.appBytes?.bytes())
            }
        }

        object JoinNegotiation {

            @Throws(IOException::class)
            fun writeJoinOrReject(
                outputStream: OutputStream,
                result: Int,
            ): Result<Ok, FailureReason> {
                outputStream.write(result)
                return Ok(Ok)
            }

            @Throws(IOException::class)
            fun readJoinOrReject(
                inputStream: InputStream,
                logger: Logger,
            ): Result<Join, FailureReason> {
                val joinResult = inputStream.read()
                when (joinResult) {
                    JOIN -> {
                        // Nice! We can continue our advancement.
                        return Ok(Join.ACCEPT)
                    }

                    REJECTED -> {
                        val message = "Connection rejected!"
                        logger.warn(message)
                        return Ok(Join.REJECT)
                    }

                    else -> {
                        val message = "Unexpected join result: $joinResult"
                        logger.warn(message)
                        return Err(FailureReason(message))
                    }
                }
            }
        }
    }
}
