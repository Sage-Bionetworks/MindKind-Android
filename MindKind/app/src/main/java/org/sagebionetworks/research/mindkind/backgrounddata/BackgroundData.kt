
/*
 * BSD 3-Clause License
 *
 * Copyright 2021  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sagebionetworks.research.mindkind.backgrounddata

import androidx.room.TypeConverter
import com.google.gson.GsonBuilder
import org.joda.time.DateTime
import org.sagebionetworks.bridge.rest.gson.ByteArrayToBase64TypeAdapter
import org.sagebionetworks.bridge.rest.gson.DateTimeTypeAdapter
import org.sagebionetworks.bridge.rest.gson.LocalDateTypeAdapter
import org.sagebionetworks.research.sageresearch.dao.room.*
import org.threeten.bp.*

open class BackgroundDataTypeConverters {

    companion object {
        private const val TAG = "BackgroundDataTypeConverters"
    }

    val gsonBuilder = GsonBuilder()
            .registerTypeAdapter(ByteArray::class.java, ByteArrayToBase64TypeAdapter())
            .registerTypeAdapter(org.joda.time.LocalDate::class.java, LocalDateTypeAdapter())
            .registerTypeAdapter(DateTime::class.java, DateTimeTypeAdapter())
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
            .registerTypeAdapter(ZonedDateTime::class.java, ZonedDateTimeAdapter())
            .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
            .registerTypeAdapter(Instant::class.java, InstantAdapter())

    val gson = gsonBuilder.create()

    @TypeConverter
    fun toBackgroundDataType(value: String) = enumValueOf<BackgroundDataType>(value)

    @TypeConverter
    fun fromBackgroundDataType(value: BackgroundDataType) = value.name

    @TypeConverter
    fun fromLocalDateString(value: String?): LocalDate? {
        val valueChecked = value ?: return null
        return LocalDate.parse(valueChecked)
    }

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        val valueChecked = value ?: return null
        return valueChecked.toString()
    }

    @TypeConverter
    fun fromLocalDateTimeString(value: String?): LocalDateTime? {
        val valueChecked = value ?: return null
        return LocalDateTime.parse(valueChecked)
    }

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? {
        val valueChecked = value ?: return null
        return valueChecked.toString()
    }

    @TypeConverter
    fun fromInstant(value: Long?): Instant? {
        val valueChecked = value ?: return null
        return Instant.ofEpochMilli(valueChecked)
    }

    @TypeConverter
    fun fromInstantTimestamp(value: Instant?): Long? {
        val valueChecked = value ?: return null
        return valueChecked.toEpochMilli()
    }

    @TypeConverter
    fun fromLocalTime(value: Long?): LocalTime? {
        val valueChecked = value ?: return null
        return LocalTime.ofNanoOfDay(valueChecked)
    }

    @TypeConverter
    fun fromLocalTimeStamp(value: LocalTime?): Long? {
        val valueChecked = value ?: return null
        return valueChecked.toNanoOfDay()
    }
}