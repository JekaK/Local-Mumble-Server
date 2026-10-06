package ua.school.localmumble.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Bounded protobuf wire codec; unknown supported wire fields are skipped safely. */
internal class Proto private constructor(private val fields: Map<Int, List<Any>>) {
    fun number(field: Int): Long? = fields[field]?.lastOrNull() as? Long
    fun bytes(field: Int): ByteArray? = fields[field]?.lastOrNull() as? ByteArray
    fun text(field: Int): String? = bytes(field)?.let {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(it)).toString()
    }
    fun numbers(field: Int): List<Long> = fields[field].orEmpty().flatMap {
        when (it) {
            is Long -> listOf(it)
            is ByteArray -> Cursor(it).let { c -> buildList { while (c.remaining > 0) add(c.varint()) } }
            else -> emptyList()
        }
    }
    companion object {
        fun parse(data: ByteArray): Proto {
            require(data.size <= 65536) { "Control message too large" }
            val c = Cursor(data)
            val fields = mutableMapOf<Int, MutableList<Any>>()
            var count = 0
            while (c.remaining > 0) {
                require(++count <= 4096) { "Too many protobuf fields" }
                val key = c.varint()
                require(key in 8..0xffff_ffffL) { "Invalid protobuf field" }
                val id = (key ushr 3).toInt()
                when ((key and 7).toInt()) {
                    0 -> fields.getOrPut(id) { mutableListOf() }.add(c.varint())
                    2 -> {
                        val size = c.varint()
                        require(size in 0..c.remaining.toLong()) { "Truncated protobuf value" }
                        fields.getOrPut(id) { mutableListOf() }.add(c.take(size.toInt()))
                    }
                    1 -> c.take(8)
                    5 -> c.take(4)
                    else -> error("Unsupported protobuf wire type")
                }
            }
            return Proto(fields)
        }
        fun build(block: Writer.() -> Unit): ByteArray = Writer().apply(block).toByteArray()
    }
    class Writer {
        private val out = ByteArrayOutputStream()
        fun number(field: Int, value: Long) { varint(field.toLong() shl 3); varint(value) }
        fun number(field: Int, value: Int) = number(field, value.toLong())
        fun bytes(field: Int, value: ByteArray) {
            varint((field.toLong() shl 3) or 2); varint(value.size.toLong()); out.write(value)
        }
        fun text(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))
        fun toByteArray(): ByteArray = out.toByteArray()
        private fun varint(value: Long) {
            var rest = value
            while ((rest and -128L) != 0L) { out.write(((rest and 127) or 128).toInt()); rest = rest ushr 7 }
            out.write(rest.toInt())
        }
    }
    private class Cursor(private val data: ByteArray) {
        private var offset = 0
        val remaining get() = data.size - offset
        fun take(size: Int): ByteArray {
            require(size in 0..remaining) { "Truncated protobuf" }
            return data.copyOfRange(offset, offset + size).also { offset += size }
        }
        fun varint(): Long {
            var result = 0L
            for (shift in 0..63 step 7) {
                require(remaining > 0) { "Truncated varint" }
                val value = data[offset++].toInt() and 255
                if (shift == 63) require(value <= 1) { "Varint overflow" }
                result = result or ((value and 127).toLong() shl shift)
                if (value < 128) return result
            }
            error("Varint overflow")
        }
    }
}
