package org.sagebionetworks.research.mindkind

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import junit.framework.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.sagebionetworks.research.mindkind.MindKindApplication.*
import org.sagebionetworks.research.mindkind.backgrounddata.ProgressInStudy
import org.sagebionetworks.research.mindkind.research.SageTaskIdentifier
import org.sagebionetworks.research.mindkind.viewmodel.TaskListViewModel
import org.sagebionetworks.research.mindkind.viewmodel.TaskListViewModel.Companion.moodDailyAnswerKey
import org.sagebionetworks.research.mindkind.viewmodel.TaskListViewModel.Companion.roiDailyId
import org.sagebionetworks.research.sageresearch.dao.room.ClientData
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset

@RunWith(AndroidJUnit4::class)
// ran into multi-dex issues moving this to a library project, leaving it here for now
@MediumTest
class TaskListViewModelTests: RoomTestHelper() {

    companion object {

        private val baselineFinished = LocalDateTime.of(
                2021,
                3,
                6,
                12,
                0)
        private val afterBaselineFinished = baselineFinished.withHour(20)

        private val week1Start = LocalDateTime.of(
                2021,
                3,
                7,
                0,
                0)

        private val week1Day1Noon = week1Start.withHour(12)

        private val week4End = LocalDateTime.of(
                2021,
                4,
                3,
                23,
                59)

        private val week5Start = LocalDateTime.of(
                2021,
                4,
                4,
                0,
                0)

        private val week5Day1Noon = week5Start.withHour(12)

        private val week8End = LocalDateTime.of(
                2021,
                5,
                1,
                23,
                59)

        private val week9Start = LocalDateTime.of(
                2021,
                5,
                2,
                0,
                0)

        private val week9Day1Noon = week9Start.withHour(12)

        private val week0BaselineReportNullClientData = ReportEntity(
                identifier = SageTaskIdentifier.Baseline,
                dateTime = baselineFinished.toInstant(ZoneOffset.UTC),
                data = ClientData(mapOf<String, String>()))

        private val week0BaselineReport = ReportEntity(
                identifier = SageTaskIdentifier.Baseline,
                dateTime = baselineFinished.toInstant(ZoneOffset.UTC),
                data = ClientData(mapOf(
                        Pair(RESULT_DATA_TYPE, "Baseline"),
                        Pair(REPORT_LOCAL_DATE_TIME, baselineFinished.toString()))))

        private val week1Day1ReportSocial = ReportEntity(
                identifier = SageTaskIdentifier.AI,
                dateTime = week1Day1Noon.toInstant(ZoneOffset.UTC),
                data = ClientData(mapOf(
                        Pair(CURRENT_AI_RESULT_ID, SOCIAL_AI),
                        Pair(REPORT_LOCAL_DATE_TIME, week1Day1Noon.toString()))))

        private val week5Day1ReportMovements = ReportEntity(
                identifier = SageTaskIdentifier.AI,
                dateTime = week5Day1Noon.toInstant(ZoneOffset.UTC),
                data = ClientData(mapOf(
                        Pair(CURRENT_AI_RESULT_ID, BODY_MOVEMENT_AI),
                        Pair(REPORT_LOCAL_DATE_TIME, week5Day1Noon.toString()))))

        private val week9ReportSleep = ReportEntity(
                identifier = SageTaskIdentifier.AI,
                dateTime = week9Day1Noon.toInstant(ZoneOffset.UTC),
                data = ClientData(mapOf(
                        Pair(CURRENT_AI_RESULT_ID, SLEEP_AI),
                        Pair(REPORT_LOCAL_DATE_TIME, week9Day1Noon.toString()))))

        private fun createMoodDaily(now: LocalDateTime, daysAgo: Int, answer: Int): ReportEntity {
            val dateTime = now.minusDays(daysAgo.toLong())
            return ReportEntity(identifier = SageTaskIdentifier.Surveys,
                    dateTime = dateTime.toInstant(ZoneOffset.UTC),
                    data = ClientData(mapOf(
                            Pair(moodDailyAnswerKey, answer),
                            Pair(REPORT_LOCAL_DATE_TIME, dateTime.toString()))))
        }

        private fun createSurveyDaily(now: LocalDateTime, daysAgo: Int,
                                      key: String, answer: Int): ReportEntity {
            val dateTime = now.minusDays(daysAgo.toLong())
            return ReportEntity(identifier = SageTaskIdentifier.Surveys,
                    dateTime = dateTime.toInstant(ZoneOffset.UTC),
                    data = ClientData(mapOf(
                            Pair(key, answer),
                            Pair(REPORT_LOCAL_DATE_TIME, dateTime.toString()))))
        }

        private fun createRoiAlertStatus(weekShown: Int): List<Boolean> {
            return (2 until 12).map { it == weekShown }
        }
    }

