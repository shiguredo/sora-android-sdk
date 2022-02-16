package jp.shiguredo.sora.sdk.util

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
}
