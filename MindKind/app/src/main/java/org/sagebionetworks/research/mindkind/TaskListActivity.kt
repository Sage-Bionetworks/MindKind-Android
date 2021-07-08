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

package org.sagebionetworks.research.mindkind

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.base.Preconditions
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_task_list.*
import org.sagebionetworks.research.domain.result.AnswerResultType
import org.sagebionetworks.research.domain.result.implementations.AnswerResultBase
import org.sagebionetworks.research.domain.result.implementations.TaskResultBase
import org.sagebionetworks.research.mindkind.MindKindApplication.*
import org.sagebionetworks.research.mindkind.TaskListViewModel.Companion.studyStartDateKey
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService.Companion.SHOW_ENGAGEMENT_NOTIFICATION_ACTION
import org.sagebionetworks.research.mindkind.backgrounddata.ProgressInStudy
import org.sagebionetworks.research.mindkind.conversation.*
import org.sagebionetworks.research.mindkind.research.SageTaskIdentifier
import org.sagebionetworks.research.mindkind.researchstack.framework.SageResearchStack
import org.sagebionetworks.research.mindkind.settings.SettingsActivity
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.extensions.*
import org.sagebionetworks.research.sageresearch.viewmodel.ReportViewModel
import org.threeten.bp.*
import org.threeten.bp.temporal.ChronoUnit
import java.util.*
import javax.inject.Inject

/**
 * A simple [Fragment] subclass that shows a list of the available surveys and tasks for the app
 */
class TaskListActivity : AppCompatActivity(), OnRequestPermissionsResultCallback {
    companion object {
        private val TAG = TaskListActivity::class.simpleName
    }

    @Inject
    lateinit var reportRepo: ReportRepository

    @Inject
    lateinit var appConfigRepo: AppConfigRepository

    private lateinit var viewModel: TaskListViewModel
    private lateinit var sharedPrefs: SharedPreferences

    private var aiDialog: Dialog? = null