    @Test
    fun test_empty() {
        val aiSelectionState = TaskListViewModel.consolidateAiValues(week1Start,
                listOf(), listOf())
        assertNotNull(aiSelectionState)
        assertNull(aiSelectionState.week1Ai)
        assertNull(aiSelectionState.week5Ai)
        assertNull(aiSelectionState.week9Ai)
        assertNull(aiSelectionState.currentAi)
        assertFalse(aiSelectionState.shouldPromptUserForAi)
    }

    @Test
    fun test_week0_empty_clientdata() {
        val aiSelectionState = TaskListViewModel.consolidateAiValues(week1Start,
                listOf(week0BaselineReportNullClientData), listOf())
        assertNotNull(aiSelectionState)
        assertNull(aiSelectionState.week1Ai)
        assertNull(aiSelectionState.week5Ai)
        assertNull(aiSelectionState.week9Ai)
        assertNull(aiSelectionState.currentAi)
        assertFalse(aiSelectionState.shouldPromptUserForAi)
    }

    @Test
    fun test_week0_after_baseline() {
        val aiSelectionState = TaskListViewModel.consolidateAiValues(
                afterBaselineFinished, listOf(week0BaselineReport), listOf())

        assertNotNull(aiSelectionState)
        assertNull(aiSelectionState.week1Ai)
        assertNull(aiSelectionState.week5Ai)
        assertNull(aiSelectionState.week9Ai)
        assertNull(aiSelectionState.currentAi)
        assertFalse(aiSelectionState.shouldPromptUserForAi)
    }

    @Test
    fun test_week1_day1_needs_ai() {
        val aiSelectionState = TaskListViewModel.consolidateAiValues(
                week1Start, listOf(week0BaselineReport), listOf())

        assertNotNull(aiSelectionState)
        assertNull(aiSelectionState.week1Ai)
        assertNull(aiSelectionState.week5Ai)
        assertNull(aiSelectionState.week9Ai)
        assertNull(aiSelectionState.currentAi)
        assertTrue(aiSelectionState.shouldPromptUserForAi)
    }

    @Test
    fun test_week1_day1_ai_provided() {
        val aiSelectionState = TaskListViewModel.consolidateAiValues(
                week1Day1Noon, listOf(week0BaselineReport), listOf(week1Day1ReportSocial))

        assertNotNull(aiSelectionState)
        assertEquals(SOCIAL_AI, aiSelectionState.week1Ai)
        assertNull(aiSelectionState.week5Ai)
        assertNull(aiSelectionState.week9Ai)
        assertEquals(aiSelectionState.currentAi, SOCIAL_AI)
        assertFalse(aiSelectionState.shouldPromptUserForAi)
    }

    @Test
    fun test_week4_end_ai_provided() {
        val aiSelectionState = TaskListViewModel.consolidateAiValues(
                week4End, listOf(week0BaselineReport), listOf(week1Day1ReportSocial))

        assertNotNull(aiSelectionState)
        assertEquals(SOCIAL_AI, aiSelectionState.week1Ai)
        assertNull(aiSelectionState.week5Ai)
        assertNull(aiSelectionState.week9Ai)
        assertEquals(aiSelectionState.currentAi, SOCIAL_AI)
        assertFalse(aiSelectionState.shouldPromptUserForAi)
    }

    @Test
    fun test_week5_day1_no_ai() {
        val aiSelectionState = TaskListViewModel.consolidateAiValues(
                week5Day1Noon, listOf(week0BaselineReport), listOf(week1Day1ReportSocial))

        assertNotNull(aiSelectionState)
        assertEquals(SOCIAL_AI, aiSelectionState.week1Ai)
        assertNull(aiSelectionState.week5Ai)
        assertNull(aiSelectionState.week9Ai)
        assertNull(aiSelectionState.currentAi)  // no ai yet for week 5
        assertTrue(aiSelectionState.shouldPromptUserForAi)
    }

