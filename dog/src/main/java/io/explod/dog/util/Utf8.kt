package io.explod.dog.util

import java.nio.charset.MalformedInputException
import java.nio.charset.StandardCharsets

fun String.truncateUtf8Bytes(maxBytes: Int): String {
    val bytes = this.toByteArray(StandardCharsets.UTF_8)

    if (bytes.size <= maxBytes) {
        return this
    }

    // Iterate backwards from maxBytes to find a valid UTF-8 character boundary
    // We go down to 0, which would result in an empty string if no characters fit.
    for (i in maxBytes downTo 0) {
        try {
            // Attempt to decode a substring of bytes
            // The String(byte[], Charset) constructor will throw MalformedInputException
            // if the byte sequence is invalid (i.e., we cut a multi-byte character).
            val truncated = String(bytes, 0, i, StandardCharsets.UTF_8)
            // If decoding succeeds, we found a valid boundary
            return truncated
        } catch (e: MalformedInputException) {
            // This means we cut a multi-byte character. Try a shorter segment.
            continue
        } catch (e: IllegalArgumentException) {
            // This can happen if 'i' is negative (though our loop ensures i >= 0).
            // Or potentially if the charset is not supported (which StandardCharsets.UTF_8 is).
            // For robustness, consider if you want to handle it.
            continue
        }
    }
    // If we reach here, it means even 0 bytes couldn't be decoded (highly unlikely
    // with maxBytes >= 0), or no valid character could be formed.
    return ""
}
