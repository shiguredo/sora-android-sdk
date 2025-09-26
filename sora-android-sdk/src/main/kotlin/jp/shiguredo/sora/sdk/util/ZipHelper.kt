package jp.shiguredo.sora.sdk.util

import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.DeflaterInputStream
import java.util.zip.InflaterInputStream

class ZipHelper {
    companion object {
        fun zip(buffer: ByteBuffer): ByteBuffer = ByteBuffer.wrap(DeflaterInputStream(ByteBufferBackedInputStream(buffer)).readBytes())

        fun unzip(buffer: ByteBuffer): ByteBuffer = ByteBuffer.wrap(InflaterInputStream(ByteBufferBackedInputStream(buffer)).readBytes())
    }

    private class ByteBufferBackedInputStream(
        private val buf: ByteBuffer,
    ) : InputStream() {
        override fun read(): Int =
            if (!buf.hasRemaining()) {
                -1
            } else {
                buf.get().toInt() and 0xFF
            }

        override fun read(
            bytes: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            // Java からの呼び出し時の安全性確保（null と範囲チェック）
            val dst =
                (bytes as ByteArray?)
                    ?: throw NullPointerException("bytes must not be null")

            if (off < 0 || len < 0 || off > dst.size || len > dst.size - off) {
                throw IndexOutOfBoundsException("off=$off, len=$len, size=${dst.size}")
            }

            if (!buf.hasRemaining()) {
                return -1
            }

            val toRead = minOf(len, buf.remaining())
            buf.get(dst, off, toRead)
            return toRead
        }
    }
}
