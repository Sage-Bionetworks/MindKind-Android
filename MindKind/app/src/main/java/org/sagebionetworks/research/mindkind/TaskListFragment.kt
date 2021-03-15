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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RelativeLayout
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.android.support.DaggerFragment
import org.sagebionetworks.research.mindkind.backgrounddata.BackgroundDataService
import org.sagebionetworks.research.mindkind.conversation.ConversationSurveyActivity
import org.sagebionetworks.research.sageresearch.dao.room.AppConfigRepository
import org.sagebionetworks.research.sageresearch.dao.room.ReportRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import javax.inject.Inject

/**
 * A simple [Fragment] subclass that shows a list of the available surveys and tasks for the app
 */
class TaskListFragment : DaggerFragment(), OnRequestPermissionsResultCallback {
    companion object {
        val logTag = TaskListFragment::class.simpleName
    }

    @Inject
    lateinit var reportRepo: ReportRepository

    @Inject
    lateinit var appConfigRepo:     AppConfigRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_task_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<RelativeLayout>(R.id.buttonPhq9).setOnClickListener {
            launchPhq9()
        }

        view.findViewById<RelativeLayout>(R.id.buttonGad7).setOnClickListener {
            launchGad7()
        }

        val uploadDataButton = view.findViewById<Button>(R.id.buttonUploadData)
        uploadDataButton.visibility = View.GONE
        uploadDataButton.setOnClickListener {
            uploadBackgroundData()
        }

        val serviceButton = view.findViewById<Button>(R.id.buttonBackgroundData)
        serviceButton.visibility = View.GONE
        serviceButton.setOnClickListener {
            if (serviceButton.text == "Start background data") {
                startBackgroundDataService(serviceButton)
            } else {
                stopBackgroundDataService(serviceButton)
            }
        }

        refreshServiceButtonState()

        // Auto-start background data collector
        startBackgroundDataService()
    }

    fun refreshServiceButtonState() {
        if (BackgroundDataService.isServiceRunning) {
            view?.findViewById<Button>(R.id.buttonBackgroundData)?.text = "Stop background data"
        } else {
            view?.findViewById<Button>(R.id.buttonBackgroundData)?.text = "Start background data"
        }
    }

    fun launchPhq9() {
        val json = stringFromJsonAsset("PHQ9") ?: run { return }
        val ctx = context ?: run { return }
        ConversationSurveyActivity.start(ctx, json)
    }

    fun launchGad7() {
        val json = stringFromJsonAsset("GAD7") ?: run { return }
        val ctx = context ?: run { return }
        ConversationSurveyActivity.start(ctx, json)
    }

    fun uploadBackgroundData() {
        // Notifies the server that it should upload the background data to bridge
        LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(BackgroundDataService.ACTIVITY_UPLOAD_DATA_ACTION))
    }

    fun startBackgroundDataService(button: Button? = null) {
        @SuppressLint("SetTextI18n")
        button?.text = "Stop background data"
        val ctx = activity ?: run { return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(Intent(ctx, BackgroundDataService::class.java))
        } else {
            ctx.startService(Intent(ctx, BackgroundDataService::class.java))
        }
    }

    fun stopBackgroundDataService(button: Button? = null) {
        @SuppressLint("SetTextI18n")
        button?.text = "Start background data"
        val ctx = activity ?: run { return }
        ctx.stopService(Intent(ctx, BackgroundDataService::class.java))
    }

    fun stringFromJsonAsset(fileName: String): String? {
        val contextUnwrapped = context ?: run { return null }
        val assetPath = "task/$fileName.json"
        val inputStream = InputStreamReader(contextUnwrapped.assets.open(assetPath), StandardCharsets.UTF_8)
        val r = BufferedReader(inputStream)
        val total = StringBuilder()
        var line: String? = null
        while (r.readLine().also({ line = it }) != null) {
            total.append(line).append('\n')
        }
        return total.toString()
    }
}