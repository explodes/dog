package io.explod.dog.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Encodes an integer using variable-length integer (Varint) encoding. This implementation is for
 * unsigned integers. For signed integers, a zigzag encoding is typically applied first.
 *
 * @param value The integer to encode. Must be non-negative.
 * @return A ByteArray representing the encoded integer.
 */
fun encodeVarint(value: Int): ByteArray {
    require(value >= 0) {
        "Varint encoding for Int usually assumes non-negative values. For signed, consider zigzag encoding first."
    }

    val outputStream = ByteArrayOutputStream()
    var tempValue = value

    while (true) {
        if ((tempValue and 0x7F) == tempValue) { // If value fits in 7 bits
            outputStream.write(tempValue)
            break
        } else {
            outputStream.write((tempValue and 0x7F) or 0x80) // Set MSB to 1 for continuation
            tempValue = tempValue ushr 7 // Unsigned right shift by 7 bits
        }
    }
    return outputStream.toByteArray()
}

/**
 * Decodes a variable-length integer (Varint) from a ByteArray.
 *
 * @param encodedBytes The ByteArray containing the encoded integer.
 * @return The decoded integer.
 * @throws IllegalArgumentException if the end of the stream is reached unexpectedly.
 */
fun decodeVarint(inputStream: InputStream): Int {
    var value = 0
    var shift = 0
    var byte: Int

    while (true) {
        if (shift >= 32) { // Prevent infinite loop for malformed data
            throw IllegalArgumentException("Malformed Varint: too many bytes for an Int.")
        }

        byte = inputStream.read()
        if (byte == -1) {
            throw IllegalArgumentException("Unexpected end of stream while decoding Varint.")
        }

        value = value or ((byte and 0x7F) shl shift)
        if ((byte and 0x80) == 0) { // If MSB is 0, it's the last byte
            break
        }
        shift += 7
    }
    return value
}

// You can also adapt these for Long:

fun encodeVarint(value: Long): ByteArray {
    require(value >= 0) {
        "Varint encoding for Long usually assumes non-negative values. For signed, consider zigzag encoding first."
    }

    val outputStream = ByteArrayOutputStream()
    var tempValue = value

    while (true) {
        if ((tempValue and 0x7FL) == tempValue) { // If value fits in 7 bits
            outputStream.write(tempValue.toInt())
            break
        } else {
            outputStream.write(
                ((tempValue and 0x7FL) or 0x80L).toInt()
            ) // Set MSB to 1 for continuation
            tempValue = tempValue ushr 7 // Unsigned right shift by 7 bits
        }
    }
    return outputStream.toByteArray()
}

fun decodeVarintLong(inputStream: InputStream): Long {
    var value = 0L
    var shift = 0
    var byte: Long

    while (true) {
        if (shift >= 64) {
            throw IllegalArgumentException("Malformed Varint: too many bytes for a Long.")
        }

        byte = inputStream.read().toLong()
        if (byte == -1L) {
            throw IllegalArgumentException("Unexpected end of stream while decoding Varint.")
        }

        value = value or ((byte and 0x7FL) shl shift)
        if ((byte and 0x80L) == 0L) {
            break
        }
        shift += 7
    }
    return value
}
