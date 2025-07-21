package io.explod.dog.util

/** Immutable copy of a ByteArray. Provides `get` and `iterator` operators. */
class ImmutableBytes private constructor(private val bytes: ByteArray) {
    operator fun get(index: Int): Byte {
        return bytes[index]
    }

    operator fun iterator(): Iterator<Byte> {
        return bytes.iterator()
    }

    fun size(): Int {
        return bytes.size
    }

    /** Return a copy of the bytes. */
    fun bytes(): ByteArray {
        return bytes.copyOf()
    }

    override fun toString(): String {
        return "ImmutableBytes(size=${bytes.size})"
    }

    companion object {
        /** Create ImmutableBytes from a copy of the provided bytes. */
        fun create(bytes: ByteArray): ImmutableBytes {
            return ImmutableBytes(bytes.copyOf())
        }
    }
}
