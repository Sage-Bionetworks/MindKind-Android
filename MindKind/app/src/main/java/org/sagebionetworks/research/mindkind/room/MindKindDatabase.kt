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

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataTypeConverters
import org.sagebionetworks.research.sageresearch.dao.room.*

/**
 * To keep track of database changes, use this comment group as a change log
 * Version 1 - BackgroundDataEntity table created and added
 */
@Database(entities = [
    BackgroundDataEntity::class],
        version = 1)

@TypeConverters(BackgroundDataTypeConverters::class)
abstract class MindKindDatabase : RoomDatabase() {

    // Room will automatically setup up this DAO
    abstract fun backgroundDataDao(): BackgroundDataEntityDao

    companion object {
        /**
         * @param tableName to add the index to
         * @param fieldName to add the index to
         * @return the SQL command to add an index to a field name in a table
         */
        private fun migrationAddIndex(tableName: String, fieldName: String): String {
            return "CREATE INDEX index_${tableName}_$fieldName ON $tableName ($fieldName)"
        }
    }

   /**
    * We will probably need to migrate version at some point,
    * If we do, here are some examples:
    *
    *   val migrations: Array<Migration> get() = arrayOf(
    *       object : Migration(1, 2) {
    *           override fun migrate(database: SupportSQLiteDatabase) {
    *               val reportTable = "ReportEntity"
    *               // Create the ReportEntity table
    *               // This can be copy/pasted from "2.json" or whatever version database was created
    *               database.execSQL("CREATE TABLE `$reportTable` " +
    *                       "(`primaryKey` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
    *                       "`identifier` TEXT, " +
    *                       "`data` TEXT, " +
    *                       "`dateTime` INTEGER, " +
    *                       "`localDate` TEXT, " +
    *                       "`needsSyncedToBridge` INTEGER)")
    *               // If indexes are specified for a field name, they need to be added separately
    *               database.execSQL(migrationAddIndex(reportTable, "identifier"))
    *               database.execSQL(migrationAddIndex(reportTable, "dateTime"))
    *               database.execSQL(migrationAddIndex(reportTable, "localDate"))
    *               database.execSQL(migrationAddIndex(reportTable, "needsSyncedToBridge"))
    *           }
    *       },
    *       object : Migration(2, 3) {
    *           override fun migrate(database: SupportSQLiteDatabase) {
    *               val reportTable = "ResourceEntity"
    *               database.execSQL("CREATE TABLE `ResourceEntity` (`identifier` TEXT NOT NULL, `type` TEXT NOT NULL, `resourceJson` TEXT, `lastUpdateTime` INTEGER NOT NULL, PRIMARY KEY(`identifier`, `type`))")
    *               database.execSQL("CREATE  INDEX `index_ResourceEntity_type` ON `ResourceEntity` (`type`)")
    *           }
    *       },
    *       object : Migration(3, 4) {
    *           override fun migrate(database: SupportSQLiteDatabase) {
    *               val tableName = "HistoryItemEntity"
    *               database.execSQL("CREATE TABLE IF NOT EXISTS `${tableName}` (`type` TEXT NOT NULL, `dataJson` TEXT NOT NULL, `reportId` TEXT NOT NULL, `dateBucket` TEXT NOT NULL, `dateTime` INTEGER NOT NULL, `time` INTEGER NOT NULL, PRIMARY KEY(`reportId`, `dateBucket`, `time`))")
    *           }
    *       })
    */
}

internal class RoomSqlHelper {
    /**
     * Because all Room queries need to be verified at compile time, we cannot build dynamic queries based on state.
     * This is where these constant string values come into play, as building blocks to form reliable Room queries.
     * These originally came about to model re-usable query components like iOS' NSPredicates and CoreData.
     */
    companion object RoomSqlConstants {

        /**
         * OP constants combing CONDITION constants
         */
        private const val OP_AND = " AND "
        private const val OP_OR = " OR "

        /**
         * DELETE constants delete tables
         */
        const val BACKGROUND_DATA_DELETE = "DELETE FROM backgrounddataentity"
        const val BACKGROUND_DATA_DELETE_WHERE = "DELETE FROM backgrounddataentity WHERE "

        /**
         * SELECT constants start off queries
         */
        private const val BACKGROUND_DATA_SELECT = "SELECT * FROM backgrounddataentity WHERE "

        /**
         * ORDER BY constants do sorting on queries
         */
        private const val ORDER_BY_DATE_OLDEST = " ORDER BY date ASC"

        /**
         * LIMIT constants restrict the number of db rows
         */
        private const val LIMIT_1 = " LIMIT 1"

        /**
         * CONDITION constants need to be joined by AND or OR in the select statement
         */
        private const val BACKGROUND_DATA_CONDITION_UPLOADED = "(uploaded = :isUploaded)"
        private const val BACKGROUND_DATA_CONDITION_TYPE = "(dataType = :dataType)"
        private const val BACKGROUND_DATA_CONDITION_DATE_BETWEEN = "(date BETWEEN :start AND :end)"

        const val BACKGROUND_DATA_CONDITION_BETWEEN_DATE_WITH_TYPE =
                BACKGROUND_DATA_CONDITION_TYPE + OP_AND +
                        BACKGROUND_DATA_CONDITION_DATE_BETWEEN

        /**
         * QUERY constants are full Room queries
         */
        const val BACKGROUND_DATA_QUERY_ALL = "SELECT * FROM backgrounddataentity"

        const val BACKGROUND_DATA_QUERY_ALL_SORTED =
                BACKGROUND_DATA_QUERY_ALL + ORDER_BY_DATE_OLDEST

        const val BACKGROUND_DATA_QUERY_IS_UPLOADED =
                BACKGROUND_DATA_SELECT + BACKGROUND_DATA_CONDITION_UPLOADED +
                        ORDER_BY_DATE_OLDEST
    }
}