    private var observersAttachedAt: LocalDateTime? = null
    private var taskLiveData: LiveData<TaskListState>? = null
    private var studyProgressLiveData: LiveData<ProgressInStudy?>? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)

        sharedPrefs = BackgroundDataService.createSharedPrefs(this)

        val llm = LinearLayoutManager(this)
        llm.orientation = LinearLayoutManager.VERTICAL
        llm.supportsPredictiveItemAnimations()
        taskRecyclerView.layoutManager = llm

        taskRecyclerView.addItemDecoration(SpacesItemDecoration(resources.getDimensionPixelSize(R.dimen.converation_recycler_spacing)))
        val adapter = TaskAdapter(mutableListOf(), object : TaskAdapterListener {
            override fun onTaskClicked(jsonResourceName: String?) {
                startTask(jsonResourceName)
            }
        })

        taskRecyclerView.adapter = adapter

        buttonUploadData.visibility = View.GONE
        buttonUploadData.setOnClickListener {
            uploadBackgroundData()
        }

        buttonBackgroundData.visibility = View.GONE
        buttonBackgroundData.setOnClickListener {
            if (buttonBackgroundData.text == "Start background data") {
                startBackgroundDataService(buttonBackgroundData)
            } else {
                stopBackgroundDataService(buttonBackgroundData)
            }
        }

        gear.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.putExtra(SettingsActivity.extraSettingsId, "Settings")
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        refreshServiceButtonState()

        // Auto-start background data collector
        startBackgroundDataService()

        // Did we enter this screen from the engagement notification?
        if (intent.action == SHOW_ENGAGEMENT_NOTIFICATION_ACTION) {
            showEngagementMessage()
        }

        viewModel = ViewModelProvider(this, TaskListViewModel.Factory(
                appConfigRepo, reportRepo)).get()
    }

    @SuppressLint("ApplySharedPref")
    private fun refreshLiveData(adapter: TaskAdapter) {
        Log.d(TAG, "Refresh live data")

        taskLiveData?.removeObservers(this)
        studyProgressLiveData?.removeObservers(this)
        viewModel.clearCache()

        loading_progress.visibility = View.VISIBLE
        taskLiveData = viewModel.taskListLiveData()
        taskLiveData?.observe(this, Observer {
            Log.i(TAG, "At ${LocalDateTime.now()} AI state is ${it.aiState}")
            loading_progress.visibility = View.GONE
            if (it.aiState.shouldPromptUserForAi) {
                showAiDialog(it.aiState)
            }
            adapter.dataSet.clear()
            adapter.dataSet.addAll(it.taskListItems)
            adapter.notifyDataSetChanged()
        })

        studyProgressLiveData = viewModel.studyProgressLiveData()
        studyProgressLiveData?.removeObservers(this)
        studyProgressLiveData?.observe(this, Observer {
            // Progress in study is null until the day after they completed their baseline
            val progressInStudy = it ?: run {
                task_progress_container.visibility = View.GONE
                return@Observer
            }
            task_progress_container.visibility = View.VISIBLE
            task_progress_bar.max = BackgroundDataService.studyDurationInWeeks * 7
            task_progress_bar.progress = progressInStudy.daysFromStart
            val weekStr = getString(R.string.week_x, progressInStudy.week.toString())
            val dayStr = getString(R.string.day_x, progressInStudy.dayOfWeek.toString())
            week_textview.text = "$weekStr | $dayStr"

            // Cache the study start date if we haven't yet
            if (!sharedPrefs.contains(studyStartDateKey)) {
                val localStartStr = progressInStudy.localStart.toString()
                sharedPrefs.edit().putString(studyStartDateKey, localStartStr).commit()
            }
        })
    }

    override fun onResume() {
        super.onResume()

        val now = LocalDateTime.now()
        if (observersAttachedAt?.inSameDayAs(now) != true) {
            observersAttachedAt = now
            val adapter = taskRecyclerView.adapter as? TaskAdapter ?: run { return }
            refreshLiveData(adapter)
        }
    }

    @SuppressLint("SetTextI18n")
    fun refreshServiceButtonState() {
        if (BackgroundDataService.isServiceRunning) {
            buttonBackgroundData?.text = "Stop background data"
        } else {
            buttonBackgroundData?.text = "Start background data"
        }
    }

    fun uploadBackgroundData() {
        // Notifies the server that it should upload the background data to bridge
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(Intent(BackgroundDataService.ACTIVITY_UPLOAD_DATA_ACTION))
    }

    fun startBackgroundDataService(button: Button? = null) {
        @SuppressLint("SetTextI18n")
        button?.text = "Stop background data"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, BackgroundDataService::class.java))
        } else {
            startService(Intent(this, BackgroundDataService::class.java))
        }
    }

    fun stopBackgroundDataService(button: Button? = null) {
        @SuppressLint("SetTextI18n")
        button?.text = "Start background data"
        stopService(Intent(this, BackgroundDataService::class.java))
    }

    fun startTask(jsonResourceName: String?) {
        val fileName = jsonResourceName ?: run { return }
        ConversationSurveyActivity.start(this, jsonResourceName)
    }

    fun showEngagementMessage() {
        MaterialAlertDialogBuilder(this)
                .setMessage(R.string.engagement_alert_message)
                .setPositiveButton(R.string.rsb_BOOL_YES) { _, _ ->
                    // TODO: mdephillips 5/13/21 send them to withdrawal
                }
                .setNegativeButton(R.string.rsb_BOOL_NO, null)
                .show()
    }

    private fun showAiDialog(aiSelection: AiSelectionState) {

        if (aiDialog != null) {
            Log.d(TAG, "Dialog already showing, skip duplicate")
            return // already showing the dialog
        }
        Log.d(TAG, "Showing dialog with AI state $aiSelection")

        // Check if we should assign a random ai instead
        val dataGroups = SageResearchStack.SageDataProvider.getInstance().userDataGroups
        val isUserInARM2 = dataGroups.contains(DATAGROUP_ARM2)

        var aiText = ""
        var title: String? = null
        var text: String? = null

        val randomAiIndex = (Math.random() * 4).toInt()
        if (isUserInARM2) {
            aiText = MindKindApplication.aiForIndex(randomAiIndex)
        }

        if (isUserInARM2) {
            if (aiSelection.week1Ai == null) {
                title = getString(R.string.arm2_week_1_ai_title)
                text = getString(R.string.arm2_week_1_ai_text).format(aiText)
            } else {
                title = getString(R.string.arm2_other_weeks_ai_title)
                text = getString(R.string.arm2_other_weeks_ai_text).format(aiText)
            }
        } else { // ARM1
            if (aiSelection.week1Ai == null) {
                title = getString(R.string.arm1_week_1_ai_title)
                text = getString(R.string.arm1_week_1_ai_text)
            } else {
                title = getString(R.string.arm1_other_weeks_ai_title)
                text = getString(R.string.arm1_other_weeks_ai_text)
            }
        }

        val dialog = Dialog(this)

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)

        if (isUserInARM2) {
            dialog.setContentView(R.layout.dialog_ai_assignment)
            dialog.findViewById<MaterialButton>(R.id.close_button).setOnClickListener {
                aiSelected(randomAiIndex)
                dialog.dismiss()
                aiDialog = null
            }
        } else { // ARM1
            dialog.setContentView(R.layout.dialog_ai_selection)
            val buttons = listOf<MaterialButton>(
                    dialog.findViewById(R.id.ai_social),
                    dialog.findViewById(R.id.ai_sleep),
                    dialog.findViewById(R.id.ai_movements),
                    dialog.findViewById(R.id.ai_experiences))

            buttons.forEachIndexed { index, button ->
                button.setOnClickListener {
                    aiSelected(index)
                    dialog.dismiss()
                    aiDialog = null
                }
            }
        }

        dialog.findViewById<TextView>(R.id.dialog_title).text = title
        dialog.findViewById<TextView>(R.id.dialog_message).text = text

        dialog.show()

        aiDialog = dialog
    }

    private fun aiSelected(index: Int) {
        val ai = MindKindApplication.aiForIndex(index)
        viewModel.saveAiAnswer(ai)
    }
}

