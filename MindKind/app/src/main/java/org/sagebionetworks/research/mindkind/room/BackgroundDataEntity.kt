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

package org.sagebionetworks.research.mindkind.room

import androidx.annotation.VisibleForTesting
import androidx.room.*
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import org.threeten.bp.LocalDateTime

/**
 * The BackgroundDataEntity contains a piece of data that was collected from the user
 * during the execution of the BackgroundDataService.
 */
@Entity
data class BackgroundDataEntity(
        /**
         * @property primaryKey
         */
        @PrimaryKey(autoGenerate = true) var primaryKey: Int = 0,  // 0 signals to room to auto-generate the id
        /**
         * @property date when the background data was collected
         */
        @Expose
        @ColumnInfo(index = true)
        var date: LocalDateTime? = null,
        /**
         * @property type of the background data
         */
        @ColumnInfo(index = true)
        var dataType: String? = null,
        /**
         * @property uploaded if this background data has been packaged for upload already
         */
        @ColumnInfo(index = true)
        var uploaded: Boolean = false,
        /**
         * @property data free-form string blob data associated with the entity
         */
        @Expose
        @SerializedName("data")
        var data: String? = null)

@Dao
interface BackgroundDataEntityDao {

        /**
         * This may take a long time and use a lot of memory if the table is large, call with caution
         * @return all the reports in the table
         */
        @VisibleForTesting
        @Query(RoomSqlHelper.BACKGROUND_DATA_QUERY_ALL)
        fun all(): List<BackgroundDataEntity>

        @VisibleForTesting
        @Query(RoomSqlHelper.BACKGROUND_DATA_QUERY_ALL_SORTED)
        fun getAllSorted(): List<BackgroundDataEntity>

        /**
         * @param BackgroundDataEntityList to insert into the database
         */
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun upsert(BackgroundDataEntityList: List<BackgroundDataEntity>)

        /**
         * @return All the background data that has not been uploaded yet
         */
        @Query(RoomSqlHelper.BACKGROUND_DATA_QUERY_IS_UPLOADED)
        fun getData(isUploaded: Boolean): List<BackgroundDataEntity>

        /**
         * Deletes all rows in the table.  To be called on sign out or a cache clear.
         */
        @Query(RoomSqlHelper.BACKGROUND_DATA_DELETE)
        fun clear()
}

/**
 * Helper class for the DataUsage data fields
 */
data class DataUsageStats(
        val totalRx: Long,
        val totalTx: Long,
        val mobileRx: Long,
        val mobileTx: Long)