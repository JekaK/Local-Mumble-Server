package ua.school.localmumble.core

import java.io.ByteArrayOutputStream

/** Mumble voice integers use a prefix encoding, distinct from protobuf varints. */
internal object MumbleVarInt {
    fun encode(value: Long): ByteArray {
        require(value in 0..0xffff_ffffL)
        val out = ByteArrayOutputStream()
        when {
            value < 0x80 -> out.write(value.toInt())
            value < 0x4000 -> { out.write((value shr 8).toInt() or 0x80); out.write(value.toInt()) }
            value < 0x20_0000 -> { out.write((value shr 16).toInt() or 0xc0); repeat(2) { out.write((value shr (8 - it * 8)).toInt()) } }
            value < 0x1000_0000 -> { out.write((value shr 24).toInt() or 0xe0); repeat(3) { out.write((value shr (16 - it * 8)).toInt()) } }
            else -> { out.write(0xf0); repeat(4) { out.write((value shr (24 - it * 8)).toInt()) } }
        }
        return out.toByteArray()
    }
    class Reader(private val data: ByteArray, start: Int = 0) {
        var position = start
            private set
        val remaining get() = data.size - position
        fun byte(): Int { require(remaining > 0); return data[position++].toInt() and 255 }
        fun skip(size: Int) { require(size in 0..remaining); position += size }
        fun unsigned(): Long {
            val first = byte()
            var value: Long
            val count: Int
            when {
                first < 0x80 -> return first.toLong()
                first < 0xc0 -> { value = (first and 0x3f).toLong(); count = 1 }
                first < 0xe0 -> { value = (first and 0x1f).toLong(); count = 2 }
                first < 0xf0 -> { value = (first and 0x0f).toLong(); count = 3 }
                first == 0xf0 -> { value = 0; count = 4 }
                first == 0xf4 -> { value = 0; count = 8 }
                else -> error("Negative or invalid voice integer")
            }
            repeat(count) {
                require(value >= 0 && value <= (Long.MAX_VALUE ushr 8)) { "Voice integer overflow" }
                value = (value shl 8) or byte().toLong()
            }
            return value
        }
    }
}
