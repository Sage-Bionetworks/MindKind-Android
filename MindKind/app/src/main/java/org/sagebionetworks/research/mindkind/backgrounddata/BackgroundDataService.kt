
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

package org.sagebionetworks.research.mindkind.backgrounddata

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Builder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import dagger.android.DaggerService
import io.reactivex.Completable
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.sagebionetworks.research.domain.Schema
import org.sagebionetworks.research.domain.result.implementations.FileResultBase
import org.sagebionetworks.research.domain.result.implementations.TaskResultBase
import org.sagebionetworks.research.domain.result.interfaces.Result
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.mindkind.R.drawable
import org.sagebionetworks.research.mindkind.R.string
import org.sagebionetworks.research.mindkind.research.SageTaskIdentifier
import org.sagebionetworks.research.mindkind.room.BackgroundDataEntity
import org.sagebionetworks.research.mindkind.room.MindKindDatabase
import org.sagebionetworks.research.presentation.perform_task.TaskResultManager
import org.sagebionetworks.research.presentation.perform_task.TaskResultProcessingManager
import org.sagebionetworks.research.sageresearch.extensions.toInstant
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.temporal.ChronoUnit
import java.io.File
import java.util.*
import javax.inject.Inject

class BackgroundDataService : DaggerService() {

    companion object {
        public const val UPLOAD_DATA_ACTION = "UPLOAD_DATA_TO_BRIDGE_ACTION"
        public const val BACKGROUND_DATA_DB_FILENAME = "org.sagebionetworks.research.MindKind.BackgroundData"
        private const val TAG = "BackgroundDataService"
        private const val TASK_IDENTIFIER = SageTaskIdentifier.BACKGROUND_DATA
        private const val FOREGROUND_NOTIFICATION_ID = 100
        private const val JSON_MIME_CONTENT_TYPE = "application/json"

        // True when this service is running, false otherwise
        public var isServiceRunning = false

        private fun createMinuteRateLimit(): RateLimiter {
            return RateLimiter(1000L * 60L)
        }
    }

    private var taskUUID = UUID.randomUUID()
    private val taskIdentifier = SageTaskIdentifier.BACKGROUND_DATA

    lateinit var database: MindKindDatabase

    @Inject
    lateinit var taskResultManager: TaskResultManager

    @Inject
    lateinit var taskResultProcessingManager: TaskResultProcessingManager

    @Inject
    lateinit var taskResultUploader: TaskResultUploader

    private val compositeDisposable = CompositeDisposable()
    /**
     * Subscribes to the completable using the CompositeDisposable
     * This is open for unit testing purposes to to run a blockingGet() instead of an asynchronous subscribe
     */
    @VisibleForTesting
    protected open fun subscribeCompletableAsync(
            completable: Completable, successMsg: String, errorMsg: String) {

        compositeDisposable.add(completable
                .subscribeOn(asyncScheduler)
                .subscribe({
                    Log.i(TAG, successMsg)
                }, {
                    Log.w(TAG, "$errorMsg ${it.localizedMessage}")
                }))
    }
    /**
     * @property asyncScheduler for performing network and database operations
     */
    @VisibleForTesting
    protected open val asyncScheduler: Scheduler get() = Schedulers.io()

    private val receiver = BackgroundDataReceiver()

    private val noRateLimit = NoLimitRateLimiter()
    private val batterChangedLimiter = createMinuteRateLimit()

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()

        isServiceRunning = true
        startForeground()

        database = Room.databaseBuilder(this,
                MindKindDatabase::class.java,
                BACKGROUND_DATA_DB_FILENAME)
                /* .addMigrations(*migrations) */
                .build()

