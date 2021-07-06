package org.sagebionetworks.research.mindkind

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import org.joda.time.DateTime
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sagebionetworks.research.mindkind.MindKindApplication.*
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.backgrounddata.ProgressInStudy
import org.sagebionetworks.research.mindkind.research.SageTaskIdentifier
import org.sagebionetworks.research.mindkind.room.BackgroundDataEntity
import org.sagebionetworks.research.sageresearch.dao.room.ClientData
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId

@RunWith(AndroidJUnit4::class)
// ran into multi-dex issues moving this to a library project, leaving it here for now
@MediumTest
class TaskListViewModelTests: RoomTestHelper() {

    companion object {

        private val week0Progress = ProgressInStudy(0, 0, 0)
        private val week1Progress = ProgressInStudy(1, 0, 0)


        // Noon on 3/7/21
        private val week1Start = LocalDateTime.of(
                2021,
                3,
                7,
                12,
                0)

        private val week4End = LocalDateTime.of(
                2021,
                4,
                4,
                0,
                0)

        // 1pm on 3/7/21
        private val march7th1PM = LocalDateTime.of(
                2021,
                3,
                7,
                13,
                0).atZone(ZoneId.systemDefault())

        // 1pm on 3/7/21
        private val march14th1PM = LocalDateTime.of(
                2021,
                3,
                7,
                13,
                0).atZone(ZoneId.systemDefault())

        private val may4th1AM = LocalDateTime.of(
                2021,
                4,
                4,
                1,
                0).atZone(ZoneId.systemDefault())

        private val week0BaselineReportNull = ReportEntity(
                identifier = SageTaskIdentifier.AI,
                dateTime = null,
                data = ClientData(mapOf(Pair(CURRENT_AI_RESULT_ID, SOCIAL_AI))))

        private val week0BaselineReport = ReportEntity(
                identifier = SageTaskIdentifier.AI,
                dateTime = march7th1PM.toInstant(),
                data = ClientData(mapOf(Pair(CURRENT_AI_RESULT_ID, SOCIAL_AI))))

        private val week1ReportSocial = ReportEntity(
                identifier = SageTaskIdentifier.AI,
                dateTime = march7th1PM.toInstant(),
                data = ClientData(mapOf(Pair(CURRENT_AI_RESULT_ID, SOCIAL_AI))))

        private val week4ReportMovements = ReportEntity(
                identifier = SageTaskIdentifier.AI,
                dateTime = march7th1PM.toInstant(),
                data = ClientData(mapOf(Pair(CURRENT_AI_RESULT_ID, BODY_MOVEMENT_AI))))

        private val week8ReportSleep = ReportEntity(
                identifier = SageTaskIdentifier.AI,
                dateTime = march7th1PM.toInstant(),
                data = ClientData(mapOf(Pair(CURRENT_AI_RESULT_ID, SLEEP_AI))))
    }

    @Test
    fun test_nulls() {
        val zone = ZoneId.systemDefault()
        val now = DateTime.parse(week1Start.toString())
        val progress = BackgroundDataService.progressInStudy(now, now)

        var aiSelectionState = TaskListViewModel.consolidateAiValues(
                week1Start.atZone(zone).toLocalDateTime(), zone,
                listOf(), null)
        assertNull(aiSelectionState)

        aiSelectionState = TaskListViewModel.consolidateAiValues(
                week1Start.atZone(zone).toLocalDateTime(), zone,
                listOf(), null)
        assertNull(aiSelectionState)

        aiSelectionState = TaskListViewModel.consolidateAiValues(
                week1Start.atZone(zone).toLocalDateTime(), zone,
                null, null)
        assertNull(aiSelectionState)

        aiSelectionState = TaskListViewModel.consolidateAiValues(
                week1Start.atZone(zone).toLocalDateTime(), zone,
                null, listOf(week0BaselineReportNull))
        assertNull(aiSelectionState)
    }

    @Test
    fun test_week0_no_baseline() {
        val zone = ZoneId.systemDefault()
        val now = DateTime.parse(week1Start.toString())
        val progress = BackgroundDataService.progressInStudy(now, now)
        val aiSelectionState = TaskListViewModel.consolidateAiValues(
                week1Start, zone,
                listOf(), listOf())

        assertNotNull(aiSelectionState)
        assertNull(aiSelectionState?.currentAi)
    }

    @Test
    fun test_week0_baseline() {
        val zone = ZoneId.systemDefault()
        val now = DateTime.parse(week1Start.toString())
        val progress = BackgroundDataService.progressInStudy(now, now)
        val aiSelectionState = TaskListViewModel.consolidateAiValues(
                week1Start.atZone(zone).toLocalDateTime(), zone,
                listOf(), listOf())

        assertNotNull(aiSelectionState)
        assertNull(aiSelectionState?.currentAi)
    }

}