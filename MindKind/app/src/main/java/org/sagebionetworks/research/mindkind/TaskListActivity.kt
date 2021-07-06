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
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_task_list.*
import org.joda.time.DateTime
import org.sagebionetworks.research.domain.result.AnswerResultType
import org.sagebionetworks.research.domain.result.implementations.AnswerResultBase
import org.sagebionetworks.research.domain.result.implementations.TaskResultBase
import org.sagebionetworks.research.mindkind.MindKindApplication.DATAGROUP_ARM2
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService.Companion.SHOW_ENGAGEMENT_NOTIFICATION_ACTION
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService.Companion.isConversationComplete
import org.sagebionetworks.research.mindkind.backgrounddata.ProgressInStudy
import org.sagebionetworks.research.mindkind.conversation.*
import org.sagebionetworks.research.mindkind.research.SageTaskIdentifier
import org.sagebionetworks.research.mindkind.researchstack.framework.SageResearchStack
import org.sagebionetworks.research.mindkind.settings.SettingsActivity
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.extensions.startOfDay
import org.sagebionetworks.research.sageresearch.extensions.toInstant
import org.sagebionetworks.research.sageresearch.viewmodel.ReportViewModel
import org.threeten.bp.*
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

    private val appConfigDisposable = CompositeDisposable()

    private lateinit var viewModel: TaskListViewModel
    private lateinit var sharedPrefs: SharedPreferences

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)

        sharedPrefs = BackgroundDataService.createSharedPrefs(this)

        val llm = LinearLayoutManager(this)
        llm.orientation = LinearLayoutManager.VERTICAL
        taskRecyclerView.layoutManager = llm

        val taskItems = mutableListOf(
                TaskItem("Sleep",
                        "Sleep",
                        "3 minutes",
                        "Sleep",
                        false),
                TaskItem("Social",
                        "Social",
                        "3 minutes",
                        "Social",
                        false),
                TaskItem(BackgroundDataService.BASELINE_IDENTIFIER_KEY,
                        "About You",
                        "4 minutes",
                        "Baseline",
                        false),
                TaskItem(BackgroundDataService.BASELINE_ENVIRONMENT_IDENTIFIER_KEY,
                        "Your Environment",
                        "3 minutes",
                        "Baseline_Environment",
                        false),
                TaskItem(BackgroundDataService.BASELINE_HABITS_IDENTIFIER_KEY,
                        "Your Habits",
                        "3 minutes",
                        "Baseline_Habits",
                        false)
        )

        // Useful for development
