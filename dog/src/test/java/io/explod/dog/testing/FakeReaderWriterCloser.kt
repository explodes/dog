package io.explod.dog.testing

import io.explod.dog.util.ReaderWriterCloser
import java.io.InputStream
import java.io.OutputStream

class FakeReaderWriterCloser() : ReaderWriterCloser {

    private val readBuffer = Buffer()

    override val inputStream: InputStream
        get() = readBuffer.inputStream

    override val outputStream =
        object : OutputStream() {
            override fun write(b: Int) {}
        }

    fun readerQueue(): OutputStream {
        return readBuffer.outputStream
    }

    override fun close() {}

}
