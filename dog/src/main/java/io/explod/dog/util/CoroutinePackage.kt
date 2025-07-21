package io.explod.dog.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

data class CoroutinePackage(
    val dispatcher: CoroutineDispatcher,
    val context: CoroutineContext,
    val scope: CoroutineScope,
) {
    companion object {
        fun create(dispatcher: CoroutineDispatcher): CoroutinePackage {
            val context = dispatcher
            val scope = CoroutineScope(context)
            return CoroutinePackage(dispatcher = dispatcher, context = context, scope = scope)
        }
    }
}
