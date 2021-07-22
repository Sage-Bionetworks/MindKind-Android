package org.sagebionetworks.research.mindkind.viewmodel

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.*
import com.google.common.base.Preconditions
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.sagebionetworks.research.domain.result.AnswerResultType
import org.sagebionetworks.research.domain.result.implementations.AnswerResultBase
import org.sagebionetworks.research.domain.result.implementations.TaskResultBase
import org.sagebionetworks.research.mindkind.*
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService.Companion.studyDurationInWeeks
import org.sagebionetworks.research.mindkind.backgrounddata.ProgressInStudy
import org.sagebionetworks.research.mindkind.conversation.ConversationSurveyActivity
import org.sagebionetworks.research.mindkind.research.SageTaskIdentifier
import org.sagebionetworks.research.mindkind.researchstack.framework.SageResearchStack
import org.sagebionetworks.research.mindkind.room.BackgroundDataTypeConverters
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.extensions.*
import org.sagebionetworks.research.sageresearch.viewmodel.ReportViewModel
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.temporal.ChronoUnit
import java.util.*
import javax.inject.Inject

open class TaskListViewModel(
        private val appConfigRepo: AppConfigRepository,
        reportRepo: ReportRepository) : ReportViewModel(reportRepo = reportRepo) {

    companion object {
        private val TAG = TaskListViewModel::class.java.simpleName

        public const val studyStartDateKey = "LocalStudyStartDate"
        public const val roiAlertBaseKey = "roiAlertBaseKey"

        fun cachedProgressInStudy(sharedPreferences: SharedPreferences): ProgressInStudy? {
            sharedPreferences.getString(studyStartDateKey, null)?.let {
                val studyStartDate = LocalDateTime.parse(it)
                val now = LocalDateTime.now()
                return calculateStudyProgress(studyStartDate, now)
            }
            return null
        }

        fun calculateStudyProgress(start: LocalDateTime, now: LocalDateTime): ProgressInStudy? {
            if (now.isBefore(start)) {  // Study starts day after the date
                return null
            }
            val daysFromStart = start.until(now, ChronoUnit.DAYS).toInt()
            val dayOfWeek = (daysFromStart % 7) + 1
            val weekInStudy = (daysFromStart / 7) + 1
            return ProgressInStudy(weekInStudy, dayOfWeek, daysFromStart, start)
        }

        // We need the changes to be reflected immediately
        fun markConversationComplete(sharedPreferences: SharedPreferences) {
            sharedPreferences.edit().putString(
                    ConversationSurveyActivity.completedDateKey,
                    LocalDateTime.now().toString()).apply()
        }

        public const val moodDailyAnswerKey = "Mood_Daily"

        public fun roiDailyId(aiIdentifier: String?): String {
            return when (aiIdentifier) {
                MindKindApplication.SLEEP_AI -> "1S_Daily_Rested"
                MindKindApplication.SOCIAL_AI -> "2C_Daily_Y"
                MindKindApplication.BODY_MOVEMENT_AI -> "3M_Daily_Y"
                else /* MindKindApplication.POSITIVE_EXPERIENCES_AI */ -> "4P_Daily_Y"
            }
        }

        private fun shouldCountRoi(answer: Int, aiIdentifier: String?): Boolean {
            return when (aiIdentifier) {
                MindKindApplication.SLEEP_AI -> listOf(2, 3, 4)
                MindKindApplication.SOCIAL_AI -> listOf(1)
                MindKindApplication.BODY_MOVEMENT_AI -> listOf(1)
                else /* MindKindApplication.POSITIVE_EXPERIENCES_AI */ -> listOf(1)
            }.contains(answer)
        }

        private fun createRoiState(aiIdentifier: String?): RoiDialogState? {
            val aiIdUnwrapped = aiIdentifier ?: run { return null }
            return when (aiIdUnwrapped) {
                MindKindApplication.SLEEP_AI -> RoiDialogState(
                        R.string.roi_sleep_ai_text1,
                        R.string.roi_sleep_ai_text2,
                        R.string.roi_sleep_ai_text3)
                MindKindApplication.SOCIAL_AI -> RoiDialogState(
                        R.string.roi_social_ai_text1,
                        R.string.roi_social_ai_text2,
                        R.string.roi_social_ai_text3)
                MindKindApplication.POSITIVE_EXPERIENCES_AI -> RoiDialogState(
                        R.string.roi_experiences_ai_text1,
                        R.string.roi_experiences_ai_text2,
                        R.string.roi_experiences_ai_text3)
                else -> RoiDialogState( /* MindKindApplication.BODY_MOVEMENT_AI */
                        R.string.roi_movements_ai_text1,
                        R.string.roi_movements_ai_text2,
                        R.string.roi_movements_ai_text3)
            }
        }

        private fun localDate(report: ReportEntity?): LocalDateTime? {
            val dateStr = ((report?.data?.data as? Map<*, *>)?.get(
                    MindKindApplication.REPORT_LOCAL_DATE_TIME) as? String) ?: run {
                return null
            }
            return LocalDateTime.parse(dateStr)
        }

        public fun consolidateAiValues(now: LocalDateTime,
                                       baselineEntities: List<ReportEntity>,
                                       aiReports: List<ReportEntity>): AiSelectionState {

            // Default ai selection is all nulls and false to prompt user
            var aiSelection = AiSelectionState(null, null, null, null, false)

            val aiStartDate = startDateTime(baselineEntities) ?: run {
                return aiSelection // Cannot find start date time, they haven't completed baseline yet
            }

            // Time helper vars
            val progressInStudy = calculateStudyProgress(aiStartDate, now)
            val weekInStudy = progressInStudy?.week ?: 1
            // Attach progress in study context
            aiSelection = aiSelection.copy(progressInStudy = progressInStudy)

            // This checks for week 1 needing to be collected
            if (aiReports.isEmpty()) {
                aiSelection = aiSelection.copy(shouldPromptUserForAi =
                now.inSameDayAs(aiStartDate) || now.isAfter(aiStartDate))
                return aiSelection // User has done their baseline and might need to assign their week 1 AI
            }

            // Populate the AI selections base on report dateTime's
            aiReports.sortedBy { it.dateTime }.map {
                val ai = ((it.data?.data as? Map<*, *>)?.get(MindKindApplication.CURRENT_AI_RESULT_ID) as? String)
                val aiLocalTime = localDate(it) ?: now
                AiClientData(it.identifier ?: "", ai, aiLocalTime)
            }.forEach {
                val aiWeek = aiStartDate.until(it.date, ChronoUnit.WEEKS) + 1
                aiSelection = when {
                    aiWeek <= 4 -> aiSelection.copy(week1Ai = it.ai)
                    aiWeek <= 8 -> aiSelection.copy(week5Ai = it.ai)
                    else /* >= 12 */ -> aiSelection.copy(week9Ai = it.ai)
                }
            }

            return when {
                weekInStudy <= 4 -> aiSelection.copy(
                        currentAi = aiSelection.week1Ai,
                        shouldPromptUserForAi = aiSelection.week1Ai == null)
                weekInStudy <= 8 -> aiSelection.copy(
                        currentAi = aiSelection.week5Ai,
                        shouldPromptUserForAi = aiSelection.week5Ai == null)
                else /* >= 12 */ -> aiSelection.copy(
                        currentAi = aiSelection.week9Ai,
                        shouldPromptUserForAi = aiSelection.week9Ai == null)
            }
        }

        fun consolidateTaskItems(aiState: AiSelectionState,
                                 baselineEntities: List<ReportEntity>,
                                 completedAiToday: List<ReportEntity>): List<TaskListItem> {
            return orderedTaskItemList().filter { item ->
                if (item.identifier.startsWith(SageTaskIdentifier.Baseline)) {
                    return@filter baselineEntities.firstOrNull {
                        dataTypeFromReport(it) == item.identifier
                    } == null
                }
                return@filter completedAiToday.isEmpty() && aiState.currentAi == item.identifier
            }
        }

        public fun consolidateReturnOfInformation(now: LocalDateTime,
                                           roiAlertStatus: List<Boolean>, aiState: AiSelectionState,
                                           completeAiPast2Weeks: List<ReportEntity>): RoiDialogState? {

            val progress = aiState.progressInStudy ?: run {
                return null
            }

            val lastDayOfRoiWeek = now.minusDays(progress.dayOfWeek.toLong())
            val startOfRoiWeekInstant = lastDayOfRoiWeek.minusDays(7).startOfDay()
            val endOfRoiWeekInstant = lastDayOfRoiWeek.endOfDay()

            // Only show summaries in week 2 or later
            val weekOfStudy = progress.week
            val weekIdx = progress.week - 2
            if (weekIdx < 0 || weekIdx >= roiAlertStatus.size) {
                return null
            }

            val lastWeekAi = when(weekOfStudy) {
                in 1..5 -> aiState.week1Ai
                in 6..9 -> aiState.week5Ai
                else -> aiState.week9Ai
            }
            val state = createRoiState(lastWeekAi)
            val aiDailyId = roiDailyId(lastWeekAi)

            val roiDailies = completeAiPast2Weeks.filter {
                val local = localDate(it) ?: now
                return@filter local.isBetweenInclusive(startOfRoiWeekInstant, endOfRoiWeekInstant)
            }

            var moodDailyCount = 0
            var aiSpecificCount = 0

            var moodTargetCount = 0
            var aiTargetCount = 0

            roiDailies.forEach { report ->
                val answerMap = report.data?.data as? Map<*, *> ?: run { return@forEach }
                (answerMap[moodDailyAnswerKey] as? Double)?.toInt()?.let {
                    moodDailyCount += 1
                    if (listOf(3, 4, 5).contains(it)) {
                        moodTargetCount += 1
                    }
                }
                (answerMap[aiDailyId] as? Double)?.toInt()?.let {
                    aiSpecificCount += 1
                    if (shouldCountRoi(it, lastWeekAi)) {
                        aiTargetCount += 1
                    }
                }
            }

            val enoughDataToShow = (moodDailyCount >= 3 || aiSpecificCount >= 3)

            return state?.copy(
                    countMoodDaily = moodTargetCount, countAiDaily = aiTargetCount,
                    enoughDataToShow = enoughDataToShow,
                    shouldShowAlert = !roiAlertStatus[weekIdx])
        }

        fun startDateTime(baselineReports: List<ReportEntity>): LocalDateTime? {
            if (baselineReports.isEmpty()) {
                // User needs to have done their baseline to set their AI
                return null
            }

            val oldestBaselineReport = baselineReports.filter {
                return@filter dataTypeFromReport(it) == SageTaskIdentifier.Baseline
            }.sortedBy { it.dateTime }.firstOrNull()

            val localDateTime = localDate(oldestBaselineReport) ?: run {
                return null // invalid client data
            }

            return localDateTime.startOfNextDay()
        }

        fun dataTypeFromReport(report: ReportEntity): String? {
            return ((report.data?.data as? Map<*, *>)?.get(MindKindApplication.RESULT_DATA_TYPE) as? String)
        }

        fun orderedTaskItemList(): List<TaskListItem> {
            return listOf(
                    TaskListItem(SageTaskIdentifier.Baseline,
                            R.string.about_you_title,
                            R.string.three_minutes,
                            R.string.about_you_detail,
                            "Baseline"),
                    TaskListItem("Baseline_EnvironmentAll",
                            R.string.env_title,
                            R.string.three_minutes,
                            R.string.env_detail,
                            "Baseline_Environment"),
                    TaskListItem("Baseline_HabitsAll",
                            R.string.habits_title,
                            R.string.three_minutes,
                            R.string.habits_detail,
                            "Baseline_Habits"),
                    TaskListItem("Baseline_HealthAll",
                            R.string.health_title,
                            R.string.six_minutes,
                            R.string.health_detail,
                            "Baseline_Health"),
                    TaskListItem(MindKindApplication.SLEEP_AI,
                            R.string.sleep_title,
                            R.string.three_to_seven_minutes,
                            R.string.sleep_detail,
                            "Sleep"),
                    TaskListItem(MindKindApplication.POSITIVE_EXPERIENCES_AI,
                            R.string.exp_title,
                            R.string.three_to_seven_minutes,
                            R.string.exp_detail,
                            "PositiveExperiences"),
                    TaskListItem(MindKindApplication.BODY_MOVEMENT_AI,
                            R.string.movements_title,
                            R.string.one_to_five_minutes,
                            R.string.movements_detail,
                            "BodyMovement"),
                    TaskListItem(MindKindApplication.SOCIAL_AI,
                            R.string.social_title,
                            R.string.three_to_seven_minutes,
                            R.string.social_detail,
                            "Social"))
        }
    }

    class Factory @Inject constructor(
            private val appConfigRepo: AppConfigRepository,
            private val reportRepo: ReportRepository) :
            ViewModelProvider.Factory {

        // Suppress unchecked cast, pre-condition would catch it first anyways
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            Preconditions.checkArgument(modelClass.isAssignableFrom(TaskListViewModel::class.java))
            return TaskListViewModel(appConfigRepo, reportRepo) as T
        }
    }

    private var roiAlertStatus: List<Boolean> = listOf()
    private var lastAiReportCount = -1
    private var aiReportsLiveData: LiveData<List<ReportEntity>>? = null
    private var lastBaselineReportCount = -1
    private var baselineReportsLiveData: LiveData<List<ReportEntity>>? = null
    private var lastCompletedAiReportCount = -1
    private var completedAiTodayLiveData: LiveData<List<ReportEntity>>? = null
    private var lastCompletedAiLastWeekReportCount = -1
    private var completedAiLastWeekLiveData: LiveData<List<ReportEntity>>? = null

    private val recruitmentJsonLiveData: MutableLiveData<RecruitmentAppConfig?> = MutableLiveData()

    private val appConfigDisposable = CompositeDisposable()

    fun clearCache() {
        lastAiReportCount = -1
        lastBaselineReportCount = -1
        lastCompletedAiReportCount = -1
        lastCompletedAiLastWeekReportCount = -1
    }

    fun recruitmentJsonLiveData(): LiveData<RecruitmentAppConfig?> {
        val dataGroups = SageResearchStack.SageDataProvider.getInstance().userDataGroups
        appConfigDisposable.add(appConfigRepo.appConfig.firstOrError()
                .subscribeOn(Schedulers.io())
                .subscribe({ appConfig ->
                    Log.i(TAG, "App config updated successfully")
                    ((appConfig.clientData as? Map<*, *>)
                            ?.get("recruitment") as? Map<*, *>)?.let { recruitment ->

                        (recruitment[dataGroups.firstOrNull {
                            return@firstOrNull recruitment[it] != null
                        }] as? Map<*, *>)?.let {
                            val gson = BackgroundDataTypeConverters().gson
                            val json = gson.toJson(it)
                            val data = gson.fromJson(json, RecruitmentAppConfig::class.java)
                            recruitmentJsonLiveData.postValue(data)
                        }
                    }
                }, {
                    Log.w(TAG, "App config updated failed ${it.localizedMessage}")
                }))

        return recruitmentJsonLiveData
    }

    fun studyProgressLiveData(): LiveData<ProgressInStudy?> {
        val now = LocalDateTime.now()
        return Transformations.map(fullStudyReportsLiveData(SageTaskIdentifier.Baseline)) { baseline ->
            startDateTime(baseline)?.let {
                return@map calculateStudyProgress(it, now)
            }
            return@map null
        }
    }

    private fun fullStudyReportsLiveData(identifier: String): LiveData<List<ReportEntity>> {
        val endDate = LocalDateTime.now().startOfNextDay()
        val studyStartDate = endDate.minusDays(
                (BackgroundDataService.studyDurationInWeeks + 1) * 7.toLong())
        return reportsLiveData(identifier, studyStartDate, endDate)
    }

    private fun todayStudyReportsLiveData(identifier: String): LiveData<List<ReportEntity>> {
        val startDate = LocalDateTime.now().startOfDay()
        val endDate = LocalDateTime.now().endOfDay()
        return reportsLiveData(identifier, startDate, endDate)
    }

    private fun last2WeeksLiveData(identifier: String): LiveData<List<ReportEntity>> {
        val startDate = LocalDateTime.now().startOfDay().minusDays(14)
        val endDate = LocalDateTime.now().startOfDay()
        return reportsLiveData(identifier, startDate, endDate)
    }

    @SuppressLint("ApplySharedPref")
    fun saveDidShowRoiAlert(sharedPrefs: SharedPreferences, weekIdx: Int) {
        sharedPrefs.edit().putBoolean("$roiAlertBaseKey$weekIdx", true).commit()
        refreshRoiAlertStatus(sharedPrefs)
    }

    private fun refreshRoiAlertStatus(sharedPrefs: SharedPreferences) {
        roiAlertStatus = (2 until studyDurationInWeeks).map {
            return@map sharedPrefs.getBoolean("$roiAlertBaseKey$it", false)
        }
    }

    @SuppressLint("ApplySharedPref")
    public fun saveAiState(sharedPrefs: SharedPreferences, aiSelection: AiSelectionState?) {
        aiSelection?.week1Ai?.let {
            if (!sharedPrefs.contains(TaskListActivity.prefsWeek1AlertKey)) {
                sharedPrefs.edit().putString(TaskListActivity.prefsWeek1AlertKey, it).commit()
            }
        }
        aiSelection?.week5Ai?.let {
            if (!sharedPrefs.contains(TaskListActivity.prefsWeek5AlertKey)) {
                sharedPrefs.edit().putString(TaskListActivity.prefsWeek5AlertKey, it).commit()
            }
        }
        aiSelection?.week9Ai?.let {
            if (!sharedPrefs.contains(TaskListActivity.prefsWeek9AlertKey)) {
                sharedPrefs.edit().putString(TaskListActivity.prefsWeek9AlertKey, it).commit()
            }
        }
    }

    fun taskListLiveData(sharedPrefs: SharedPreferences): LiveData<TaskListState> {
        val mediator = MediatorLiveData<TaskListState>()
        refreshRoiAlertStatus(sharedPrefs)

        // Consolidation first-class fun to be invoked below
        val consolidationFun: (() -> Unit) = {
            val baselines = baselineReportsLiveData?.value
            val aiReports = aiReportsLiveData?.value
            val completedAi = completedAiTodayLiveData?.value
            val completedAiLastWeek = completedAiLastWeekLiveData?.value

            // Edge case bug fix for when the ReportRepo clears out the db
            // To Re-write reports that come down from the web
            // The live data gets a call back that there are now zero ai reports
            // which temporarily causes issues, so check to see if the ai state is stale
            val newBaselineCount = baselines?.size ?: -1
            val newAiReportsCount = aiReports?.size ?: -1
            val newCompletedAiCount = completedAi?.size ?: -1
            val newCompletedAiLastWeekCount = completedAiLastWeek?.size ?: -1

            // Check if the mediator live data is ready, and that it is not
            // a transition state where it has less reports than we saw previously
            if (baselines != null && aiReports != null &&
                    completedAi != null && completedAiLastWeek != null &&
                    (newBaselineCount > lastBaselineReportCount ||
                            newAiReportsCount > lastAiReportCount ||
                            newCompletedAiCount > lastCompletedAiReportCount ||
                            newCompletedAiLastWeekCount > lastCompletedAiLastWeekReportCount)) {

                Log.d(TAG, "baselines ${baselines.size} aireports ${aiReports.size} completedAi ${completedAi.size}")
                lastBaselineReportCount = newBaselineCount
                lastAiReportCount = newAiReportsCount
                lastCompletedAiReportCount = newCompletedAiCount
                lastCompletedAiLastWeekReportCount = newCompletedAiLastWeekCount

                val aiState = consolidateAiValues(LocalDateTime.now(), baselines, aiReports)
                val taskItems = consolidateTaskItems(aiState, baselines, completedAi)
                val returnOfInfo = consolidateReturnOfInformation(LocalDateTime.now(),
                        roiAlertStatus, aiState, completedAiLastWeek)
                val state = TaskListState(aiState, taskItems, returnOfInfo)

                mediator.postValue(state)
            }
        }

        val aiSelectionReports = fullStudyReportsLiveData(SageTaskIdentifier.AI)
        aiReportsLiveData = aiSelectionReports
        mediator.addSource(aiSelectionReports) { consolidationFun.invoke() }

        val baselineSelectionReports = fullStudyReportsLiveData(SageTaskIdentifier.Baseline)
        baselineReportsLiveData = baselineSelectionReports
        mediator.addSource(baselineSelectionReports) { consolidationFun.invoke() }

        val aiCompletedReports = todayStudyReportsLiveData(SageTaskIdentifier.Surveys)
        completedAiTodayLiveData = aiCompletedReports
        mediator.addSource(aiCompletedReports) { consolidationFun.invoke() }

        val aiCompletedLastWeekReports = last2WeeksLiveData(SageTaskIdentifier.Surveys)
        completedAiLastWeekLiveData = aiCompletedLastWeekReports
        mediator.addSource(aiCompletedLastWeekReports) { consolidationFun.invoke() }

        return mediator
    }

    fun saveAiAnswer(answer: String) {
        var aiTaskResult = TaskResultBase(SageTaskIdentifier.AI, UUID.randomUUID())

        val aiResult = AnswerResultBase<String>(
                MindKindApplication.CURRENT_AI_RESULT_ID, Instant.now(), Instant.now(),
                answer, AnswerResultType.STRING)
        aiTaskResult = aiTaskResult.addStepHistory(aiResult)

        val aiTimeResult = AnswerResultBase<String>(
                MindKindApplication.REPORT_LOCAL_DATE_TIME, Instant.now(), Instant.now(),
                LocalDateTime.now().toString(), AnswerResultType.STRING)
        aiTaskResult = aiTaskResult.addStepHistory(aiTimeResult)

        reportRepo.saveReports(aiTaskResult)
    }
}

