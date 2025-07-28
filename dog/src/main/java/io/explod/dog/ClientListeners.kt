package io.explod.dog

import io.explod.dog.conn.ConnectedLink
import io.explod.dog.conn.IdentifiedLink
import io.explod.dog.conn.Link
import io.explod.dog.conn.LinkedConnection
import io.explod.dog.conn.UnidentifiedLink
import io.explod.dog.conn.advanceInScope
import io.explod.dog.protocol.Protocol
import io.explod.loggly.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
