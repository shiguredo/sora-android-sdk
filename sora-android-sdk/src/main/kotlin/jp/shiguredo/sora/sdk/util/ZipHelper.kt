package jp.shiguredo.sora.sdk.util

import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.DeflaterInputStream
import java.util.zip.InflaterInputStream

class ZipHelper {
    companion object {
        fun zip(buffer: ByteBuffer): ByteBuffer {
            return ByteBuffer.wrap(DeflaterInputStream(ByteBufferBackedInputStream(buffer)).readBytes())
        }

        fun unzip(buffer: ByteBuffer): ByteBuffer {
            return ByteBuffer.wrap(InflaterInputStream(ByteBufferBackedInputStream(buffer)).readBytes())
        }
    }

    private class ByteBufferBackedInputStream(private val buf: ByteBuffer) : InputStream() {
        override fun read(): Int {
            return if (!buf.hasRemaining()) {
                -1
            } else {
                buf.get().toInt() and 0xFF
            }
        }

        override fun read(bytes: ByteArray?, off: Int, len: Int): Int {
            var len = len
            if (!buf.hasRemaining()) {
                return -1
            }
            len = Math.min(len, buf.remaining())
            buf.get(bytes, off, len)
            return len
        }
    }
}
