package io.explod.dog.util

@JvmInline
/** Reason an IO operation failed. */
value class FailureReason(
    /** System-friendly reason for failure. */
    val message: String
)
