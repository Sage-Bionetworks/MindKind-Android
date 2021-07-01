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
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.base.Preconditions
import dagger.android.AndroidInjection
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_task_list.*
import org.sagebionetworks.research.domain.result.AnswerResultType
import org.sagebionetworks.research.domain.result.implementations.AnswerResultBase
import org.sagebionetworks.research.domain.result.implementations.TaskResultBase
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService.Companion.SHOW_ENGAGEMENT_NOTIFICATION_ACTION
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService.Companion.isConversationComplete
import org.sagebionetworks.research.mindkind.conversation.*
import org.sagebionetworks.research.mindkind.research.SageTaskIdentifier
import org.sagebionetworks.research.mindkind.researchstack.framework.SageResearchStack
import org.sagebionetworks.research.mindkind.settings.SettingsActivity
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch.dao.room.ReportEntity
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.viewmodel.ReportViewModel
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
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

        viewModel = ViewModelProvider(this, TaskListViewModel.Factory(
                appConfigRepo, reportRepo)).get()

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

        task_progress_bar.max = 12 * 7 // 12 weeks in the study
        val progressInStudy = BackgroundDataService.progressInStudy(sharedPrefs)
        task_progress_bar.progress = progressInStudy.daysFromStart
        val weekStr = getString(R.string.week_x, progressInStudy.week.toString())
        val dayStr = getString(R.string.day_x, progressInStudy.dayOfWeek.toString())
        week_textview.text = "$weekStr | $dayStr"

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
}

open class TaskListViewModel(
        private val appConfigRepo: AppConfigRepository,
        reportRepo: ReportRepository) : ReportViewModel(reportRepo = reportRepo) {

    companion object {
        private val TAG = TaskListViewModel::class.java.simpleName
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

    fun saveAiAnswer(answer: String {
        var aiTaskResult = TaskResultBase(SageTaskIdentifier.AI, UUID.randomUUID())
        val aiResult = AnswerResultBase<String>(
                MindKindApplication.CURRENT_AI_RESULT_ID, Instant.now(), Instant.now(),
                answer, AnswerResultType.STRING)
        aiTaskResult = aiTaskResult.addStepHistory(aiResult)
        reportRepo.saveReports(aiTaskResult)
    }
}