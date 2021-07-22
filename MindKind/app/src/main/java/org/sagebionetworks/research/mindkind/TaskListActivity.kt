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
import android.net.Uri
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
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_task_list.*
import org.sagebionetworks.research.mindkind.MindKindApplication.*
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService.Companion.SHOW_ENGAGEMENT_NOTIFICATION_ACTION
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService.Companion.hasShownRecruitmentNotifKey
import org.sagebionetworks.research.mindkind.backgrounddata.ProgressInStudy
import org.sagebionetworks.research.mindkind.conversation.*
import org.sagebionetworks.research.mindkind.researchstack.framework.SageResearchStack
import org.sagebionetworks.research.mindkind.settings.SettingsActivity
import org.sagebionetworks.research.mindkind.viewmodel.TaskListViewModel
import org.sagebionetworks.research.mindkind.viewmodel.TaskListViewModel.Companion.studyStartDateKey
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import org.sagebionetworks.research.sageresearch.extensions.*
import org.threeten.bp.*
import java.util.*
import javax.inject.Inject

/**
 * A simple [Fragment] subclass that shows a list of the available surveys and tasks for the app
 */
class TaskListActivity : AppCompatActivity(), OnRequestPermissionsResultCallback {
    companion object {
        private val TAG = TaskListActivity::class.simpleName
        public const val prefsIntroAlertKey = "BaselineIntroAlertKey"

        public const val prefsWeek1AlertKey =  "AiAlertKeyWeek1"
        public const val prefsWeek5AlertKey =  "AiAlertKeyWeek5"
        public const val prefsWeek9AlertKey =  "AiAlertKeyWeek9"

        public fun prefsHasShownAiAlert(sharedPrefs: SharedPreferences, weekInStudy: Int): Boolean {
            return sharedPrefs.contains(
                when (weekInStudy) {
                    in 5..8 -> prefsWeek5AlertKey
                    in 9..12 -> prefsWeek9AlertKey
                    else -> prefsWeek1AlertKey
                })
        }
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
    private var recruitmentLiveData: LiveData<RecruitmentAppConfig?>? = null

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

        recruitmentLiveData = viewModel.recruitmentJsonLiveData()
        recruitmentLiveData?.observe(this, Observer {
            Log.i(TAG, "Recuitment app config value $it")
        })
    }

    @SuppressLint("ApplySharedPref")
    private fun refreshLiveData(adapter: TaskAdapter) {
        Log.d(TAG, "Refresh live data")

        taskLiveData?.removeObservers(this)
        studyProgressLiveData?.removeObservers(this)
        viewModel.clearCache()

        loading_progress.visibility = View.VISIBLE
        taskLiveData = viewModel.taskListLiveData(sharedPrefs)
        taskLiveData?.observe(this, Observer {
            Log.i(TAG, "At ${LocalDateTime.now()} AI state is ${it.aiState}")
            loading_progress.visibility = View.GONE

            viewModel.saveAiState(sharedPrefs, it.aiState)

            // Process if we should show alerts to the user
            val shouldShowRoiAlert = (it.returnOfInfo?.shouldShowAlert == true)
            val shouldShowAiAlert = it.aiState.shouldPromptUserForAi
            if (shouldShowRoiAlert) {
                val weekIdx = it.aiState.progressInStudy?.week ?: 1
                val ai = if (shouldShowAiAlert) it.aiState else null
                showRoiAlert(it.returnOfInfo, ai, weekIdx)
            } else if (shouldShowAiAlert) {
                showAiDialog(it.aiState)
            }

            // Update the task items
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
                showBaselineIntroAlertIfNecessary()
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
        if (didShowRecruitmentAlert()) {
            return // Check for recruitment alert when a user goes to do a survey
        }

        val fileName = jsonResourceName ?: run { return }
        ConversationSurveyActivity.start(this, fileName)
    }

    fun didShowRecruitmentAlert(): Boolean {
        val recruitment = recruitmentLiveData?.value ?: run { return false }
        val progress = studyProgressLiveData?.value ?: run { return false }

        if (sharedPrefs.getBoolean(hasShownRecruitmentNotifKey, false)) {
            return false // Already showed the recruitment notif
        }

        val now = LocalDateTime.now()
        if (progress.week >= recruitment.notifyAtStartOfWeek &&
                now.isAfter(recruitment.startDate) && now.isBefore(recruitment.endDate)) {

            showRecruitmentAlert(recruitment)
            return true
        }

        return false
    }

    fun showRecruitmentAlert(recruitment: RecruitmentAppConfig) {
        sharedPrefs.edit().putBoolean(hasShownRecruitmentNotifKey, true).apply()

        val dialog = Dialog(this)

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.dialog_2_button_message)
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)

        val title = dialog.findViewById<TextView>(R.id.dialog_title)
        title?.text = null
        title.visibility = View.GONE

        val msg = dialog.findViewById<TextView>(R.id.dialog_message)
        msg?.text = recruitment.title

        val negButton = dialog.findViewById<MaterialButton>(R.id.confirm_button)
        negButton?.text = getString(R.string.rsb_BOOL_NO)
        negButton?.setOnClickListener {
            dialog.dismiss()
        }

        val posButton = dialog.findViewById<MaterialButton>(R.id.cancel_button)
        posButton?.text = getString(R.string.rsb_BOOL_YES)
        posButton?.setOnClickListener {
            dialog.dismiss()
            goToWebpage(recruitment.url)
        }

        dialog.show()
    }

