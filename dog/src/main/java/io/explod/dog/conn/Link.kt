package io.explod.dog.conn

import androidx.annotation.CheckResult
import io.explod.dog.protocol.Identity
import io.explod.dog.protocol.Protocol.Join
import io.explod.dog.util.FailureReason
import io.explod.dog.util.Ok
import io.explod.dog.util.Result

/** Link to another device. A link is continuously upgrade until a full connection is made. */
sealed class Link {
    /** An identifier shared with each Link that this Link advances to. */
    abstract val chainId: ChainId

    /**
     * Get the currently available identity of the remote device. The identity information is likely
     * only partially complete until being identified or completely connected.
     */
    abstract fun getIdentity(): Identity

    /** Permanently close this link. No effect if the link is already closed. */
    internal abstract suspend fun close()
}

/**
 * A Link that has not yet undergone the Identity process, and may not have even established a
 * physical link yet.
 */
abstract class UnidentifiedLink(override val chainId: ChainId) : Link() {
    /**
     * Advances to the next Link state.
     *
     * In this state, the link is potentially completely unknown to the remote device.
     *
     * If pairing is allowed and required, user-interaction will be necessary to complete the
     * out-of-band paring process before advancing will complete. There is an undocumented timeout
     * for this out-of-band process.
     */
    @CheckResult
    abstract suspend fun advance(allowPairing: Boolean): Result<IdentifiedLink, FailureReason>

    /** True if the underlying connection is already paired (bluetooth bonding for example). */
    abstract fun isPaired(): Boolean
}

/**
 * A Link that has undergone the Identity process, and is ready to convert to a ConnectedLink or be
 * rejected by the user.
 */
abstract class IdentifiedLink(override val chainId: ChainId) : Link() {

    /** Advances to the final Link state. */
    @CheckResult abstract suspend fun advance(join: Join): Result<ConnectedLink, FailureReason>
}

/** A link that has advanced to its final point. */
abstract class ConnectedLink(override val chainId: ChainId) : Link() {

    /** Send raw bytes to the remote device. */
    @CheckResult abstract suspend fun send(bytes: ByteArray): Result<Ok, FailureReason>

    /**
     * Receive raw bytes from the remote device. Control messages are handled at the "Connection"
     * layer before they reach the 3p.
     */
    @CheckResult abstract suspend fun receive(bytes: ByteArray): Result<Int, FailureReason>
}
