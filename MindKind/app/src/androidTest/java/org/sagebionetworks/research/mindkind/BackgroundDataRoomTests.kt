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

package org.sagebionetworks.research.sageresearch.viewmodel

import android.content.Intent
import androidx.test.filters.MediumTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sagebionetworks.research.mindkind.RoomTestHelper
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.research.SageTaskIdentifier
import org.sagebionetworks.research.mindkind.room.BackgroundDataEntity
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId

@RunWith(AndroidJUnit4::class)
// ran into multi-dex issues moving this to a library project, leaving it here for now
@MediumTest
class BackgroundDataRoomTests: RoomTestHelper() {

    companion object {

        // Noon on 3/7/21
        private val march7thNoon = LocalDateTime.of(
                2021,
                3,
                7,
                12,
                0).atZone(ZoneId.systemDefault())

        // 1pm on 3/7/21
        private val march7th1PM = LocalDateTime.of(
                2021,
                3,
                7,
                13,
                0).atZone(ZoneId.systemDefault())

        // 2pm on 3/7/21
        private val march7th2PM = LocalDateTime.of(
                2021,
                3,
                7,
                14,
                0).atZone(ZoneId.systemDefault())

        private val userPresentData = BackgroundDataEntity(
                primaryKey = 0,
                date = march7thNoon,
                dataType = SageTaskIdentifier.ScreenTime,
                data = BackgroundDataService.screenTimeData(Intent.ACTION_USER_PRESENT),
                subType = "subTypeA",
                uploaded = false)

        private val screenOnData = BackgroundDataEntity(
                primaryKey = 0,
                date = march7th1PM,
                dataType = SageTaskIdentifier.ScreenTime,
                data = BackgroundDataService.screenTimeData(Intent.ACTION_SCREEN_ON),
                subType = "subTypeB",
                uploaded = false)

        private val screenOffData = BackgroundDataEntity(
                primaryKey = 0,
                date = march7th2PM,
                dataType = SageTaskIdentifier.ScreenTime,
                data = BackgroundDataService.screenTimeData(Intent.ACTION_SCREEN_OFF),
                subType = "subTypeC",
                uploaded = true)
    }

    @Before
    fun setupForEachTestWithEmptyDatabase() {
        backgroundDataDao.clear()
    }

    @Test
    fun test_clear() {
        backgroundDataDao.upsert(listOf(screenOnData, screenOffData))
        backgroundDataDao.clear()
        assertEquals(0, backgroundDataDao.all().size)
    }

    @Test
    fun test_insert1() {
        val expected = listOf(userPresentData)
        backgroundDataDao.upsert(expected)
        val actual = backgroundDataDao.all()
        assertEqualLists(expected, actual)
    }

    @Test
    fun test_insert3_sorted() {
        val expected = listOf(userPresentData, screenOnData, screenOffData)
        backgroundDataDao.upsert(expected.reversed()) // Add them in reverse, to "shuffle"
        val actual = backgroundDataDao.getAllSorted()
        assertEqualLists(expected, actual)
    }

    @Test
    fun test_needs_uploaded() {
        val expected = listOf(userPresentData, screenOnData)
        val items = listOf(userPresentData, screenOnData, screenOffData)
        backgroundDataDao.upsert(items.reversed()) // Add them in reverse, to "shuffle"
        val actual = backgroundDataDao.getData(false)
        assertEqualLists(expected, actual)
    }

    fun assertEqualLists(expected: List<BackgroundDataEntity>, actual: List<BackgroundDataEntity>) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            // Don't test primaryKey, it will be different
            assertEquals(expected[i].dataType, actual[i].dataType)
            assertEquals(expected[i].data, actual[i].data)
            assertEquals(expected[i].subType, actual[i].subType)
            assertEquals(expected[i].date, actual[i].date)
            assertEquals(expected[i].uploaded, actual[i].uploaded)
        }
    }
}