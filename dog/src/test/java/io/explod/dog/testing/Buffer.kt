package io.explod.dog.testing

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentLinkedDeque

class Buffer {
    private val bytes = ConcurrentLinkedDeque<Byte>()

    val inputStream: InputStream = Reader(bytes)
    val outputStream: OutputStream = Writer(bytes)

    private class Reader(private val bytes: ConcurrentLinkedDeque<Byte>) : InputStream() {
        override fun available(): Int {
            return bytes.size
        }

        override fun read(): Int {
            @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            return bytes.pollFirst().toInt()
        }
    }

    private class Writer(private val bytes: ConcurrentLinkedDeque<Byte>) : OutputStream() {
        override fun write(b: Int) {
            bytes.offerLast(b.toByte())
        }
    }
}
