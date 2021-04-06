
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
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Builder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import dagger.android.DaggerService
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.Completable
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.sagebionetworks.bridge.android.manager.UploadManager
import org.sagebionetworks.research.domain.Schema
import org.sagebionetworks.research.domain.result.implementations.FileResultBase
import org.sagebionetworks.research.domain.result.implementations.TaskResultBase
import org.sagebionetworks.research.mindkind.R
import org.sagebionetworks.research.mindkind.R.drawable
import org.sagebionetworks.research.mindkind.R.string
import org.sagebionetworks.research.mindkind.research.SageTaskIdentifier
import org.sagebionetworks.research.mindkind.room.BackgroundDataEntity
import org.sagebionetworks.research.mindkind.room.BackgroundDataTypeConverters
import org.sagebionetworks.research.mindkind.room.MindKindDatabase
import org.sagebionetworks.research.mindkind.util.NoLimitRateLimiter
import org.sagebionetworks.research.mindkind.util.RateLimiter
import org.sagebionetworks.research.sageresearch.extensions.toInstant
import org.sagebionetworks.research.sageresearch_app_sdk.TaskResultUploader
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject

class BackgroundDataService : DaggerService() {

    companion object {
        private const val TAG = "BackgroundDataService"

        public const val ACTIVITY_UPLOAD_DATA_ACTION =
                "org.sagebionetworks.research.MindKind.UploadDataToBridge"
        public const val WIFI_CHARGING_UPLOAD_DATA_ACTION =
                "org.sagebionetworks.research.MindKind.WifiChargingUploadDataToBridge"
        public const val BACKGROUND_DATA_DB_FILENAME =
                "org.sagebionetworks.research.MindKind.BackgroundData"
        public const val DATA_USAGE_RECEIVER_ACTION =
                "org.sagebionetworks.research.MindKind.DataUsageReceiver"

        private const val TASK_IDENTIFIER = SageTaskIdentifier.BACKGROUND_DATA
        private const val FOREGROUND_NOTIFICATION_ID = 100
        private const val JSON_MIME_CONTENT_TYPE = "application/json"

        // True when this service is running, false otherwise
        public var isServiceRunning = false

        private fun createMinuteRateLimit(minutes: Int): RateLimiter {
            return RateLimiter(minutes * 1000L * 60L)
        }

        private fun createHourRateLimit(hours: Double): RateLimiter {
            return RateLimiter((hours * 1000.0 * 60.0 * 60.0).toLong())
        }

        fun chargingTimeData(intentAction: String?): String {
            return when (intentAction) {
                Intent.ACTION_POWER_CONNECTED -> "connected"
                Intent.ACTION_POWER_DISCONNECTED -> "disconnected"
                else -> "error"
            }
        }

        fun screenTimeData(intentAction: String?): String {
            return when (intentAction) {
                Intent.ACTION_SCREEN_ON -> "on"
                Intent.ACTION_SCREEN_OFF -> "off"
                Intent.ACTION_USER_PRESENT -> "present"
                else -> "error"
            }
        }
    }

    private val typeConverters = BackgroundDataTypeConverters()
    lateinit var database: MindKindDatabase

    // Injected from BridgeAndroidSdk, this controls uploading a TaskResult
    @Inject
    lateinit var taskResultUploader: TaskResultUploader

    // Injected from BridgeAndroidSdk, it controls uploading past failed uploads
    @Inject
    lateinit var uploadManager: UploadManager

    // List of data types to track
    // This should be hooked up to the permission manager's list of data the user wants us to track
    public var dataToTrack = listOf(
            SageTaskIdentifier.ScreenTime,
            SageTaskIdentifier.BatteryStatistics,
            SageTaskIdentifier.ChargingTime,
            SageTaskIdentifier.DataUsage)

