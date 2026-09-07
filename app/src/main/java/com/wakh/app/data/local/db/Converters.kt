package com.wakh.app.data.local.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromMessageDirection(value: MessageDirection): String = value.name

    @TypeConverter
    fun toMessageDirection(value: String): MessageDirection = MessageDirection.valueOf(value)

    @TypeConverter
    fun fromMessageKind(value: MessageKind): String = value.name

    @TypeConverter
    fun toMessageKind(value: String): MessageKind = MessageKind.valueOf(value)

    @TypeConverter
    fun fromMessageStatus(value: MessageStatus): String = value.name

    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus = MessageStatus.valueOf(value)
}
