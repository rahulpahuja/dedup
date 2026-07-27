package com.rp.dedup.core.search

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FloatArrayConverter {
    @TypeConverter
    fun fromFloatArray(value: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(value.size * Float.SIZE_BYTES).apply {
            order(ByteOrder.nativeOrder())
            value.forEach { putFloat(it) }
        }
        return buf.array()
    }

    @TypeConverter
    fun toFloatArray(value: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(value).apply { order(ByteOrder.nativeOrder()) }
        return FloatArray(value.size / Float.SIZE_BYTES) { buf.float }
    }
}