    private val compositeDisposable = CompositeDisposable()
    /**
     * Subscribes to the completable using the CompositeDisposable
     * This is open for unit testing purposes to to run a blockingGet() instead of an asynchronous subscribe
     */
    private fun subscribeCompletableAsync(
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
    private val asyncScheduler: Scheduler get() = Schedulers.io()

    private val receiver = BackgroundDataReceiver()

    // If you always want to log an event, use no rate limit
    private val noRateLimit = NoLimitRateLimiter()

    // Battery changed broadcast receiver is very noisy, so rate limit it every 1 hour
    private val batteryChangedLimiter = createHourRateLimit(1.0)

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()

        // Setup daily wifi & charger upload worker
        WorkUtils.enqueueDailyWorkNetwork(this,
                BridgeUploadWorker::class.java, BridgeUploadWorker.periodicWorkName)
        // Setup data usage worker
        if (dataToTrack.contains(SageTaskIdentifier.DataUsage)) {
            WorkUtils.enqueueHourlyWork(this,
                    DataUsageWorker::class.java, DataUsageWorker.periodicWorkName)
        }

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
                uploadDataReceiver, IntentFilter(ACTIVITY_UPLOAD_DATA_ACTION))

        // Some events need to be tracked immediately
        recordStartupBackgroundData()
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

    private fun recordStartupBackgroundData() {
        writeDataUsageToDatabase()
    }

    /**
     * Packages all existing un-uploaded data and attempts to upload it to bridge
     */
    private fun uploadDataToBridge() {
        // Always try to upload all past failed uploads if any exist
        subscribeCompletableAsync(
                RxJavaInterop.toV2Completable(uploadManager.processUploadFiles()),
                "Successfully uploaded all past data in upload manager",
                "Failed to uploaded all past data in upload manager")

        // Upload all the data from the background data table
        subscribeCompletableAsync(Completable.fromAction {
            val data =
                    database.backgroundDataDao().getData(false)

            if (data.isEmpty()) {
                return@fromAction
            }

            val taskResultList = mutableListOf<TaskResultBase>()
            // Only upload data the user allowed to track
            dataToTrack.forEach { taskIdentifier ->
                val filtered = data.filter {
                    return@filter it.dataType == taskIdentifier
                }
                if (filtered.isNotEmpty()) {
                    taskResultList.add(createTaskResult(taskIdentifier, filtered))
                }
            }

            // Upload all task results
            taskResultList.forEach {
                subscribeCompletableAsync(
                        taskResultUploader.processTaskResult(it),
                        "Successfully uploaded ${it.identifier} task result to bridge",
                        "Failed to upload ${it.identifier} task result to bridge")
            }

            // Archive/uploader always eventually succeeds so mark all data as uploaded
            data.forEach {
                it.uploaded = true
            }
            database.backgroundDataDao().upsert(data)

        } .doOnError {
            Log.w(TAG, it.localizedMessage ?: "")
        },
        "Successfully saved BackgroundData to room",
        "Failed to save BackgroundData to room")
    }