open class TaskListViewModel(
        private val appConfigRepo: AppConfigRepository,
        reportRepo: ReportRepository) : ReportViewModel(reportRepo = reportRepo) {

    companion object {
        private val TAG = TaskListViewModel::class.java.simpleName

        public const val studyStartDateKey = "LocalStudyStartDate"

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

        public fun consolidateAiValues(now: LocalDateTime,
                                       baselineEntities: List<ReportEntity>,
                                       aiReports: List<ReportEntity>): AiSelectionState {

            // Default ai selection is all nulls and false to prompt user
            var aiSelection = AiSelectionState(null, null, null, null, false)

            val aiStartDate = startDateTime(baselineEntities) ?: run {
                return aiSelection // Cannot find start date time, they haven't completed baseline yet
            }

            // This checks for week 1 needing to be collected
            if (aiReports.isEmpty()) {
                aiSelection = aiSelection.copy(shouldPromptUserForAi =
                    now.inSameDayAs(aiStartDate) || now.isAfter(aiStartDate))
                return aiSelection // User has done their baseline and might need to assign their week 1 AI
            }

            // Time helper vars
            val weekInStudy = aiStartDate.until(now, ChronoUnit.WEEKS) + 1

            // Populate the AI selections base on report dateTime's
            aiReports.sortedBy { it.dateTime }.map {
                val ai = ((it.data?.data as? Map<*, *>)?.get(CURRENT_AI_RESULT_ID) as? String)
                val aiLocalTimeStr = ((it.data?.data as? Map<*, *>)?.get(
                        REPORT_LOCAL_DATE_TIME) as? String) ?: LocalDateTime.now().toString()
                val aiLocalTime = LocalDateTime.parse(aiLocalTimeStr)
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

            return orderedTaskItemList()
            return orderedTaskItemList().filter { item ->
                if (item.identifier.startsWith(SageTaskIdentifier.Baseline)) {
                    return@filter baselineEntities.firstOrNull {
                        dataTypeFromReport(it) == item.identifier
                    } == null
                }
                return@filter completedAiToday.isEmpty() && aiState.currentAi == item.identifier
            }
        }

        fun startDateTime(baselineReports: List<ReportEntity>): LocalDateTime? {
            if (baselineReports.isEmpty()) {
                // User needs to have done their baseline to set their AI
                return null
            }

            val oldestBaselineReport = baselineReports.filter {
                return@filter dataTypeFromReport(it) == SageTaskIdentifier.Baseline
            }.sortedBy { it.dateTime }.firstOrNull()

            val baselineClientData = oldestBaselineReport?.data?.data as? Map<*, *> ?: run {
                return null // Invalid baseline data
            }

            val localDateTimeStr = baselineClientData[REPORT_LOCAL_DATE_TIME] as? String ?: run {
                return null // invalid client data
            }

            return LocalDateTime.parse(localDateTimeStr)?.startOfNextDay()
        }

        fun dataTypeFromReport(report: ReportEntity): String? {
            return ((report.data?.data as? Map<*, *>)?.get(RESULT_DATA_TYPE) as? String)
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
                            "Positive_Experiences"),
                    TaskListItem(MindKindApplication.BODY_MOVEMENT_AI,
                            R.string.movements_title,
                            R.string.three_to_seven_minutes,
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

    private var lastAiReportCount = -1
    private var aiReportsLiveData: LiveData<List<ReportEntity>>? = null
    private var lastBaselineReportCount = -1
    private var baselineReportsLiveData: LiveData<List<ReportEntity>>? = null
    private var lastCompletedAiReportCount = -1
    private var completedAiTodayLiveData: LiveData<List<ReportEntity>>? = null

    fun clearCache() {
        lastAiReportCount = -1
        lastBaselineReportCount = -1
        lastCompletedAiReportCount = -1
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

    fun taskListLiveData(): LiveData<TaskListState> {
        val mediator = MediatorLiveData<TaskListState>()

        // Consolidation first-class fun to be invoked below
        val consolidationFun: (() -> Unit) = {
            val baselines = baselineReportsLiveData?.value
            val aiReports = aiReportsLiveData?.value
            val completedAi = completedAiTodayLiveData?.value

            // Edge case bug fix for when the ReportRepo clears out the db
            // To Re-write reports that come down from the web
            // The live data gets a call back that there are now zero ai reports
            // which temporarily causes issues, so check to see if the ai state is stale
            val newBaselineCount = baselines?.size ?: -1
            val newAiReportsCount = aiReports?.size ?: -1
            val newCompletedAiCount = completedAi?.size ?: -1

            // Check if the mediator live data is ready, and that it is not
            // a transition state where it has less reports than we saw previously
            if (baselines != null && aiReports != null && completedAi != null &&
                    (newBaselineCount > lastBaselineReportCount ||
                     newAiReportsCount > lastAiReportCount ||
                     newCompletedAiCount > lastCompletedAiReportCount)) {

                Log.d(TAG, "baselines ${baselines.size} aireports ${aiReports.size} completedAi ${completedAi.size}")
                lastBaselineReportCount = newBaselineCount
                lastAiReportCount = newAiReportsCount
                lastCompletedAiReportCount = newCompletedAiCount

                val aiState = consolidateAiValues(LocalDateTime.now(), baselines, aiReports)
                val taskItems = consolidateTaskItems(aiState, baselines, completedAi)
                val state = TaskListState(aiState, taskItems)
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

        return mediator
    }

    fun saveAiAnswer(answer: String) {
        var aiTaskResult = TaskResultBase(SageTaskIdentifier.AI, UUID.randomUUID())

        val aiResult = AnswerResultBase<String>(
                CURRENT_AI_RESULT_ID, Instant.now(), Instant.now(),
                answer, AnswerResultType.STRING)
        aiTaskResult = aiTaskResult.addStepHistory(aiResult)

        val aiTimeResult = AnswerResultBase<String>(
                REPORT_LOCAL_DATE_TIME, Instant.now(), Instant.now(),
                LocalDateTime.now().toString(), AnswerResultType.STRING)
        aiTaskResult = aiTaskResult.addStepHistory(aiTimeResult)

        reportRepo.saveReports(aiTaskResult)
    }
}

data class TaskListState(
        val aiState: AiSelectionState,
        val taskListItems: List<TaskListItem>)

data class AiSelectionState(
        val week1Ai: String?,
        val week5Ai: String?,
        val week9Ai: String?,
        val currentAi: String?,
        val shouldPromptUserForAi: Boolean = false)

data class AiClientData(
        val identifier: String,
        val ai: String?,
        val date: LocalDateTime)