        // Register broadcast receivers
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT).apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(receiver, filter)

        LocalBroadcastManager.getInstance(this).registerReceiver(
                uploadDataReceiver, IntentFilter(UPLOAD_DATA_ACTION))
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")

        LocalBroadcastManager.getInstance(this).unregisterReceiver(uploadDataReceiver)
        unregisterReceiver(receiver)
        compositeDisposable.clear()
        stopForeground(true)

        isServiceRunning = false

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind")
        return null
    }

    private val uploadDataReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            uploadDataToBridge()
        }
    }

    private fun uploadDataToBridge() {

        val completable = Completable.fromAction {
            val dataToUpload =
                    database.backgroundDataDao().getData(false)

            if (dataToUpload.isEmpty()) {
                return@fromAction
            }

            val taskResultBase = createTaskResult(dataToUpload)

            // Upload task result
            subscribeCompletableAsync(
                    taskResultUploader.processTaskResult(taskResultBase),
                    "Successfully uploaded task result to bridge",
                    "Failed to upload task result to bridge")

            // Write database changes
            dataToUpload.forEach {
                it.uploaded = true
            }
            database.backgroundDataDao().upsert(dataToUpload)

        } .doOnError {
            Log.w(TAG, it.localizedMessage ?: "")
        }

        subscribeCompletableAsync(completable,
                "Successfully saved BackgroundData to room",
                "Failed to save BackgroundData to room")
    }

    private fun createTaskResult(backgroundData: List<BackgroundDataEntity>): TaskResultBase {
        val startTime = backgroundData.firstOrNull()?.date
                ?.toInstant(ZoneId.systemDefault()) ?: Instant.now()
        val endTime = backgroundData.lastOrNull()?.date
                ?.toInstant(ZoneId.systemDefault()) ?: Instant.now()

        val json = BackgroundDataTypeConverters().gson.toJson(backgroundData)
        openFileOutput("data.json", Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }

        val jsonFileResult = FileResultBase("data",
                startTime, endTime, JSON_MIME_CONTENT_TYPE, filesDir.path + File.separator + "data.json")

        var taskResultBase = TaskResultBase(TASK_IDENTIFIER, startTime,
                endTime, UUID.randomUUID(), Schema(TASK_IDENTIFIER, 1),
                mutableListOf(), mutableListOf())

        taskResultBase = taskResultBase.addStepHistory(jsonFileResult)
        return taskResultBase
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: startId = $startId")

//        START_STICKY- tells the system to create a fresh copy of the service,
//        when sufficient memory is available, after it recovers from low memory.
//        Here you will lose the results that might have computed before.
//
//        START_NOT_STICKY- tells the system not to bother to restart the service,
//        even when it has sufficient memory.
//
//        START_REDELIVER_INTENT- tells the system to restart the service after the crash
//                and also redeliver the intents that were present at the time of crash.

        return START_STICKY
    }

    private fun startForeground() {
        createNotificationChannel()
        val notification = createNotification(
                "Monitoring for data changes.  We only collect data you have consented to sharing.")
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun createNotification(text: String? = null): Notification {
        return Builder(this, getString(string.foreground_channel_id))
                .setContentTitle("MindKind Study")
                .setContentText(text ?: "")
                .setSmallIcon(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            drawable.ic_launcher_foreground else R.mipmap.ic_launcher)
                .setStyle(NotificationCompat.BigTextStyle()
                        .bigText(text ?: ""))
                .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(FOREGROUND_NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val title = getString(string.foreground_channel_title)
            val desc = getString(string.foreground_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val mChannel = NotificationChannel(getString(string.foreground_channel_id), title, importance)
            mChannel.description = desc
            mChannel.importance = IMPORTANCE_LOW
            // Register the channel with the system; can't change importance or other behaviors after this
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(mChannel)
        }
    }

    /**
     * Some broadcast receivers are called too frequently and need rate limited
     * to avoid constantly writing to the database and draining the user's battery
     * @param dataType to rate limit
     */
    fun rateLimiterFor(dataType: BackgroundDataType): RateLimiter {
        return when(dataType) {
            BackgroundDataType.BATTERY_PERCENTAGE -> batterChangedLimiter
            else -> noRateLimit
        }
    }

    /**
     * Writes background data to room asynchronously
     * @param backgroundData to add to room
     */
    fun writeBackgroundDataToRoom(backgroundData: BackgroundDataEntity) {
        val completable = Completable.fromAction {
            database.backgroundDataDao().upsert(listOf(backgroundData))
        } .doOnError {
            Log.w(TAG, it.localizedMessage ?: "")
        }
        subscribeCompletableAsync(completable,
                "Successfully saved BackgroundData to room",
                "Failed to save BackgroundData to room")
    }

    inner class BackgroundDataReceiver : BroadcastReceiver() {

        override fun onReceive(ctx: Context, intent: Intent) {
            val dataType = BackgroundDataType.from(intent) ?: run {
                Log.e(TAG, "Error creating data type from intent action")
                return
            }

            val now = LocalDateTime.now()

            // Check if we should rate-limit this data type
            if (rateLimiterFor(dataType).shouldLimit(now)) {
                return
            }

            val backgroundData = BackgroundDataEntity(
                    date = now,
                    dataType = dataType,
                    uploaded = false)

            writeBackgroundDataToRoom(backgroundData)

            // Do dataType specific operations
            when(intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> onReceiveBatteryChanged(intent)
                else -> onReceiveDefaultActionChanged(intent)
            }
        }

        private fun onReceiveDefaultActionChanged(intent: Intent) {
            Log.d(TAG, "Received ${intent.action}")
        }

        private fun onReceiveBatteryChanged(intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = level * 100 / scale.toFloat()
            Log.d(TAG, "Received $batteryPct% ${intent.action}")
            // TODO: mdephillips 3/7/21 save battery percentage entity in its own table
        }
    }
}

open class NoLimitRateLimiter: RateLimiter(0) {
    override fun shouldLimit(eventTime: LocalDateTime): Boolean {
        return false
    }
}

open class RateLimiter(val limitTime: Long) {
    var lastEventTime: LocalDateTime? = null
    /**
     * @param eventTime time of event to possibly limit
     * @return if eventTime should be limited or not.
     *          note: if returning true, eventTime will
     *          be used as limit time on next function call
     */
    open fun shouldLimit(eventTime: LocalDateTime): Boolean {
        val last = lastEventTime ?: run {
            lastEventTime = LocalDateTime.now()
            return false
        }
        if (ChronoUnit.MILLIS.between(last, eventTime) > limitTime) {
            lastEventTime = LocalDateTime.now()
            return false
        }
        return true
    }
}

public enum class BackgroundDataType(val type: String) {
    SCREEN_ON("Screen_On"),
    SCREEN_OFF("Screen_Off"),
    USER_PRESENT("User_Present"),
    BATTERY_PERCENTAGE("Battery_Percentage"),
    POWER_CONNECTED("Power_Connected"),
    POWER_DISCONNECTED("Power_Disconnected");

    companion object {
        /**
         * @return the background data type for the action
         */
        fun from(intent: Intent): BackgroundDataType? {
            val action = intent.action ?: run { return null }
            return when(action) {
                Intent.ACTION_BATTERY_CHANGED -> BATTERY_PERCENTAGE
                Intent.ACTION_SCREEN_ON -> SCREEN_ON
                Intent.ACTION_SCREEN_OFF -> SCREEN_OFF
                Intent.ACTION_USER_PRESENT -> USER_PRESENT
                Intent.ACTION_POWER_CONNECTED -> POWER_CONNECTED
                Intent.ACTION_POWER_DISCONNECTED -> POWER_DISCONNECTED
                else -> null
            }
        }
    }
}