    fun goToWebpage(uriString: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
        startActivity(browserIntent)
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

        if (prefsHasShownAiAlert(sharedPrefs, aiSelection.progressInStudy?.week ?: 1)) {
            Log.d(TAG, "Dialog already shown in the past, skip it")
            return // already showed the dialog
        }

        // Check if we should assign a random ai instead
        val dataGroups = SageResearchStack.SageDataProvider.getInstance().userDataGroups
        val isUserInARM2 = dataGroups.contains(DATAGROUP_ARM2)

        var aiText = ""
        var title: String? = null
        var text: String? = null

        val randomAiIndex = (Math.random() * 4).toInt()
        if (isUserInARM2) {
            aiText = titleForAIIdentifier(MindKindApplication.aiForIndex(randomAiIndex))
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

    private fun showRoiAlert(roiState: RoiDialogState?, shouldMoveTo: AiSelectionState?, weekIdx: Int) {
        val roi = roiState ?: run { return }
        val dialog = Dialog(this)

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_basic_message)
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)

        val title = dialog.findViewById<TextView>(R.id.dialog_title)
        title?.text = getString(R.string.home_return_title)

        val msg = dialog.findViewById<TextView>(R.id.dialog_message)
        if (roi.enoughDataToShow) {
            val preFormattedStr = getString(R.string.home_return_message_5strings)
            val text1 = getString(roi.AI_text1)
            val text2 = getString(roi.AI_text2)
            val text3 = getString(roi.AI_text3)
            val text4 = roi.countMoodDaily.toString()
            val text5 = roi.countAiDaily.toString()
            msg?.text = preFormattedStr.format(text1, text2, text3, text4, text5)
        } else {
            msg?.text = getString(R.string.home_return_message_not_enough)
        }

        val closeButton = dialog.findViewById<MaterialButton>(R.id.close_button)
        closeButton?.text = getString(R.string.home_okay)
        closeButton?.setOnClickListener { view ->
            dialog.dismiss()
            shouldMoveTo?.let { showAiDialog(it) }
        }

        dialog.show()
        viewModel.saveDidShowRoiAlert(sharedPrefs, weekIdx)
    }

    private fun titleForAIIdentifier(title: String): String {
        when (title) {
            SOCIAL_AI -> return getString(R.string.social)
            SLEEP_AI -> return getString(R.string.sleep)
            BODY_MOVEMENT_AI -> return getString(R.string.movement)
            POSITIVE_EXPERIENCES_AI -> return getString(R.string.positive_experiences)
            else -> return getString(R.string.health) // Shouldn't ever hit the default
        }
    }

    private fun aiSelected(index: Int) {
        val ai = MindKindApplication.aiForIndex(index)
        viewModel.saveAiAnswer(ai)
    }

    private fun showBaselineIntroAlertIfNecessary() {
        if (sharedPrefs.getBoolean(prefsIntroAlertKey, false)) {
            return
        }
        sharedPrefs.edit().putBoolean(prefsIntroAlertKey, true).apply()

        val dialog = Dialog(this)

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.dialog_basic_message)
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)

        val title = dialog.findViewById<TextView>(R.id.dialog_title)
        title?.text = getString(R.string.home_baseline_intro_title)

        val msg = dialog.findViewById<TextView>(R.id.dialog_message)
        msg?.text = getString(R.string.home_baseline_intro_message)

        val closeButton = dialog.findViewById<MaterialButton>(R.id.close_button)
        closeButton?.text = getString(R.string.home_baseline_intro_continue)
        closeButton?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}

data class TaskListState(
        val aiState: AiSelectionState,
        val taskListItems: List<TaskListItem>,
        val returnOfInfo: RoiDialogState? = null)

data class AiSelectionState(
        val week1Ai: String?,
        val week5Ai: String?,
        val week9Ai: String?,
        val currentAi: String?,
        val shouldPromptUserForAi: Boolean = false,
        val progressInStudy: ProgressInStudy? = null)

data class AiClientData(
        val identifier: String,
        val ai: String?,
        val date: LocalDateTime)

data class RecruitmentAppConfig(
        val url: String,
        val notifyAtStartOfWeek: Int,
        val startDate: LocalDateTime,
        val endDate: LocalDateTime,
        val title: String)

data class RoiDialogState(
        val AI_text1: Int,
        val AI_text2: Int,
        val AI_text3: Int,
        val countMoodDaily: Int = 0,
        val countAiDaily: Int = 0,
        val enoughDataToShow: Boolean = false,
        val shouldShowAlert: Boolean = false)