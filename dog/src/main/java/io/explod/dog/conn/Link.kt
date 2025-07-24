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

    /** Get the currently available identity of the remote device. */
    abstract fun getIdentity(): Identity

    /** Permanently close this link. No effect if the link is already closed. */
    internal abstract suspend fun close()
}

abstract class PartialIdentityLink(override val chainId: ChainId) : Link() {
    /**
     * Advances to the next Link state. If bonding is allowed and required, user-interaction will be
     * necessary to complete bonding!
     */
    @CheckResult abstract suspend fun advance(allowBonding: Boolean): Result<Link, FailureReason>

    /** True if the underlying connection is already bonded (bluetooth pairing). */
    abstract fun isBonded(): Boolean
}

abstract class FullIdentityLink(override val chainId: ChainId) : Link() {

    /** Advances to the final Link state. */
    @CheckResult abstract suspend fun advance(join: Join): Result<ConnectedLink, FailureReason>
}

abstract class ConnectedLink(override val chainId: ChainId) : Link() {

    /** Send raw bytes to the remote device. */
    @CheckResult abstract suspend fun send(bytes: ByteArray): Result<Ok, FailureReason>

    /**
     * Receive raw bytes from the remote device. Control messages are handled at the "Connection"
     * layer before they reach the 3p.
     */
    @CheckResult abstract suspend fun receive(bytes: ByteArray): Result<Int, FailureReason>
}
