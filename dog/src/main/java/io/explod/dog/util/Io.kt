package io.explod.dog.util

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

interface Reader {
    val inputStream: InputStream
}

interface Writer {
    val outputStream: OutputStream
}

fun interface Closer : Closeable

interface ReaderWriterCloser : Reader, Writer, Closer
