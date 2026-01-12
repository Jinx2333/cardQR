package com.example.qrtask.data.database

import androidx.room.TypeConverter

/**
 * Room类型转换器 - 用于将List<String>转换为数据库可存储的格式
 */
class Converters {
    
    @TypeConverter
    fun fromStringList(value: String): List<String> {
        return if (value.isEmpty()) {
            emptyList()
        } else {
            value.split(",").map { it.trim() }
        }
    }
    
    @TypeConverter
    fun toStringList(value: List<String>): String {
        return value.joinToString(",")
    }
}