    @Test
    fun test_week5_day1_ai_provided() {
        val aiSelectionState = TaskListViewModel.consolidateAiValues(
                week5Day1Noon, listOf(week0BaselineReport),
                listOf(week1Day1ReportSocial, week5Day1ReportMovements))

        assertNotNull(aiSelectionState)
        assertEquals(SOCIAL_AI, aiSelectionState.week1Ai)
        assertEquals(BODY_MOVEMENT_AI, aiSelectionState.week5Ai)
        assertNull(aiSelectionState.week9Ai)
        assertEquals(aiSelectionState.currentAi, BODY_MOVEMENT_AI)
        assertFalse(aiSelectionState.shouldPromptUserForAi)
    }

    @Test
    fun test_week8_end_ai_provided() {
        val aiSelectionState = TaskListViewModel.consolidateAiValues(
                week8End, listOf(week0BaselineReport),
                listOf(week1Day1ReportSocial, week5Day1ReportMovements))

        assertNotNull(aiSelectionState)
        assertEquals(SOCIAL_AI, aiSelectionState.week1Ai)
        assertEquals(BODY_MOVEMENT_AI, aiSelectionState.week5Ai)
        assertNull(aiSelectionState.week9Ai)
        assertEquals(aiSelectionState.currentAi, BODY_MOVEMENT_AI)
        assertFalse(aiSelectionState.shouldPromptUserForAi)
    }

    @Test
    fun test_week9_day1_no_ai() {
        val aiSelectionState = TaskListViewModel.consolidateAiValues(
                week9Day1Noon, listOf(week0BaselineReport),
                listOf(week1Day1ReportSocial, week5Day1ReportMovements))

        assertNotNull(aiSelectionState)
        assertEquals(SOCIAL_AI, aiSelectionState.week1Ai)
        assertEquals(BODY_MOVEMENT_AI, aiSelectionState.week5Ai)
        assertNull(aiSelectionState.week9Ai)
        assertNull(aiSelectionState.currentAi)  // no ai yet for week 9
        assertTrue(aiSelectionState.shouldPromptUserForAi)
    }

    @Test
    fun test_week9_day1_ai_provided() {
        val aiSelectionState = TaskListViewModel.consolidateAiValues(
                week9Day1Noon, listOf(week0BaselineReport),
                listOf(week1Day1ReportSocial, week5Day1ReportMovements, week9ReportSleep))

        assertNotNull(aiSelectionState)
        assertEquals(SOCIAL_AI, aiSelectionState.week1Ai)
        assertEquals(BODY_MOVEMENT_AI, aiSelectionState.week5Ai)
        assertEquals(SLEEP_AI, aiSelectionState.week9Ai)
        assertEquals(aiSelectionState.currentAi, SLEEP_AI)
        assertFalse(aiSelectionState.shouldPromptUserForAi)
    }

    @Test
    fun test_roi_null_progress() {
        val alerts = listOf<Boolean>()
        val progress: ProgressInStudy? = null
        val aiState = AiSelectionState(null, null, null,
                null, false, progress)

        val roi = TaskListViewModel.consolidateReturnOfInformation(
                week9Day1Noon, alerts, aiState, listOf())

        assertNull(roi)
    }

    @Test
    fun test_roi_first_week() {
        val alerts = listOf<Boolean>()
        val progress = ProgressInStudy(1, 1, 1, week9Day1Noon)
        val aiState = AiSelectionState(SOCIAL_AI, null, null,
                SOCIAL_AI, false, progress)

        val roi = TaskListViewModel.consolidateReturnOfInformation(
                week9Day1Noon, alerts, aiState, listOf())

        assertNull(roi)
    }

    @Test
    fun test_roi_week2_not_enough() {
        val alerts = listOf<Boolean>()
        val progress = ProgressInStudy(2, 1, 1, week9Day1Noon)
        val aiState = AiSelectionState(SOCIAL_AI, null, null,
                SOCIAL_AI, false, progress)

        val roi = TaskListViewModel.consolidateReturnOfInformation(
                week9Day1Noon, alerts, aiState, listOf())

        assertNull(roi)
    }

