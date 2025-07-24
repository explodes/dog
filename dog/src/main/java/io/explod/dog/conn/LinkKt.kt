package io.explod.dog.conn

import io.explod.dog.protocol.Protocol.Join
import io.explod.loggly.Logger

suspend fun UnidentifiedLink.advanceInScope(logger: Logger, allowPairing: Boolean) {
    advance(allowPairing).err { logger.error("Unable to advance partial identity link: $it") }
}

suspend fun IdentifiedLink.advanceInScope(join: Join, logger: Logger) {
    advance(join).err { logger.error("Unable to advance full identity link: $it") }
}
