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
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_conversation_survey.*
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.conversation.ConversationSurveyActivity
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import javax.inject.Inject

import kotlinx.android.synthetic.main.activity_task_list.*
import org.sagebionetworks.research.mindkind.conversation.SpacesItemDecoration
import org.sagebionetworks.research.mindkind.settings.SettingsActivity

/**
 * A simple [Fragment] subclass that shows a list of the available surveys and tasks for the app
 */
class TaskListActivity : AppCompatActivity(), OnRequestPermissionsResultCallback {
    companion object {
        val logTag = TaskListActivity::class.simpleName
    }

    @Inject
    lateinit var reportRepo: ReportRepository

    @Inject
    lateinit var appConfigRepo: AppConfigRepository

    var taskItems = mutableListOf(
            TaskItem("GAD-7 Anxiety",
                    "How are you feeling today.",
                    "PHQ9"),
            TaskItem("Patient Health Questionaire",
                    "Ready to start your day.",
                    "GAD7"),
            TaskItem("Sleep Week1 Day 1",
                    "Sleep survey",
                    "Sleep_WK1_D1"),
            TaskItem("Sleep Week1 Day 2",
                    "Sleep survey",
                    "Sleep_WK1_D2"),
            TaskItem("Sleep Week1 Day 3",
                    "Sleep survey",
                    "Sleep_WK1_D3"),
            TaskItem("Sleep Week1 Day 4",
                    "Sleep survey",
                    "Sleep_WK1_D4"),
            TaskItem("Sleep Week1 Day 5",
                    "Sleep survey",
                    "Sleep_WK1_D5"),
            TaskItem("Sleep Week1 Day 6",
                    "Sleep survey",
                    "Sleep_WK1_D6"),
            TaskItem("Playground",
                    "Ready to start your day.",
                    "Playground"))

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)

        val llm = LinearLayoutManager(this)
        llm.orientation = LinearLayoutManager.VERTICAL
        taskRecyclerView.layoutManager = llm

        taskRecyclerView.addItemDecoration(SpacesItemDecoration(resources.getDimensionPixelSize(R.dimen.converation_recycler_spacing)))
        val adapter = TaskAdapter(taskItems, object: TaskAdapterListener {
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
    }

    @Override
    override fun onResume() {
        super.onResume()
        // Re-enables the disabled button
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
}