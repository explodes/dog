package io.explod.dog.conn

import io.explod.dog.protocol.Protocol.Join
import io.explod.loggly.Logger

suspend fun PartialIdentityLink.advanceInScope(logger: Logger, allowBonding: Boolean) {
    advance(allowBonding).err { logger.error("Unable to advance partial identity link: $it") }
}

suspend fun FullIdentityLink.advanceInScope(join: Join, logger: Logger) {
    advance(join).err { logger.error("Unable to advance full identity link: $it") }
}