    private fun createTaskResult(taskIdentifier: String,
                                 backgroundData: List<BackgroundDataEntity>): TaskResultBase {

        val startTime = backgroundData.firstOrNull()?.date
                ?.toInstant(ZoneId.systemDefault()) ?: Instant.now()
        val endTime = backgroundData.lastOrNull()?.date
                ?.toInstant(ZoneId.systemDefault()) ?: Instant.now()

        val json = BackgroundDataTypeConverters().gsonExposeOnly.toJson(backgroundData)
        val folderPath = cacheDir.absolutePath + File.separator + taskIdentifier
        val folder = File(folderPath)
        // Create folder to hold data file
        if (!folder.exists()) {
            folder.mkdir()
        }

        val file = File(folder, "data.json")
        val stream = FileOutputStream(file)
        try {
            stream.write(json.toByteArray())
        } finally {
            stream.close()
        }

        val jsonFileResult = FileResultBase("data",
                startTime, endTime, JSON_MIME_CONTENT_TYPE,
                folderPath + File.separator + "data.json")

        var taskResultBase = TaskResultBase(taskIdentifier, startTime,
                endTime, UUID.randomUUID(), Schema(taskIdentifier, 1),
                mutableListOf(), mutableListOf())

        taskResultBase = taskResultBase.addStepHistory(jsonFileResult)
        return taskResultBase
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: startId = $startId")

        if (intent?.action == WIFI_CHARGING_UPLOAD_DATA_ACTION) {
            uploadDataToBridge()
        }

        if (intent?.action == DATA_USAGE_RECEIVER_ACTION) {
            writeDataUsageToDatabase()
        }

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
    fun rateLimiterFor(dataType: String): RateLimiter {
        return when(dataType) {
            SageTaskIdentifier.BatteryStatistics -> batteryChangedLimiter
            else -> noRateLimit
        }
    }

    /**
     * Writes background data to room asynchronously
     * @param backgroundData to add to room
     */
    fun writeBackgroundDataToRoom(backgroundData: List<BackgroundDataEntity>) {
        backgroundData.forEach {
            Log.d(TAG, "Writing ${it.dataType} ${it.subType ?: ""} ${it.data}")
        }

        val completable = Completable.fromAction {
            database.backgroundDataDao().upsert(backgroundData)
        } .doOnError {
            Log.w(TAG, it.localizedMessage ?: "")
        }
        subscribeCompletableAsync(completable,
                "Successfully saved BackgroundData to room",
                "Failed to save BackgroundData to room")
    }

    /**
     * Writes background data to room asynchronously
     * @param backgroundData to add to room
     */
    fun writeBackgroundDataToRoom(backgroundData: BackgroundDataEntity) {
        writeBackgroundDataToRoom(listOf(backgroundData))
    }

    fun writeDataUsageToDatabase() {
        if (!dataToTrack.contains(SageTaskIdentifier.DataUsage)) {
            // User did not consent to have this tracked
            return
        }

        val now = LocalDateTime.now()
        val backgroundData = mutableListOf<BackgroundDataEntity>()
        mapOf(
            "totalRx" to TrafficStats.getTotalRxBytes(),
            "totalTx" to TrafficStats.getTotalTxBytes(),
            "mobileRx" to TrafficStats.getMobileRxBytes(),
            "mobileTx" to TrafficStats.getMobileTxBytes()).forEach {

            backgroundData.add(BackgroundDataEntity(
                    date = now,
                    dataType = SageTaskIdentifier.DataUsage,
                    subType = it.key,
                    uploaded = false,
                    data = it.value.toString()))
        }

        writeBackgroundDataToRoom(backgroundData)
    }

    inner class BackgroundDataReceiver : BroadcastReceiver() {

        override fun onReceive(ctx: Context, intent: Intent) {
            val dataType = dataType(intent) ?: run {
                Log.e(TAG, "Error creating data type from intent action")
                return
            }

            val now = LocalDateTime.now()

            // Check if we should rate-limit this data type
            if (rateLimiterFor(dataType).shouldLimit(now)) {
                return
            }

            val data = // Do dataType specific operations
                    when(dataType) {
                        SageTaskIdentifier.BatteryStatistics -> onReceiveBatteryChanged(intent)
                        SageTaskIdentifier.ScreenTime -> screenTimeData(intent.action)
                        SageTaskIdentifier.ChargingTime -> chargingTimeData(intent.action)
                        else -> onReceiveDefaultActionChanged(intent.action)
                    }

            val backgroundData = BackgroundDataEntity(
                    date = now,
                    dataType = dataType,
                    uploaded = false,
                    data = data)

            writeBackgroundDataToRoom(backgroundData)
        }

        /**
         * @return the background data type for the action
         */
        private fun dataType(intent: Intent): String? {
            val action = intent.action ?: run { return null }
            return when(action) {
                Intent.ACTION_BATTERY_CHANGED -> SageTaskIdentifier.BatteryStatistics
                Intent.ACTION_SCREEN_ON -> SageTaskIdentifier.ScreenTime
                Intent.ACTION_SCREEN_OFF -> SageTaskIdentifier.ScreenTime
                Intent.ACTION_USER_PRESENT -> SageTaskIdentifier.ScreenTime
                Intent.ACTION_POWER_CONNECTED -> SageTaskIdentifier.ChargingTime
                Intent.ACTION_POWER_DISCONNECTED -> SageTaskIdentifier.ChargingTime
                else -> null
            }
        }

        private fun onReceiveBatteryChanged(intent: Intent): String {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = level * 100 / scale.toFloat()
            Log.d(TAG, "Received $batteryPct% ${intent.action}")
            return "$batteryPct%"
        }


        fun onReceiveDefaultActionChanged(intentAction: String?): String? {
            Log.d(TAG, "Received ${intentAction}")
            return null
        }
    }
}