    @Test
    fun test_roi_week2_not_enough2() {
        val ai = SOCIAL_AI
        val key = roiDailyId(ai)
        val alerts = createRoiAlertStatus(0)
        val progress = ProgressInStudy(2, 1, 1, week9Day1Noon)
        val aiSurveys = listOf(
                createMoodDaily(week9Day1Noon, 1, 3),
                createMoodDaily(week9Day1Noon, 1, 3),
                createSurveyDaily(week9Day1Noon, 1, key, 1),
                createSurveyDaily(week9Day1Noon, 1, key, 1))
        val aiState = AiSelectionState(ai, null, null,
                ai, false, progress)

        val roi = TaskListViewModel.consolidateReturnOfInformation(
                week9Day1Noon, alerts, aiState, aiSurveys)

        assertNotNull(roi)
        assertEquals(roi!!.countAiDaily, 2)
        assertEquals(roi!!.countMoodDaily, 2)
        assertFalse(roi!!.enoughDataToShow)
        assertTrue(roi!!.shouldShowAlert)
    }

    @Test
    fun test_roi_week2_not_enough3() {
        val ai = SOCIAL_AI
        val key = roiDailyId(ai)
        val alerts = createRoiAlertStatus(0)
        val progress = ProgressInStudy(2, 1, 1, week9Day1Noon)
        val aiSurveys = listOf(
                createMoodDaily(week9Day1Noon, 1, 3),
                createMoodDaily(week9Day1Noon, 1, 3),
                createMoodDaily(week9Day1Noon, 9, 3), // outside time window
                createMoodDaily(week9Day1Noon, 0, 3), // outside time window
                createMoodDaily(week9Day1Noon, 1, 1), // wrong answer
                createSurveyDaily(week9Day1Noon, 1, key, 1),
                createSurveyDaily(week9Day1Noon, 1, key, 1),
                createSurveyDaily(week9Day1Noon, 1, key, 1))
        val aiState = AiSelectionState(ai, null, null,
                ai, false, progress)

        val roi = TaskListViewModel.consolidateReturnOfInformation(
                week9Day1Noon, alerts, aiState, aiSurveys)

        assertNotNull(roi)
        assertEquals(roi!!.countAiDaily, 3)
        assertEquals(roi!!.countMoodDaily, 2)
        assertFalse(roi!!.enoughDataToShow)
        assertTrue(roi!!.shouldShowAlert)
    }

    @Test
    fun test_roi_week2_alert() {
        val ai = SOCIAL_AI
        val key = roiDailyId(ai)
        val alerts = createRoiAlertStatus(0)
        val progress = ProgressInStudy(2, 1, 1, week9Day1Noon)
        val aiSurveys = listOf(
                createMoodDaily(week9Day1Noon, 1, 3),
                createMoodDaily(week9Day1Noon, 1, 3),
                createMoodDaily(week9Day1Noon, 1, 3),
                createSurveyDaily(week9Day1Noon, 1, key, 1),
                createSurveyDaily(week9Day1Noon, 1, key, 1),
                createSurveyDaily(week9Day1Noon, 1, key, 1))
        val aiState = AiSelectionState(ai, null, null,
                ai, false, progress)

        val roi = TaskListViewModel.consolidateReturnOfInformation(
                week9Day1Noon, alerts, aiState, aiSurveys)

        assertNotNull(roi)
        assertEquals(roi!!.countAiDaily, 3)
        assertEquals(roi!!.countMoodDaily, 3)
        assertTrue(roi!!.enoughDataToShow)
        assertTrue(roi!!.shouldShowAlert)
    }

    @Test
    fun test_roi_week2_alert_already_showed() {
        val ai = SOCIAL_AI
        val key = roiDailyId(ai)
        val alerts = createRoiAlertStatus(2)
        val progress = ProgressInStudy(2, 1, 1, week9Day1Noon)
        val aiSurveys = listOf(
                createMoodDaily(week9Day1Noon, 1, 3),
                createMoodDaily(week9Day1Noon, 1, 3),
                createMoodDaily(week9Day1Noon, 1, 3),
                createSurveyDaily(week9Day1Noon, 1, key, 1),
                createSurveyDaily(week9Day1Noon, 1, key, 1),
                createSurveyDaily(week9Day1Noon, 1, key, 1))
        val aiState = AiSelectionState(ai, null, null,
                ai, false, progress)

        val roi = TaskListViewModel.consolidateReturnOfInformation(
                week9Day1Noon, alerts, aiState, aiSurveys)

        assertNotNull(roi)
        assertEquals(roi!!.countAiDaily, 3)
        assertEquals(roi!!.countMoodDaily, 3)
        assertTrue(roi!!.enoughDataToShow)
        assertFalse(roi!!.shouldShowAlert)
    }
}