package com.rp.dedup.core.search

import androidx.room.TypeConverter

class LongListConverter {
    @TypeConverter
    fun fromList(list: List<Long>): String = list.joinToString(",")

    @TypeConverter
    fun toList(value: String): List<Long> =
        if (value.isEmpty()) emptyList()
        else value.split(",").mapNotNull { it.toLongOrNull() }
}