//          TaskItem("Playground",
//                  "Ready to start your day.",
//                  "Playground"))

        taskRecyclerView.addItemDecoration(SpacesItemDecoration(resources.getDimensionPixelSize(R.dimen.converation_recycler_spacing)))
        val adapter = TaskAdapter(taskItems, object : TaskAdapterListener {
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

        task_progress_bar.max = BackgroundDataService.studyDurationInWeeks * 7
        val progressInStudy = BackgroundDataService.progressInStudy(sharedPrefs)
        task_progress_bar.progress = progressInStudy.daysFromStart
        val weekStr = getString(R.string.week_x, progressInStudy.week.toString())
        val dayStr = getString(R.string.day_x, progressInStudy.dayOfWeek.toString())
        week_textview.text = "$weekStr | $dayStr"

        viewModel = ViewModelProvider(this, TaskListViewModel.Factory(
                appConfigRepo, reportRepo)).get()

        viewModel.currentAiLiveData().observe(this, Observer {
            val currentAi = it.lastOrNull()?.ai
            Log.i(TAG, "Current AI is $currentAi")
        })

        refreshServiceButtonState()

        // Auto-start background data collector
        startBackgroundDataService()

        // Did we enter this screen from the engagement notification?
        if (intent.action == SHOW_ENGAGEMENT_NOTIFICATION_ACTION) {
            showEngagementMessage()
        }

        // Refresh the app config
        appConfigDisposable.add(
                appConfigRepo.appConfig.firstOrError()
                        .subscribeOn(Schedulers.io())
                        .subscribe({
                            Log.i(TAG, "App config updated successfully")
                        }, {
                            Log.w(TAG, "App config updated failed ${it.localizedMessage}")
                        }))
    }

    @Override
    override fun onResume() {
        super.onResume()

        // Re-enables the disabled button
        updateTaskItems()
        taskRecyclerView.adapter?.notifyDataSetChanged()
    }

    @SuppressLint("SetTextI18n")
    fun refreshServiceButtonState() {
        if (BackgroundDataService.isServiceRunning) {
            buttonBackgroundData?.text = "Stop background data"
        } else {
            buttonBackgroundData?.text = "Start background data"
        }
    }

    fun updateTaskItems() {
        (taskRecyclerView.adapter as? TaskAdapter)?.let { taskAdapter ->
            taskAdapter.dataSet.forEach {
                it.isComplete = isConversationComplete(sharedPrefs, it.identifier)
            }
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

    fun showAiDialog() {

        // Check if we should assign a random ai instead
        val dataGroups = SageResearchStack.SageDataProvider.getInstance().userDataGroups
        if (dataGroups.contains(DATAGROUP_ARM2)) {
            val randomAiIndex = (Math.random() * 4).toInt()
            aiSelected(randomAiIndex)
            return
        }

        val dialog = Dialog(this)

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_ai_selection)

        val buttons = listOf<MaterialButton>(
                dialog.findViewById(R.id.ai_social),
                dialog.findViewById(R.id.ai_social),
                dialog.findViewById(R.id.ai_movements),
                dialog.findViewById(R.id.ai_experiences))

        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                aiSelected(index)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    fun aiSelected(index: Int) {
        val ai = MindKindApplication.aiForIndex(index)

    }
}

open class TaskListViewModel(
        private val appConfigRepo: AppConfigRepository,
        reportRepo: ReportRepository) : ReportViewModel(reportRepo = reportRepo) {

    companion object {
        private val TAG = TaskListViewModel::class.java.simpleName

        public fun consolidateAiValues(
                now: DateTime, zone: ZoneId,
                aiEntities: List<ReportEntity>?, baselineEntities: List<ReportEntity>?): AiSelectionState? {

            // We can only consolidate and create an AiSelectionState if we have all the request
            val aiReports = aiEntities ?: return null
            val baselineReports = baselineEntities ?: return null

            var aiSelection = AiSelectionState(null, null, null, null, false)

            if (baselineReports.isEmpty()) {
                // User needs to have done their baseline to set their AI
                return aiSelection
            }

            val dateTimeStartOfDay = now.withTimeAtStartOfDay()
            val startOfDayLocal = LocalDateTime.of(
                    dateTimeStartOfDay.year,
                    dateTimeStartOfDay.monthOfYear,
                    dateTimeStartOfDay.dayOfMonth, 0, 0, 0)
            val studyStartInstant = baselineReports.sortedBy { it.dateTime }.lastOrNull()?.dateTime ?: run {
                return null
            }

            val studyStartDate = DateTime(org.joda.time.Instant(studyStartInstant.toEpochMilli()))
                    .plusDays(1).withTimeAtStartOfDay() // end of day next day
            val progressInStudy = BackgroundDataService.progressInStudy(
                    dateTimeStartOfDay, studyStartDate)

            // This checks for week 1 needing to be collected
            if (aiReports.isEmpty()) {
                aiSelection = aiSelection.copy(shouldPromptUserForAi = )
                return aiSelection // User has done their baseline, but needs to assign their week 1 AI
            }

            // Time helper vars
            val studyStart = startOfDayLocal.minusDays(progressInStudy.daysFromStart.toLong())
            val week4Start = studyStart.plusDays(7 * 4)
            val week4StartInstant = week4Start.toInstant(zone)
            val week8Start = studyStart.plusDays(7 * 8)
            val week8StartInstant = week8Start.toInstant(zone)

            // Populate the AI selections base on report dateTime's
            aiReports.sortedBy { it.dateTime }.map {
                AiClientData(it.identifier ?: "",
                        ((it.data?.data as? Map<*, *>)?.get("Current_AI") as? String),
                        it.dateTime ?: Instant.now())
            }.forEach {
                if (it.date.isBefore(week4StartInstant)) {
                    aiSelection = aiSelection.copy(week1Ai = it.ai)
                } else if (it.date.isAfter(week4StartInstant) &&
                        it.date.isBefore(week8StartInstant)) {
                    aiSelection = aiSelection.copy(week4Ai = it.ai)
                } else if (it.date.isAfter(week8StartInstant)) {
                    aiSelection = aiSelection.copy(week8Ai = it.ai)
                }
            }

            // Check if user is in week 1 range
            if (startOfDayLocal.isBefore(week4Start)) {
                aiSelection = aiSelection.copy(
                        currentAi = aiSelection.week1Ai,
                        shouldPromptUserForAi = aiSelection.week1Ai != null)
                return aiSelection
            }

            // Check if user in week 4 range
            if (startOfDayLocal.isAfter(week4Start) && startOfDayLocal.isBefore(week8Start)) {
                aiSelection = aiSelection.copy(
                        currentAi = aiSelection.week4Ai,
                        shouldPromptUserForAi = aiSelection.week4Ai != null)
                return aiSelection
            }

            // Check if user is in week 8 or later
            if (startOfDayLocal.isAfter(week8Start)) {
                aiSelection = aiSelection.copy(
                        currentAi = aiSelection.week8Ai,
                        shouldPromptUserForAi = aiSelection.week8Ai != null)
                return aiSelection
            }

            return null
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

    private val compositeDisposable = CompositeDisposable()

    protected open var aiLiveData: MediatorLiveData<AiSelectionState>? = null
    private var aiReportsLiveData: LiveData<List<ReportEntity>>? = null
    private var baselineReportsLiveData: LiveData<List<ReportEntity>>? = null

    open fun startOfDayToday(): LocalDateTime {
        return LocalDate.now().atStartOfDay()
    }

    open fun zone(): ZoneId {
        return ZoneId.systemDefault()
    }

    fun getAllSurveyAnswers(): LiveData<List<ReportEntity>> {
        val endDate = LocalDateTime.now()
        val studyStartDate = endDate.minusDays(
                BackgroundDataService.studyDurationInWeeks * 7.toLong())
        return reportsLiveData(SageTaskIdentifier.Surveys, studyStartDate, endDate)
    }

    fun getAiSurveyAnswers(): LiveData<List<ReportEntity>> {
        val endDate = LocalDateTime.now()
        val studyStartDate = endDate.minusDays(
                (BackgroundDataService.studyDurationInWeeks + 1) * 7.toLong())
        return reportsLiveData(SageTaskIdentifier.AI, studyStartDate, endDate)
    }

    fun currentAiLiveData(): LiveData<List<AiClientData>> {
        val endDate = LocalDateTime.now().plusDays(1)
        val studyStartDate = endDate.minusDays(
                (BackgroundDataService.studyDurationInWeeks + 1) * 7.toLong())

        return Transformations.map(
                reportsLiveData(SageTaskIdentifier.AI, studyStartDate, endDate)) { reports ->
            return@map reports.sortedBy { it.dateTime }.map {
                AiClientData(it.identifier ?: "",
                        ((it.data?.data as? Map<*, *>)?.get("Current_AI") as? String),
                        it.dateTime ?: Instant.now())
            }
        }
    }

    fun aiSelectionLiveData(): LiveData<AiSelectionState> {
        val liveDataChecked = aiLiveData ?: {
            val mediator = MediatorLiveData<AiSelectionState>()

            val endDate = startOfDayToday().plusDays(1)
            val studyStartDate = endDate.minusDays(
                    (BackgroundDataService.studyDurationInWeeks + 1) * 7.toLong())

            val startOfDayLocal = LocalDateTime.now().startOfDay()

            // Consolidation first-class fun to be invoked below
            val consolidationFun: (() -> Unit) = {
                consolidateAiValues(startOfDayLocal, zone(),
                        aiReportsLiveData?.value, baselineReportsLiveData?.value)?.let {
                    mediator.postValue(it)
                }
            }

            val aiSelectionReports = aiReportsLiveData ?:
            reportsLiveData(SageTaskIdentifier.AI, studyStartDate, endDate)
            aiReportsLiveData = aiSelectionReports
            mediator.addSource(aiSelectionReports) { consolidationFun.invoke() }

            val baselineSelectionReports = baselineReportsLiveData ?:
                reportsLiveData(SageTaskIdentifier.Baseline, studyStartDate, endDate)
            baselineReportsLiveData = baselineSelectionReports
            mediator.addSource(aiSelectionReports) { consolidationFun.invoke() }

            mediator
        }.invoke()

        aiLiveData = liveDataChecked
        return liveDataChecked
    }

    fun saveAiAnswer(answer: String) {
        var aiTaskResult = TaskResultBase(SageTaskIdentifier.AI, UUID.randomUUID())
        val aiResult = AnswerResultBase<String>(
                MindKindApplication.CURRENT_AI_RESULT_ID, Instant.now(), Instant.now(),
                answer, AnswerResultType.STRING)
        aiTaskResult = aiTaskResult.addStepHistory(aiResult)
        reportRepo.saveReports(aiTaskResult)
    }
}

data class AiSelectionState(
        val week1Ai: String?,
        val week4Ai: String?,
        val week8Ai: String?,
        val currentAi: String?,
        val shouldPromptUserForAi: Boolean = false)

data class AiClientData(
        val identifier: String,
        val ai: String?,
        val date: Instant)