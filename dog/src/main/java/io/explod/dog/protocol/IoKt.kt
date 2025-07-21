package io.explod.dog.protocol

import io.explod.dog.util.decodeVarint
import io.explod.dog.util.encodeVarint
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val MAX_VARINT_ARRAY_SIZE = 10_240

@Throws(IOException::class)
fun OutputStream.writeVarintLengthAndArray(
    bytes: ByteArray?,
    maxLength: Int = MAX_VARINT_ARRAY_SIZE,
) {
    val length = bytes?.size ?: 0
    if (length > maxLength) {
        throw IOException("Array size too large. $length/$maxLength")
    }
    val lengthEncoded = encodeVarint(bytes?.size ?: 0)
    write(lengthEncoded)
    if (bytes != null && bytes.isNotEmpty()) {
        write(bytes)
    }
}

@Throws(IOException::class)
fun InputStream.readVarintLengthAndArray(maxLength: Int = MAX_VARINT_ARRAY_SIZE): ByteArray? {
    val length = decodeVarint(this)
    if (length == 0) {
        return null
    }
    if (length > maxLength) {
        throw IOException("Array size too large. $length/$maxLength")
    }
    val buffer = ByteArray(length)
    // TODO: read to fill the buffer.
    read(buffer)
    return buffer
}
